package com.devin.ontology.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bearer API 链严格拒绝 Cookie 身份回退，响应与日志均不回显调用令牌。 */
public final class AgentBearerAuthenticationFilter extends OncePerRequestFilter {
    private final AgentAccessTokenService tokens;
    private final ObjectMapper mapper;

    public AgentBearerAuthenticationFilter(final AgentAccessTokenService tokens, final ObjectMapper mapper) {
        this.tokens = tokens;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            unauthorized(response, false);
            return;
        }
        if (!header.startsWith("Bearer ") || header.length() <= 7) {
            unauthorized(response, true);
            return;
        }
        Authentication authentication = tokens.authenticate(header.substring(7).trim());
        if (authentication == null) {
            unauthorized(response, true);
            return;
        }
        ((org.springframework.security.authentication.AbstractAuthenticationToken) authentication)
                .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }

    private void unauthorized(final HttpServletResponse response, final boolean invalidToken) throws IOException {
        SecurityContextHolder.clearContext();
        response.setHeader("WWW-Authenticate", invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("error", invalidToken ? "invalid_token" : "unauthorized"));
    }
}
