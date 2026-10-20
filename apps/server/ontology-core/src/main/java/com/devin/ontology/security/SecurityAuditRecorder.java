package com.devin.ontology.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 只把认证/授权拒绝和服务端失败写入审计，不读取请求正文、查询串或异常文本。 */
@Slf4j
@Component
@Profile("multiuser")
public class SecurityAuditRecorder {
    static final String ACTOR_USER_ID_ATTRIBUTE = "ontology.auditActorUserId";

    private final SecurityAuditEventWriter writer;

    public SecurityAuditRecorder(final SecurityAuditEventWriter writer) {
        this.writer = writer;
    }

    public void recordResponseFailure(final HttpServletRequest request, final HttpServletResponse response) {
        int status = response.getStatus();
        String path = safe(request.getRequestURI(), 512);
        String eventType = eventType(status, path);
        if (eventType == null) return;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", safe(request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE), 80));
        metadata.put("method", safe(request.getMethod(), 16));
        metadata.put("path", path);
        metadata.put("status", status);
        metadata.put("peer", safe(request.getAttribute(RequestCorrelationFilter.CLIENT_ADDRESS_ATTRIBUTE), 64));
        Object actor = request.getAttribute(ACTOR_USER_ID_ATTRIBUTE);
        String actorUserId = actor instanceof String value && !value.isBlank() ? safe(value, 64) : null;

        try {
            writer.appendFailure(eventType, actorUserId, metadata);
        } catch (RuntimeException auditUnavailable) {
            // 审计存储故障只记录错误类型，不改变已生成的业务响应，也不泄露底层异常内容。
            log.warn("security audit persistence failed requestId={} eventType={} failureType={}",
                    metadata.get("requestId"), eventType, auditUnavailable.getClass().getSimpleName());
        }
    }

    private String eventType(final int status, final String path) {
        if (status == HttpServletResponse.SC_UNAUTHORIZED) return "SECURITY_AUTHENTICATION_FAILURE";
        if (status == HttpServletResponse.SC_FORBIDDEN) return "SECURITY_ACCESS_DENIED";
        if (status == 429) return "SECURITY_RATE_LIMITED";
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) return "SECURITY_REQUEST_FAILURE";
        if (status == HttpServletResponse.SC_BAD_REQUEST
                && (path.endsWith("/oidc/app/authorize") || path.endsWith("/oidc/app/token")
                        || path.endsWith("/oidc/app/revoke"))) {
            return "SECURITY_OAUTH_PROTOCOL_REJECTED";
        }
        return null;
    }

    private String safe(final Object value, final int maxLength) {
        if (value == null) return "-";
        String normalized = value.toString().replaceAll("[\\r\\n\\t\\p{Cntrl}]", "_");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
