/*
 * Licensed to Apereo under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Apereo licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at the following location:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.cas.support.oauth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.jasig.cas.CentralAuthenticationService;
import org.jasig.cas.authentication.principal.Principal;
import org.jasig.cas.authentication.principal.Service;
import org.jasig.cas.authentication.principal.SimpleWebApplicationServiceImpl;
import org.jasig.cas.support.oauth.OAuthConstants;
import org.jasig.cas.support.oauth.OAuthToken;
import org.jasig.cas.support.oauth.OAuthUtils;
import org.jasig.cas.ticket.InvalidTicketException;
import org.jasig.cas.ticket.ServiceTicket;
import org.jasig.cas.ticket.TicketGrantingTicket;
import org.jasig.cas.ticket.registry.TicketRegistry;
import org.jasig.cas.util.CipherExecutor;
import org.jasig.cas.validation.Assertion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * This controller returns a list of active applications for the authenticated user
 * (identifier + attributes), found with the access token (CAS granting
 * ticket).
 *
 * @author Michael Haselton
 * @since 4.1.0
 */
public final class OAuth20SessionsController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth20SessionsController.class);

    private static final String ID = "id";

    private static final String ATTRIBUTES = "attributes";

    private final TicketRegistry ticketRegistry;

    private final CentralAuthenticationService centralAuthenticationService;

    private final CipherExecutor cipherExecutor;

    /**
     * Instantiates a new o auth20 profile controller.
     *
     * @param ticketRegistry the ticket registry
     */
    public OAuth20SessionsController(final TicketRegistry ticketRegistry,
                                     final CentralAuthenticationService centralAuthenticationService,
                                     final CipherExecutor cipherExecutor) {
        this.ticketRegistry = ticketRegistry;
        this.centralAuthenticationService = centralAuthenticationService;
        this.cipherExecutor = cipherExecutor;
    }

    @Override
    protected ModelAndView handleRequestInternal(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
        String jwtAccessToken = request.getParameter(OAuthConstants.ACCESS_TOKEN);
        if (StringUtils.isBlank(jwtAccessToken)) {
            final String authHeader = request.getHeader("Authorization");
            if (StringUtils.isNotBlank(authHeader) && authHeader.startsWith(OAuthConstants.BEARER_TOKEN + " ")) {
                jwtAccessToken = authHeader.substring(OAuthConstants.BEARER_TOKEN.length() + 1);
            }
        }
        LOGGER.debug("{} : {}", OAuthConstants.ACCESS_TOKEN, jwtAccessToken);

        // jwtAccessToken is required
        if (StringUtils.isBlank(jwtAccessToken)) {
            LOGGER.error("Missing {}", OAuthConstants.ACCESS_TOKEN);
            return OAuthUtils.writeTextError(response, OAuthConstants.MISSING_ACCESS_TOKEN, HttpStatus.SC_BAD_REQUEST);
        }

        OAuthToken accessToken = OAuthToken.read(cipherExecutor.decode(jwtAccessToken));
        LOGGER.debug("Token : {}", accessToken);

        final ServiceTicket serviceTicket;
        if (accessToken.serviceTicketId == null) {
            TicketGrantingTicket ticketGrantingTicket = (TicketGrantingTicket) this.ticketRegistry.getTicket(accessToken.ticketGrantingTicketId);
            if (ticketGrantingTicket == null) {
                LOGGER.error("Unknown Ticket Granting Ticket : {}", accessToken.ticketGrantingTicketId);
                return OAuthUtils.writeTextError(response, OAuthConstants.MISSING_ACCESS_TOKEN, HttpStatus.SC_NOT_FOUND);
            }

            final Service service = new SimpleWebApplicationServiceImpl(accessToken.serviceId);
            serviceTicket = centralAuthenticationService.grantServiceTicket(ticketGrantingTicket.getId(), service);
        } else {
            // get service ticket, needed to lookup service for validation
            serviceTicket = (ServiceTicket) this.ticketRegistry.getTicket(accessToken.serviceTicketId);
        }

        if (serviceTicket == null) {
            LOGGER.error("Unknown Service Ticket : {}", accessToken.serviceTicketId);
            return OAuthUtils.writeTextError(response, OAuthConstants.MISSING_ACCESS_TOKEN, HttpStatus.SC_NOT_FOUND);
        }

        // validate the service ticket, also applies attribute release policy
        final Assertion assertion;
        try {
            assertion = this.centralAuthenticationService.validateServiceTicket(serviceTicket.getId(), serviceTicket.getService());
        } catch (InvalidTicketException e) {
            LOGGER.error("Expired {} (Service Ticket) : {}", OAuthConstants.ACCESS_TOKEN, serviceTicket.getId());
            return OAuthUtils.writeTextError(response, OAuthConstants.MISSING_ACCESS_TOKEN, HttpStatus.SC_BAD_REQUEST);
        }

        // generate profile : identifier + attributes
        final Principal principal = assertion.getPrimaryAuthentication().getPrincipal();

        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, Object> map = new HashMap<>();
        final Map<String, Object> attributeMap = new HashMap<>();
        map.put(ID, principal.getId());
        for (final Map.Entry<String, Object> attribute : principal.getAttributes().entrySet()) {
            attributeMap.put(attribute.getKey(), attribute.getValue());
        }
        map.put(ATTRIBUTES, attributeMap);

        final String result = mapper.writeValueAsString(map);
        LOGGER.debug("result : {}", result);

        response.setContentType("application/json");
        return OAuthUtils.writeText(response, result, HttpStatus.SC_OK);
    }
}