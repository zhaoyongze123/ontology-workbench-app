package com.devin.ontology.controller;

import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.oidc.PendingWorkbenchBinding;
import com.devin.ontology.oidc.WorkbenchOidcConfigurer;
import com.devin.ontology.security.CurrentIdentity;
import com.devin.ontology.security.LoginUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 显式 Workbench 账号绑定入口；CSRF 校验由现有 multiuser 安全链承担。 */
@RestController
@Profile("multiuser")
@ConditionalOnProperty(prefix = "ontology.workbench", name = "enabled", havingValue = "true")
public class WorkbenchLoginController {
    private static final long RECENT_LOGIN_MILLIS = 10L * 60 * 1000;
    private static final long BINDING_TTL_MILLIS = 5L * 60 * 1000;
    private final CurrentIdentity currentIdentity;

    public WorkbenchLoginController(final CurrentIdentity currentIdentity) {
        this.currentIdentity = currentIdentity;
    }

    @PostMapping("/auth/workbench/bind")
    public ResponseEntity<Map<String, Object>> beginExplicitBinding(final HttpServletRequest request) {
        InternalUser user = currentIdentity.requireUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUserPrincipal principal)
                || !principal.userId().equals(user.id())) {
            throw new AuthenticationCredentialsNotFoundException("需要有效的内部登录会话");
        }

        long now = System.currentTimeMillis();
        if (principal.loginAtEpochMillis() > now
                || now - principal.loginAtEpochMillis() > RECENT_LOGIN_MILLIS) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "recent_authentication_required"));
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(PendingWorkbenchBinding.SESSION_ATTRIBUTE,
                new PendingWorkbenchBinding(user.id(), user.authVersion(), principal.loginAtEpochMillis(),
                        now + BINDING_TTL_MILLIS));
        // 仅返回站内固定授权入口；不接受任意 redirect 参数。
        return ResponseEntity.ok(Map.of("authorizationUrl", request.getContextPath() + WorkbenchOidcConfigurer.AUTHORIZATION_ENTRY,
                "expiresInSeconds", BINDING_TTL_MILLIS / 1000));
    }
}
