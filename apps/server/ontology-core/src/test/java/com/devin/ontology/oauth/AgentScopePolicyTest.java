package com.devin.ontology.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AgentScopePolicyTest {
    private final AgentScopePolicy policy = new AgentScopePolicy();

    @Test
    void queryPostIsReadOnlyAndDoesNotNeedWriteScope() {
        Authentication read = authentication("ontology.read");
        assertTrue(allowed(read, "POST", "/cypher/query"));
        assertFalse(allowed(read, "POST", "/node/saveNode"));
    }

    @Test
    void managementScopeDoesNotImplicitlyGrantGraphWrites() {
        Authentication manage = authentication("ontology.manage", "profile");
        assertTrue(allowed(manage, "GET", "/oidc/app/userinfo"));
        assertTrue(allowed(manage, "POST", "/projects/10/invitations"));
        assertFalse(allowed(manage, "POST", "/node/saveNode"));
    }

    @Test
    void viewerCannotWriteEvenWithSystemAdminAuthorityUnlessWriteScopeWasGranted() {
        Authentication viewerWriteScope = authentication("ontology.write");
        assertTrue(allowed(viewerWriteScope, "POST", "/node/saveNode"));
        // 本測試只驗證 Scope 層；ProjectAccessService 仍以即時成員角色拒絕 VIEWER。
    }

    @Test
    void projectDeletionRequiresManageAndUnknownRoutesFailClosed() {
        Authentication read = authentication("ontology.read");
        Authentication write = authentication("ontology.write");
        Authentication manage = authentication("ontology.manage");

        assertFalse(allowed(read, "POST", "/organization/delete"));
        assertFalse(allowed(write, "POST", "/organization/delete"));
        assertTrue(allowed(manage, "POST", "/organization/delete"));
        assertTrue(allowed(read, "GET", "/organization/page"));
        assertFalse(allowed(write, "GET", "/organization/page"));
        assertTrue(allowed(write, "POST", "/graph/import/json"));
        assertFalse(allowed(write, "GET", "/graph/unclassified"));
        assertFalse(allowed(write, "POST", "/graph/unclassified"));
    }

    private boolean allowed(final Authentication authentication, final String method, final String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        return policy.isAllowed(authentication, request);
    }

    private Authentication authentication(final String... scopes) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(scopes)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope)).toList();
        return UsernamePasswordAuthenticationToken.authenticated("user-1", null, authorities);
    }
}
