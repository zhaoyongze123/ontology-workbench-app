package com.devin.ontology.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.devin.ontology.identity.InternalUser;
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
}
