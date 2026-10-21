package com.devin.ontology.oidc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devin.ontology.controller.WorkbenchLoginController;
import com.devin.ontology.identity.InternalUser;
import com.devin.ontology.security.CurrentIdentity;
import com.devin.ontology.security.LoginUserPrincipal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class WorkbenchLoginControllerTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bindingCreatesOnlyShortLivedServerSessionIntentAndFixedEntryUrl() {
        long now = System.currentTimeMillis();
        InternalUser user = new InternalUser("internal-id", "alice", "Alice", false, true, 7);
        CurrentIdentity currentIdentity = mock(CurrentIdentity.class);
        when(currentIdentity.requireUser()).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUserPrincipal(user.id(), user.username(), user.displayName(), false, 7, now), null,
                java.util.List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/workbench/bind");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE,
                "/api/v1/oidc/app/authorize?client_id=stale&state=opaque");
        request.setSession(session);

        ResponseEntity<Map<String, Object>> response = new WorkbenchLoginController(currentIdentity)
                .beginExplicitBinding(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(WorkbenchOidcConfigurer.AUTHORIZATION_ENTRY, response.getBody().get("authorizationUrl"));
        PendingWorkbenchBinding pending = assertInstanceOf(PendingWorkbenchBinding.class,
                session.getAttribute(PendingWorkbenchBinding.SESSION_ATTRIBUTE));
        assertEquals(user.id(), pending.userId());
        assertEquals(user.authVersion(), pending.authVersion());
        assertTrue(pending.expiresAtEpochMillis() - System.currentTimeMillis() <= 5L * 60 * 1000);
        assertEquals(null, session.getAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE));
    }

    @Test
    void bindingRequiresRecentInternalLogin() {
        long oldLogin = System.currentTimeMillis() - 11L * 60 * 1000;
        InternalUser user = new InternalUser("internal-id", "alice", "Alice", false, true, 7);
        CurrentIdentity currentIdentity = mock(CurrentIdentity.class);
        when(currentIdentity.requireUser()).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new LoginUserPrincipal(user.id(), user.username(), user.displayName(), false, 7, oldLogin), null,
                java.util.List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/workbench/bind");

        ResponseEntity<Map<String, Object>> response = new WorkbenchLoginController(currentIdentity)
                .beginExplicitBinding(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(null, request.getSession(false));
    }
}
