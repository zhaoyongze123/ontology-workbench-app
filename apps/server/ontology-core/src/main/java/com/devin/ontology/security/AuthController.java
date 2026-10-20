package com.devin.ontology.security;

import com.devin.ontology.identity.InternalUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向前端的认证会话接口；所有返回对象均不包含凭据。 */
@RestController
@Profile("multiuser")
public class AuthController {
    private final CurrentIdentity currentIdentity;
    private final SessionRevocationService sessionRevocationService;
    private final boolean workbenchEnabled;

    public AuthController(final CurrentIdentity currentIdentity,
            final SessionRevocationService sessionRevocationService,
            @Value("${ontology.workbench.enabled:false}") final boolean workbenchEnabled) {
        this.currentIdentity = currentIdentity;
        this.sessionRevocationService = sessionRevocationService;
        this.workbenchEnabled = workbenchEnabled;
    }

    @GetMapping("/auth/options")
    public Map<String, Object> options() {
        return Map.of("passwordEnabled", true, "workbenchEnabled", workbenchEnabled);
    }

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(final HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) token = (CsrfToken) request.getAttribute("_csrf");
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/auth/me")
    public InternalUser me() {
        return currentIdentity.requireUser();
    }

    @PostMapping("/auth/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(final HttpServletRequest request) {
        InternalUser user = currentIdentity.requireUser();
        sessionRevocationService.revokeAllSessions(user);
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("status", "logged_out_all"));
    }

}
