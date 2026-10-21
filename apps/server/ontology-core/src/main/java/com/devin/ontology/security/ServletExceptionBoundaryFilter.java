package com.devin.ontology.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 最外层 HTTP 故障边界：不让未处理异常正文进入容器错误页或异常堆栈日志。 */
@Slf4j
@Component
@Profile("multiuser")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class ServletExceptionBoundaryFilter extends OncePerRequestFilter {
    private final SecurityAuditRecorder auditRecorder;

    public ServletExceptionBoundaryFilter(final SecurityAuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException | ServletException failure) {
            SecurityContextHolder.clearContext();
            String requestId = requestId(request, response);
            // 不附 Throwable 参数，避免日志后端递归输出 message、cause、SQL 或响应正文。
            log.error("unhandled servlet request failure requestId={} exceptionType={}",
                    requestId, failure.getClass().getSimpleName());

            if (!response.isCommitted()) {
                // 任何异常都不能保留下游已写的敏感正文或不匹配的 Content-Length。
                response.reset();
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
                response.setHeader("X-Request-ID", requestId);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("X-Content-Type-Options", "nosniff");
                try {
                    response.getWriter().write("{\"error\":\"request_unavailable\"}");
                } catch (IOException | RuntimeException writeFailure) {
                    // 客户端断开时响应无法补写；不以第二个异常覆盖首个固定故障记录。
                }
            }
            if (response.getStatus() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                try {
                    auditRecorder.recordResponseFailure(request, response);
                } catch (RuntimeException auditFailure) {
                    // 审计故障不能覆盖固定 503 响应，也不记录底层异常文本。
                    log.warn("servlet failure audit unavailable requestId={} exceptionType={}",
                            requestId, auditFailure.getClass().getSimpleName());
                }
            }
        }
    }

    private String requestId(final HttpServletRequest request, final HttpServletResponse response) {
        Object existing = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String id && !id.isBlank()) return safeRequestId(id);
        String generated = UUID.randomUUID().toString();
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, generated);
        response.setHeader("X-Request-ID", generated);
        return generated;
    }

    private String safeRequestId(final String value) {
        String safe = value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", "_");
        return safe.length() <= 80 ? safe : safe.substring(0, 80);
    }

}
