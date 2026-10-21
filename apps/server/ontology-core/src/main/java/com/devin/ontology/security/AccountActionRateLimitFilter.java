package com.devin.ontology.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** 匿名账号动作按可信来源持久限流；从不读取请求正文或保存 token 原值。 */
final class AccountActionRateLimitFilter extends OncePerRequestFilter {
    private final OAuthProtocolRequestLimiter limiter;
    private final TrustedClientAddressResolver addressResolver;
    private final ObjectMapper mapper;

    AccountActionRateLimitFilter(final OAuthProtocolRequestLimiter limiter,
            final TrustedClientAddressResolver addressResolver, final ObjectMapper mapper) {
        this.limiter = limiter;
        this.addressResolver = addressResolver;
        this.mapper = mapper;
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
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                mapper.writeValue(response.getWriter(), Map.of("error", "too_many_requests"));
                return;
            }
        } catch (RuntimeException unavailable) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), Map.of("error", "authentication_unavailable"));
            return;
        }
        chain.doFilter(request, response);
    }
}
