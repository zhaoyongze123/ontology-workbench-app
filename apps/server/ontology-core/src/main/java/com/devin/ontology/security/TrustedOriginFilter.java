package com.devin.ontology.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cookie 认证的写请求只接受配置的可信来源或请求自身来源。 */
final class TrustedOriginFilter extends OncePerRequestFilter {
    private final Set<String> trustedOrigins;

    TrustedOriginFilter(final Set<String> trustedOrigins) {
        this.trustedOrigins = trustedOrigins;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        if (!isSafe(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin != null && !trustedOrigins.contains(origin) && !origin.equals(requestOrigin(request))) {
                response.sendError(HttpStatus.FORBIDDEN.value());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSafe(final String method) {
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method) || HttpMethod.TRACE.matches(method);
    }

    private String requestOrigin(final HttpServletRequest request) {
        String scheme = request.isSecure() ? "https" : "http";
        int port = request.getServerPort();
        boolean defaultPort = ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }
}
