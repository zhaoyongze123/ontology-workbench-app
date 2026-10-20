package com.devin.ontology.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devin.ontology.identity.IdentityRepository;
import com.devin.ontology.identity.InternalUser;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** MockMvc 覆盖会话撤销边界；仓储完全 mock，不启动应用或外部服务。 */
class SecuritySessionMockMvcTest {
    private static final String USER_ID = "user-1";
    private static final long NOW = System.currentTimeMillis();
    private IdentityRepository identities;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        identities = org.mockito.Mockito.mock(IdentityRepository.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new SessionValidityFilter(identities)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passiveMeProbeDoesNotRefreshRealActivity() throws Exception {
        when(identities.findById(USER_ID)).thenReturn(Optional.of(enabledUser(7)));
        MockHttpSession session = session(NOW - 10_000, NOW - 500);

        mockMvc.perform(get("/auth/me").session(session))
                .andExpect(status().isNoContent());

        assertEquals(NOW - 500, session.getAttribute(SessionValidityFilter.LAST_REAL_ACTIVITY));
    }

    @Test
    void absoluteLifetimeInvalidatesSessionEvenWhenRecentlyActive() throws Exception {
        when(identities.findById(USER_ID)).thenReturn(Optional.of(enabledUser(7)));
        MockHttpSession session = session(NOW - SessionValidityFilter.ABSOLUTE_LIMIT_MILLIS - 1, NOW);

        mockMvc.perform(get("/organization/1").session(session))
                .andExpect(status().isUnauthorized());

        assertInvalidated(session);
        org.mockito.Mockito.verifyNoInteractions(identities);
    }

    @Test
    void idleLifetimeInvalidatesSession() throws Exception {
        when(identities.findById(USER_ID)).thenReturn(Optional.of(enabledUser(7)));
        MockHttpSession session = session(NOW - 5_000,
                NOW - SessionValidityFilter.IDLE_LIMIT_MILLIS - 1);

        mockMvc.perform(get("/organization/1").session(session))
                .andExpect(status().isUnauthorized());

        assertInvalidated(session);
        org.mockito.Mockito.verifyNoInteractions(identities);
    }

    @Test
    void changedAuthVersionRevokesExistingSession() throws Exception {
        when(identities.findById(USER_ID)).thenReturn(Optional.of(enabledUser(8)));
        MockHttpSession session = session(NOW - 5_000, NOW - 1_000);

        mockMvc.perform(get("/organization/1").session(session))
                .andExpect(status().isUnauthorized());

        verify(identities).findById(USER_ID);
        assertInvalidated(session);
    }

    @Test
    void providerNeverStoresCredentialHashInAuthenticatedPrincipal() {
        InternalUser user = enabledUser(7);
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65_536, 3);
        when(identities.findByUsername(user.username())).thenReturn(Optional.of(user));
        when(identities.findPasswordHash(USER_ID)).thenReturn(Optional.of(encoder.encode("correct horse")));
        DatabaseAuthenticationProvider provider = new DatabaseAuthenticationProvider(identities, encoder);

        var result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                user.username(), "correct horse"));

        LoginUserPrincipal principal = assertInstanceOf(LoginUserPrincipal.class, result.getPrincipal());
        assertEquals(USER_ID, principal.userId());
        assertEquals(7, principal.authVersion());
        assertEquals(null, result.getCredentials());
    }

    @Test
    void providerUsesSameCredentialFailureForUnknownDisabledAndLongInputs() {
        Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 65_536, 3);
        DatabaseAuthenticationProvider provider = new DatabaseAuthenticationProvider(identities, encoder);
        when(identities.findByUsername("missing")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("missing", "x")));
        verify(identities).findByUsername("missing");
        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("x".repeat(121), "x")));
    }

    private MockHttpSession session(final long loginAt, final long lastActivity) {
        LoginUserPrincipal principal = new LoginUserPrincipal(USER_ID, "alice", "Alice", false, 7, loginAt);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionValidityFilter.LAST_REAL_ACTIVITY, lastActivity);
        return session;
    }

    private InternalUser enabledUser(final long version) {
        return new InternalUser(USER_ID, "alice", "Alice", false, true, version);
    }

    private void assertInvalidated(final MockHttpSession session) {
        // MockHttpSession 的 getId() 在 invalidate() 后仍返回 ID；以其失效状态验证容器行为。
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @RestController
    static class ProbeController {
        @GetMapping("/auth/me")
        org.springframework.http.ResponseEntity<Void> me() {
            return org.springframework.http.ResponseEntity.noContent().build();
        }

        @GetMapping("/organization/1")
        org.springframework.http.ResponseEntity<Void> authenticatedOnly() {
            return SecurityContextHolder.getContext().getAuthentication() == null
                    ? org.springframework.http.ResponseEntity.status(401).build()
                    : org.springframework.http.ResponseEntity.noContent().build();
        }
    }
}
