package com.devin.ontology.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.identity.InternalUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.SecurityContextRepository;

class WorkbenchOidcSuccessHandlerTest {
    private static final long AUTH_VERSION = 4;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginConsumesSavedAppReturnExactlyOnce() throws Exception {
        String returnPath = "/api/v1/oidc/app/authorize?client_id=app&state=opaque";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oidc/callback");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE, returnPath);
        request.setSession(session);
        var handler = handler();
        var authentication = authentication();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, firstResponse, authentication);

        assertThat(firstResponse.getRedirectedUrl()).isEqualTo(returnPath);
        assertThat(session.getAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE)).isNull();
        assertThat(firstResponse.getRedirectedUrl()).doesNotContain("token", "secret", "code=");

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, secondResponse, authentication);
        assertThat(secondResponse.getRedirectedUrl()).isEqualTo("/ontology-management/");
    }

    @Test
    void explicitBindingAlwaysDiscardsAnyAppReturn() throws Exception {
        long now = System.currentTimeMillis();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oidc/callback");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE,
                "/api/v1/oidc/app/authorize?client_id=app&state=opaque");
        session.setAttribute(PendingWorkbenchBinding.SESSION_ATTRIBUTE,
                new PendingWorkbenchBinding("user-1", AUTH_VERSION, now, now + 30_000));
        request.setSession(session);
        var handler = handler();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("/ontology-management/");
        assertThat(session.getAttribute(WorkbenchOAuthReturnPath.SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(PendingWorkbenchBinding.SESSION_ATTRIBUTE)).isNull();
    }

    private WorkbenchOidcSuccessHandler handler() {
        WorkbenchIdentityService identities = mock(WorkbenchIdentityService.class);
        InternalUser user = new InternalUser("user-1", "alice", "Alice", false, true, AUTH_VERSION);
        when(identities.loginOrCreate(org.mockito.ArgumentMatchers.any())).thenReturn(user);
        when(identities.bind(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.eq(AUTH_VERSION))).thenReturn(user);
        WorkbenchOidcProperties properties = new WorkbenchOidcProperties();
        properties.setIssuer("https://workbench.example");
        SecurityContextRepository contexts = mock(SecurityContextRepository.class);
        return new WorkbenchOidcSuccessHandler(identities, properties, new ObjectMapper(), contexts);
    }

    private TestingAuthenticationToken authentication() {
        OidcUser user = mock(OidcUser.class);
        when(user.getClaimAsString("iss")).thenReturn("https://workbench.example");
        when(user.getSubject()).thenReturn("verified-subject");
        when(user.getClaimAsString("name")).thenReturn("Alice");
        when(user.getClaimAsString("email")).thenReturn("alice@example.test");
        return new TestingAuthenticationToken(user, null);
    }
}
