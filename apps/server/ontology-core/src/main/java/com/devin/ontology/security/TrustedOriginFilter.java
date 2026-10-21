package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.ApiErrorResponseWriter;
import com.devin.ontology.common.exception.LegacyErrorAdapter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/** Cookie 认证的写请求只接受配置的可信来源或请求自身来源。 */
final class TrustedOriginFilter extends OncePerRequestFilter {
    private final Set<String> trustedOrigins;
    private final ApiErrorResponseWriter errorWriter;

    TrustedOriginFilter(final Set<String> trustedOrigins, final ApiErrorResponseWriter errorWriter) {
        this.trustedOrigins = trustedOrigins;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        if (!isSafe(request.getMethod())) {
            String origin = request.getHeader("Origin");
            if (origin != null && !trustedOrigins.contains(origin) && !origin.equals(requestOrigin(request))) {
                errorWriter.write(request, response, org.springframework.http.HttpStatus.FORBIDDEN,
                        ApiErrorCode.SECURITY_ORIGIN_REJECTED, LegacyErrorAdapter.Shape.ERROR_FIELD,
                        "forbidden", null);
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
