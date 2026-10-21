package com.devin.ontology.security;

import com.devin.ontology.common.exception.ApiErrorResponseWriter;
import com.devin.ontology.common.exception.ApiRequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** OAuth 协议端点使用自己的错误格式，不参与普通 API 的 Accept 内容协商。 */
@Component
@Profile("multiuser")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
final class OAuthProtocolErrorWriter {
    private static final String TOKEN_ENDPOINT = "/oidc/app/token";

    private final ObjectMapper mapper;
    private final ApiErrorResponseWriter requestIds;

    OAuthProtocolErrorWriter(final ObjectMapper mapper, final ApiErrorResponseWriter requestIds) {
        this.mapper = mapper;
        this.requestIds = requestIds;
    }

    boolean owns(final HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return TOKEN_ENDPOINT.equals(path);
    }

    void writeServerError(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(ApiRequestContext.REQUEST_ID_HEADER, requestIds.requestId(request));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        // 不回显内部异常；server_error 是客户端可识别的协议错误，Accept 不会改变其结构。
        mapper.writeValue(response.getWriter(), Map.of("error", "server_error"));
    }
}
