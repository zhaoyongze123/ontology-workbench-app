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

/** 登录前检查账号与来源地址桶；此过滤器由 SecurityFilterChain 手工注册，不做 Servlet 自动注册。 */
final class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginAttemptThrottleService throttleService;
    private final TrustedClientAddressResolver addressResolver;
    private final ApiErrorResponseWriter errorWriter;

    LoginRateLimitFilter(final LoginAttemptThrottleService throttleService,
            final TrustedClientAddressResolver addressResolver, final ApiErrorResponseWriter errorWriter) {
        this.throttleService = throttleService;
        this.addressResolver = addressResolver;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !"/auth/login".equals(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        try {
            if (throttleService.isBlocked(request.getParameter("username"), addressResolver.resolve(request))) {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.RETRY_AFTER, Long.toString(throttleService.retryAfterSeconds()));
                errorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS, ApiErrorCode.RATE_LIMITED,
                        LegacyErrorAdapter.Shape.ERROR_FIELD, "too_many_attempts", headers);
                return;
            }
        } catch (RuntimeException unavailable) {
            // 限流存储异常时关闭登录入口，避免数据库故障导致绕过保护。
            errorWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.DEPENDENCY_UNAVAILABLE, LegacyErrorAdapter.Shape.ERROR_FIELD,
                    "authentication_unavailable", null);
            return;
        }
        filterChain.doFilter(request, response);
    }

}
