package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.ApiErrorResponseWriter;
import com.devin.ontology.common.exception.LegacyErrorAdapter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
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
    private final ApiErrorResponseWriter errorWriter;
    private final OAuthProtocolErrorWriter oauthErrorWriter;

    public ServletExceptionBoundaryFilter(final ApiErrorResponseWriter errorWriter,
            final OAuthProtocolErrorWriter oauthErrorWriter) {
        this.errorWriter = errorWriter;
        this.oauthErrorWriter = oauthErrorWriter;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException | ServletException failure) {
            SecurityContextHolder.clearContext();
            String requestId = errorWriter.requestId(request);
            // 不附 Throwable 参数，避免日志后端递归输出 message、cause、SQL 或响应正文。
            log.error("unhandled servlet request failure requestId={} exceptionType={}",
                    requestId, failure.getClass().getSimpleName());

            if (!response.isCommitted()) {
                // 任何异常都不能保留下游已写的敏感正文或不匹配的 Content-Length。
                try {
                    response.reset();
                    if (oauthErrorWriter.owns(request)) {
                        oauthErrorWriter.writeServerError(request, response);
                    } else {
                        errorWriter.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR,
                                ApiErrorCode.INTERNAL_ERROR, LegacyErrorAdapter.Shape.ERROR_FIELD,
                                "request_unavailable", null);
                    }
                } catch (IOException | RuntimeException writeFailure) {
                    // 客户端断开时响应无法补写；不以第二个异常覆盖首个固定故障记录。
                }
            }
        }
    }
}
