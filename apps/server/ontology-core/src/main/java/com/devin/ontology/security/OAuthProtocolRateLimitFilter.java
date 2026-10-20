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

/** OAuth 协议入口使用独立的来源/客户端桶；限流失败时不落入密码登录失败桶。 */
public final class OAuthProtocolRateLimitFilter extends OncePerRequestFilter {
    private final OAuthProtocolRequestLimiter limiter;
    private final TrustedClientAddressResolver addressResolver;
    private final ObjectMapper mapper;
    private final long retryAfterSeconds;

    public OAuthProtocolRateLimitFilter(final OAuthProtocolRequestLimiter limiter,
            final TrustedClientAddressResolver addressResolver, final ObjectMapper mapper,
            final long retryAfterSeconds) {
        this.limiter = limiter;
        this.addressResolver = addressResolver;
        this.mapper = mapper;
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !("/oidc/app/authorize".equals(path) || "/oidc/app/token".equals(path)
                || "/oidc/app/revoke".equals(path));
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String type = switch (path) {
            case "/oidc/app/authorize" -> "OAUTH_AUTHORIZE";
            case "/oidc/app/token" -> "OAUTH_TOKEN";
            case "/oidc/app/revoke" -> "OAUTH_REVOKE";
            default -> throw new IllegalStateException("OAuth 限流路径未分类");
        };
        try {
            if (!limiter.allowRequest(type, request.getParameter("client_id"), addressResolver.resolve(request))) {
                writeError(response, 429, "too_many_requests");
                return;
            }
        } catch (RuntimeException unavailable) {
            // 限流存储不可用时关闭 OAuth 凭证入口，避免数据库故障绕过保护。
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "authentication_unavailable");
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(final HttpServletResponse response, final int status, final String error)
            throws IOException {
        response.setStatus(status);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("error", error));
    }
}
