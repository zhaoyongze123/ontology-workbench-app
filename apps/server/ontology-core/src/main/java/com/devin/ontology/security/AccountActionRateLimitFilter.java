package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.ApiErrorResponseWriter;
import com.devin.ontology.common.exception.LegacyErrorAdapter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** 匿名账号动作按可信来源持久限流；从不读取请求正文或保存 token 原值。 */
final class AccountActionRateLimitFilter extends OncePerRequestFilter {
    private final OAuthProtocolRequestLimiter limiter;
    private final TrustedClientAddressResolver addressResolver;
    private final ApiErrorResponseWriter errorWriter;

    AccountActionRateLimitFilter(final OAuthProtocolRequestLimiter limiter,
            final TrustedClientAddressResolver addressResolver, final ApiErrorResponseWriter errorWriter) {
        this.limiter = limiter;
        this.addressResolver = addressResolver;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("/auth/account-actions/inspect".equals(path) || "/auth/activate".equals(path)
                || "/auth/password-reset".equals(path));
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String bucket = "/auth/password-reset".equals(path) ? "ACCOUNT_RESET" : "ACCOUNT_ACTIVATE";
        try {
            if (!limiter.allowRequest(bucket, null, addressResolver.resolve(request))) {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.RETRY_AFTER, "60");
                errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.RATE_LIMITED,
                        LegacyErrorAdapter.Shape.ERROR_FIELD, "too_many_requests", headers);
                return;
            }
        } catch (RuntimeException unavailable) {
            errorWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.DEPENDENCY_UNAVAILABLE, LegacyErrorAdapter.Shape.ERROR_FIELD,
                    "authentication_unavailable", null);
            return;
        }
        chain.doFilter(request, response);
    }
}
