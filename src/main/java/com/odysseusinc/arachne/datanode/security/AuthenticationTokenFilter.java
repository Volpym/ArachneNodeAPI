/*
 *
 * Copyright 2018 Odysseus Data Services, inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Company: Odysseus Data Services, Inc.
 * Product Owner/Architecture: Gregory Klebanov
 * Authors: Pavel Grafkin, Alexandr Ryabokon, Vitaly Koulakov, Anton Gackovka, Maria Pozhidaeva, Mikhail Mironov
 * Created: December 16, 2016
 *
 */

package com.odysseusinc.arachne.datanode.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseusinc.arachne.commons.api.v1.dto.util.JsonResult;
import com.odysseusinc.arachne.datanode.exception.AuthException;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

public class AuthenticationTokenFilter extends UsernamePasswordAuthenticationFilter {

    Logger log = LoggerFactory.getLogger(AuthenticationTokenFilter.class);

    @Value("${datanode.jwt.header}")
    private String tokenHeader;

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private UserDetailsService userDetailsService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String authToken = httpRequest.getHeader(this.tokenHeader);
        try {
            if (authToken != null) {
                String username = tokenUtils.getUsernameFromToken(authToken);
                if (tokenUtils.isExpired(authToken)) {
                    if (((HttpServletRequest) request).getRequestURI().startsWith("/api")) {
                        if (username != null) {
                            throw new AuthException("token expired");
                        }
                    }
                }

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                    if (this.tokenUtils.validateToken(authToken, userDetails)) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpRequest));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }

            chain.doFilter(request, response);
        } catch (AuthenticationException | AuthException ex) {
            ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            JsonResult<Boolean> result = new JsonResult<>(JsonResult.ErrorCode.UNAUTHORIZED);
            result.setResult(Boolean.FALSE);

            response.getOutputStream().write(objectMapper.writeValueAsString(result).getBytes());
            response.setContentType("application/json");
        }
    }

}