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

/** 登录前检查账号与来源地址桶；此过滤器由 SecurityFilterChain 手工注册，不做 Servlet 自动注册。 */
final class LoginRateLimitFilter extends OncePerRequestFilter {
    private final LoginAttemptThrottleService throttleService;
    private final ObjectMapper objectMapper;

    LoginRateLimitFilter(final LoginAttemptThrottleService throttleService, final ObjectMapper objectMapper) {
        this.throttleService = throttleService;
        this.objectMapper = objectMapper;
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
            // 只信任 Servlet socket peer；将来使用 Nginx 时由受信入口先解析代理头，再传入可信 remoteAddr。
            if (throttleService.isBlocked(request.getParameter("username"), request.getRemoteAddr())) {
                writeError(response, 429, "too_many_attempts");
                return;
            }
        } catch (RuntimeException unavailable) {
            // 限流存储异常时关闭登录入口，避免数据库故障导致绕过保护。
            writeError(response, 503, "authentication_unavailable");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(final HttpServletResponse response, final int status, final String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", code));
    }
}
