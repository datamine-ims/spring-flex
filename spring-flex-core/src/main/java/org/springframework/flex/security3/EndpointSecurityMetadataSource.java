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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;

import flex.messaging.FlexContext;
import flex.messaging.endpoints.Endpoint;

/**
 *
 * Holds the security attributes (required roles/authorities) for BlazeDS {@link Endpoint Endpoints}.
 *
 * <p>
 * This implementation is capable of securing Endpoints both by their channel id, and by their URL pattern.
 *
 * <p>
 * Security attributes are represented as plain {@code String} authority names (for example {@code ROLE_USER}).
 * The legacy Spring Security {@code ConfigAttribute}/{@code SecurityMetadataSource} types were intentionally
 * dropped so that this class does not depend on the {@code spring-security-access} module (required from
 * Spring Security 7).
 *
 * @author Jeremy Grelle
 */

public class EndpointSecurityMetadataSource {

	private Map<RequestMatcher, Collection<String>> requestMap = new LinkedHashMap<RequestMatcher, Collection<String>>();

    private Map<String, Collection<String>> endpointMap = new LinkedHashMap<String, Collection<String>>();

    public EndpointSecurityMetadataSource(LinkedHashMap<RequestMatcher, Collection<String>> requestMap) {
    	Assert.notNull(requestMap, "requestMap cannot be null");
        this.requestMap = requestMap;
    }

    /**
     * Builds the internal request map from the supplied map, and stores the endpoint map for matching by channel id.
     *
     * @param endpointMap map of &lt;String, Collection&lt;String&gt;&gt;
     */
    public EndpointSecurityMetadataSource(LinkedHashMap<RequestMatcher, Collection<String>> requestMap,
        HashMap<String, Collection<String>> endpointMap) {
    	this(requestMap);
        Assert.notNull(endpointMap, "endpointMap cannot be null");
        this.endpointMap = endpointMap;
    }

    /**
     * Returns the security attributes that apply to the supplied {@link Endpoint}, matching first by channel id
     * and then by the URL pattern of the current request.
     */
    public Collection<String> getAttributes(Object object) throws IllegalArgumentException {
        if (object == null || !this.supports(object.getClass())) {
            throw new IllegalArgumentException("Object must be an Endpoint");
        }

        Endpoint endpoint = (Endpoint) object;
        Collection<String> attributes = null;

        if (this.endpointMap.containsKey(endpoint.getId())) {
            attributes = this.endpointMap.get(endpoint.getId());
        } else {
            HttpServletRequest request = FlexContext.getHttpRequest();
            if (request != null) {
                attributes = findMatchingRequestAttributes(request);
            }
        }
        return attributes;
    }

    private Collection<String> findMatchingRequestAttributes(HttpServletRequest request) {
        for (Map.Entry<RequestMatcher, Collection<String>> entry : this.requestMap.entrySet()) {
            if (entry.getKey().matches(request)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns all configured security attributes across the endpoint and request maps.
     */
    public Collection<String> getAllConfigAttributes() {
        List<String> allAttributes = new ArrayList<String>();
        for (Map.Entry<String, Collection<String>> entry : this.endpointMap.entrySet()) {
            allAttributes.addAll(entry.getValue());
        }
        for (Map.Entry<RequestMatcher, Collection<String>> entry : this.requestMap.entrySet()) {
            allAttributes.addAll(entry.getValue());
        }
        return Collections.unmodifiableCollection(allAttributes);
    }

    public boolean supports(Class<?> clazz) {
        return Endpoint.class.isAssignableFrom(clazz);
    }
}
