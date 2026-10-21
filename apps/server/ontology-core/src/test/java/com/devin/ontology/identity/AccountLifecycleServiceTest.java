package com.devin.ontology.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.devin.ontology.security.CurrentIdentity;
import com.devin.ontology.security.PublicOrigin;
import com.devin.ontology.security.SessionRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionOperations;

class AccountLifecycleServiceTest {
    private final AccountLifecycleService service = new AccountLifecycleService(mock(JdbcTemplate.class),
            mock(TransactionOperations.class), mock(CurrentIdentity.class), mock(PasswordEncoder.class),
            mock(SessionRevocationService.class), "https://ontology.example");

    @Test
    void actionTokenMustHaveExactRandomTokenShapeBeforeAnyDatabaseLookup() {
        assertThatThrownBy(() -> service.inspectActionToken("short"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void passwordMustMeetMinimumLengthBeforeTokenConsumption() {
        assertThatThrownBy(() -> service.activate("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "too-short"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void publicOriginCannotContainAnUntrustedPathOrFragment() {
        for (String invalid : new String[] {
                "https://user:password@ontology.example",
                "https://ontology.example/unexpected",
                "https://ontology.example?next=/evil",
                "https://ontology.example#fragment"
        }) {
            assertThatThrownBy(() -> new AccountLifecycleService(mock(JdbcTemplate.class),
                    mock(TransactionOperations.class), mock(CurrentIdentity.class), mock(PasswordEncoder.class),
                    mock(SessionRevocationService.class), invalid))
                    .as("public origin %s must be rejected", invalid)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void publicOriginAcceptsAndNormalizesATrailingRootSlash() {
        new AccountLifecycleService(mock(JdbcTemplate.class), mock(TransactionOperations.class),
                mock(CurrentIdentity.class), mock(PasswordEncoder.class), mock(SessionRevocationService.class),
                "https://ontology.example/");
        org.assertj.core.api.Assertions.assertThat(PublicOrigin.parse("https://ontology.example/"))
                .isEqualTo("https://ontology.example");
    }
}
