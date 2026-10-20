package com.devin.ontology.oidc;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/** 清除短时绑定意图，并以固定错误码结束失败的上游认证。 */
final class WorkbenchOidcFailureHandler implements AuthenticationFailureHandler {
    private final ObjectMapper mapper;

    WorkbenchOidcFailureHandler(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void onAuthenticationFailure(final HttpServletRequest request,
            final HttpServletResponse response, final org.springframework.security.core.AuthenticationException exception)
            throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        SecurityContextHolder.clearContext();
        if (session != null) {
            try {
                // 回调失败时一并删除旧会话，避免下一请求从持久 Session 恢复旧内部身份。
                session.invalidate();
            } catch (IllegalStateException alreadyInvalidated) {
                // 已失效的会话无需再清理。
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of("error", "workbench_login_failed"));
    }
}
