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
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

            AuthorizationDecision decision = this.authorizationManager.check(() -> authentication, endpoint);

            if (decision != null && !decision.isGranted()) {
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

        @Override
        public AuthorizationDecision check(Supplier<Authentication> authentication, AbstractEndpoint endpoint) {
            Collection<String> attributes = this.metadataSource.getAttributes(endpoint);

            // No attributes configured means the endpoint is not secured - grant access.
            if (attributes == null || attributes.isEmpty()) {
                return new AuthorizationDecision(true);
            }

            Authentication auth = authentication.get();
            if (isUnauthenticated(auth)) {
                return new AuthorizationDecision(false);
            }

            for (String attribute : attributes) {
                if (attribute == null) {
                    continue;
                }
                // IS_AUTHENTICATED_* attributes are satisfied by any authenticated principal
                // (mirrors AuthenticatedVoter behaviour).
                if (attribute.startsWith("IS_AUTHENTICATED_")) {
                    return new AuthorizationDecision(true);
                }
                if (auth.getAuthorities().contains(new SimpleGrantedAuthority(attribute))) {
                    return new AuthorizationDecision(true);
                }
            }

            return new AuthorizationDecision(false);
        }
    }
}
