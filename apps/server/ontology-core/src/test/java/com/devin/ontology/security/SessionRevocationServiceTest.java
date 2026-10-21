package com.devin.ontology.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.identity.InternalUser;
import org.assertj.core.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

class SessionRevocationServiceTest {
    @Test
    void incrementsAuthVersionAndWritesAuditWithinOneCall() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("UPDATE ontology.og_user"), any(Object[].class))).thenReturn(1);
        SessionRevocationService service = new SessionRevocationService(jdbc);
        InternalUser actor = new InternalUser("user-1", "alice", "Alice", false, true, 12);

        service.revokeAllSessions(actor);

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).update(contains("auth_version = auth_version + 1"), any(Object[].class));
        order.verify(jdbc).update(contains("USER_SESSIONS_REVOKED"), any(Object[].class));
    }

    @Test
    void administratorRevocationTargetsTheRequestedUsersSessionsAndAppGrants() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("auth_version = auth_version + 1"), any(Object[].class))).thenReturn(1);
        SessionRevocationService service = new SessionRevocationService(jdbc);
        InternalUser actor = new InternalUser("admin-1", "admin", "Admin", true, true, 3);
        ArgumentCaptor<Object[]> targetArgs = ArgumentCaptor.forClass(Object[].class);
        ArgumentCaptor<Object[]> auditArgs = ArgumentCaptor.forClass(Object[].class);

        service.revokeAllSessions(actor, "target-2");

        InOrder order = inOrder(jdbc);
        order.verify(jdbc).update(contains("auth_version = auth_version + 1"), targetArgs.capture());
        order.verify(jdbc).update(contains("UPDATE ontology.og_app_authorization"), targetArgs.capture());
        order.verify(jdbc).update(contains("UPDATE ontology.og_app_refresh_token_history"), targetArgs.capture());
        order.verify(jdbc).update(contains("USER_SESSIONS_REVOKED"), auditArgs.capture());
        Assertions.assertThat(targetArgs.getAllValues())
                .allSatisfy(arguments -> Assertions.assertThat(arguments).containsExactly("target-2"));
        Assertions.assertThat(auditArgs.getValue()).containsExactly("admin-1", "target-2");
    }
}
