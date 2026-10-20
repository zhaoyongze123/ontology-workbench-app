package com.devin.ontology.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在授权判断前把已验证的内部用户 ID 放入请求属性，供安全拒绝审计关联操作者。 */
public final class AuditActorCaptureFilter extends OncePerRequestFilter {
    public AuditActorCaptureFilter() {
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginUserPrincipal principal) {
            request.setAttribute(SecurityAuditRecorder.ACTOR_USER_ID_ATTRIBUTE, principal.userId());
        }
        chain.doFilter(request, response);
    }
}
