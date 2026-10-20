package com.devin.ontology.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为每个请求生成服务端关联号，拒绝沿用客户端自报的关联号。 */
@Slf4j
public final class RequestCorrelationFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_ATTRIBUTE = "ontology.requestId";
    static final String CLIENT_ADDRESS_ATTRIBUTE = "ontology.clientAddress";

    private final TrustedClientAddressResolver addressResolver;

    public RequestCorrelationFilter(final TrustedClientAddressResolver addressResolver) {
        this.addressResolver = addressResolver;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String clientAddress = addressResolver.resolve(request);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(CLIENT_ADDRESS_ATTRIBUTE, clientAddress);
        response.setHeader("X-Request-ID", requestId);
        MDC.put("requestId", requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 错误日志只记录元数据；请求/响应正文和异常文本由其他安全边界处理。
            if (response.getStatus() >= 400) {
                log.warn("http request failed requestId={} peer={} method={} path={} status={}",
                        requestId, clientAddress, request.getMethod(), request.getRequestURI(), response.getStatus());
            }
            MDC.remove("requestId");
        }
    }
}
