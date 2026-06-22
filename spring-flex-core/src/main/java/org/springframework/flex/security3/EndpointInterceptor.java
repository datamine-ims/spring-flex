/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.flex.security3;

import java.util.Collection;
import java.util.function.Supplier;

import org.springframework.flex.core.MessageInterceptor;
import org.springframework.flex.core.MessageProcessingContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;

import flex.messaging.endpoints.AbstractEndpoint;
import flex.messaging.messages.CommandMessage;
import flex.messaging.messages.Message;

/**
 * Security interceptor that secures messages being passed to BlazeDS endpoints based on the security attributes
 * configured for the endpoint being invoked.
 *
 * <p>This implementation uses the {@link AuthorizationManager} API introduced as the primary authorization model
 * in Spring Security. It deliberately avoids the legacy {@code AbstractSecurityInterceptor} /
 * {@code AccessDecisionManager} voter infrastructure, which was moved to the optional {@code spring-security-access}
 * module in Spring Security 7. The default {@link AuthorizationManager} preserves the previous behaviour of the
 * {@code RoleVoter} + {@code AuthenticatedVoter} combination: access is granted when the authenticated principal
 * holds any of the required authorities, or when the endpoint declares no attributes.
 *
 * @author Jeremy Grelle
 */
public class EndpointInterceptor implements MessageInterceptor {

    private EndpointSecurityMetadataSource securityMetadataSource;

    private AuthorizationManager<AbstractEndpoint> authorizationManager;

    /**
     * Ensures the default {@link AuthorizationManager} is in place if a custom one was not supplied.
     */
    public void afterPropertiesSet() {
        Assert.notNull(this.securityMetadataSource, "securityMetadataSource must be set");
        if (this.authorizationManager == null) {
            this.authorizationManager = new EndpointAuthorizationManager(this.securityMetadataSource);
        }
    }

    public EndpointSecurityMetadataSource getObjectDefinitionSource() {
        return this.securityMetadataSource;
    }

    /**
     * Sets the {@link EndpointSecurityMetadataSource} for the endpoint being secured
     *
     * @param newSource the endpoint definition source
     */
    public void setObjectDefinitionSource(EndpointSecurityMetadataSource newSource) {
        this.securityMetadataSource = newSource;
        if (this.authorizationManager == null) {
            this.authorizationManager = new EndpointAuthorizationManager(newSource);
        }
    }

    /**
     * Replaces the default attribute-based {@link AuthorizationManager} with a custom implementation.
     */
    public void setAuthorizationManager(AuthorizationManager<AbstractEndpoint> authorizationManager) {
        this.authorizationManager = authorizationManager;
    }

    /**
     * {@inheritDoc}
     */
    public Message preProcess(MessageProcessingContext context, Message inputMessage) {
        if (!isPassThroughCommand(inputMessage)) {
            AbstractEndpoint endpoint = (AbstractEndpoint) context.getMessageTarget();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            AuthorizationResult result = this.authorizationManager.authorize(() -> authentication, endpoint);

            // AuthorizationManager#authorize may return null to abstain. Deny by default so a custom
            // AuthorizationManager that abstains cannot unintentionally grant access.
            if (result == null || !result.isGranted()) {
                // Mirror AbstractSecurityInterceptor: an unauthenticated principal yields an
                // AuthenticationException (CLIENT_AUTHENTICATION_CODE), whereas an authenticated
                // but insufficiently privileged principal yields an AccessDeniedException
                // (CLIENT_AUTHORIZATION_CODE).
                if (isUnauthenticated(authentication)) {
                    throw new InsufficientAuthenticationException(
                        "An Authentication object was not found in the SecurityContext");
                }
                throw new AccessDeniedException("Access is denied");
            }
        }
        return inputMessage;
    }

    /**
     * {@inheritDoc}
     */
    public Message postProcess(MessageProcessingContext context, Message inputMessage, Message outputMessage) {
        return outputMessage;
    }

    private static boolean isUnauthenticated(Authentication authentication) {
        return authentication == null
            || authentication instanceof AnonymousAuthenticationToken
            || !authentication.isAuthenticated();
    }

    private boolean isPassThroughCommand(Message message) {
        if (message instanceof CommandMessage) {
            CommandMessage command = (CommandMessage) message;
            return command.getOperation() == CommandMessage.CLIENT_PING_OPERATION || command.getOperation() == CommandMessage.LOGIN_OPERATION;
        }
        return false;
    }

    /**
     * Default {@link AuthorizationManager} that grants access when the authenticated principal holds any of the
     * authorities declared for the endpoint, or when the endpoint declares no attributes (open). This mirrors the
     * legacy {@code AffirmativeBased} manager configured with a {@code RoleVoter} and an {@code AuthenticatedVoter}.
     */
    private static final class EndpointAuthorizationManager implements AuthorizationManager<AbstractEndpoint> {

        private final EndpointSecurityMetadataSource metadataSource;

        EndpointAuthorizationManager(EndpointSecurityMetadataSource metadataSource) {
            this.metadataSource = metadataSource;
        }

        private static final String IS_AUTHENTICATED_FULLY = "IS_AUTHENTICATED_FULLY";

        private static final String IS_AUTHENTICATED_REMEMBERED = "IS_AUTHENTICATED_REMEMBERED";

        private static final String IS_AUTHENTICATED_ANONYMOUSLY = "IS_AUTHENTICATED_ANONYMOUSLY";

        @Override
        public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, AbstractEndpoint endpoint) {
            Collection<String> attributes = this.metadataSource.getAttributes(endpoint);

            // No attributes configured means the endpoint is not secured - grant access.
            if (attributes == null || attributes.isEmpty()) {
                return new AuthorizationDecision(true);
            }

            Authentication auth = authentication.get();
            for (String attribute : attributes) {
                if (attribute == null) {
                    continue;
                }
                if (grants(attribute, auth)) {
                    return new AuthorizationDecision(true);
                }
            }

            return new AuthorizationDecision(false);
        }

        /**
         * Mirrors the legacy {@code AuthenticatedVoter} + {@code RoleVoter} semantics:
         * <ul>
         *     <li>{@code IS_AUTHENTICATED_ANONYMOUSLY} - granted for any authentication, including anonymous.</li>
         *     <li>{@code IS_AUTHENTICATED_REMEMBERED} - granted for remember-me or fully authenticated principals.</li>
         *     <li>{@code IS_AUTHENTICATED_FULLY} - granted only for fully authenticated (non-anonymous, non-remembered) principals.</li>
         *     <li>Any other attribute is treated as the name of a {@link GrantedAuthority} that the authentication must hold.</li>
         * </ul>
         */
        private static boolean grants(String attribute, Authentication auth) {
            if (auth == null) {
                return false;
            }
            switch (attribute) {
                case IS_AUTHENTICATED_ANONYMOUSLY:
                    return true;
                case IS_AUTHENTICATED_REMEMBERED:
                    return auth instanceof RememberMeAuthenticationToken || isFullyAuthenticated(auth);
                case IS_AUTHENTICATED_FULLY:
                    return isFullyAuthenticated(auth);
                default:
                    return auth.isAuthenticated() && hasAuthority(auth, attribute);
            }
        }

        private static boolean hasAuthority(Authentication auth, String attribute) {
            if (auth.getAuthorities() == null) {
                return false;
            }
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if (authority != null && attribute.equals(authority.getAuthority())) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isFullyAuthenticated(Authentication auth) {
            return auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && !(auth instanceof RememberMeAuthenticationToken);
        }
    }
}
