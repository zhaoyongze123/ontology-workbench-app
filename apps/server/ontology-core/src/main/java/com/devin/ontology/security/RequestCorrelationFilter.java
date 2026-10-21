package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** 为每个请求生成服务端关联号，拒绝沿用客户端自报的关联号。 */
@Slf4j
public final class RequestCorrelationFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_ATTRIBUTE = ApiRequestContext.REQUEST_ID_ATTRIBUTE;
    static final String CLIENT_ADDRESS_ATTRIBUTE = "ontology.clientAddress";
    private static final Set<String> AUDITED_OAUTH_PATHS = Set.of(
            "/oidc/app/authorize", "/oidc/app/token", "/oidc/app/revoke");

    private final TrustedClientAddressResolver addressResolver;
    private final SecurityAuditRecorder auditRecorder;

    public RequestCorrelationFilter(final TrustedClientAddressResolver addressResolver,
            final SecurityAuditRecorder auditRecorder) {
        this.addressResolver = addressResolver;
        this.auditRecorder = auditRecorder;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String clientAddress = addressResolver.resolve(request);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(CLIENT_ADDRESS_ATTRIBUTE, clientAddress);
        response.setHeader(ApiRequestContext.REQUEST_ID_HEADER, requestId);
        MDC.put("requestId", requestId);
        // OAuth 协议过滤器会先缓存并释放响应体；外层再缓存一次，确保失败审计提交后客户端才收到响应。
        ContentCachingResponseWrapper bufferedResponse = shouldBufferOAuthResponse(request)
                ? new ContentCachingResponseWrapper(response) : null;
        HttpServletResponse observedResponse = bufferedResponse == null ? response : bufferedResponse;
        try {
            filterChain.doFilter(request, observedResponse);
        } finally {
            // 错误日志只记录元数据；请求/响应正文和异常文本由其他安全边界处理。
            if (observedResponse.getStatus() >= 400) {
                log.warn("http request failed requestId={} peer={} method={} path={} status={}",
                        requestId, clientAddress, safeLogValue(request.getMethod(), 16),
                        safeLogValue(request.getRequestURI(), 512), observedResponse.getStatus());
                try {
                    auditRecorder.recordResponseFailure(request, observedResponse);
                } catch (RuntimeException auditFailure) {
                    // 审计写入失败不能覆盖已经确定的业务响应。
                    log.warn("http failure audit unavailable requestId={} exceptionType={}", requestId,
                            auditFailure.getClass().getSimpleName());
                }
            }
            try {
                if (bufferedResponse != null) bufferedResponse.copyBodyToResponse();
            } finally {
                MDC.remove("requestId");
            }
        }
    }

    private boolean shouldBufferOAuthResponse(final HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return AUDITED_OAUTH_PATHS.contains(path);
    }

    private String safeLogValue(final String value, final int maxLength) {
        if (value == null) return "-";
        StringBuilder sanitized = new StringBuilder(Math.min(value.length(), maxLength));
        for (int index = 0; index < value.length() && sanitized.length() < maxLength; index++) {
            char character = value.charAt(index);
            sanitized.append(Character.isISOControl(character) ? '_' : character);
        }
        return sanitized.toString();
    }
}
