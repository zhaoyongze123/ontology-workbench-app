package com.devin.ontology.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.security.CurrentIdentity;
import com.devin.ontology.security.PublicOrigin;
import com.devin.ontology.security.SessionRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;

class AccountLifecycleServiceTest {
    private final AccountLifecycleService service = new AccountLifecycleService(mock(JdbcTemplate.class),
            mock(TransactionOperations.class), mock(CurrentIdentity.class), mock(PasswordEncoder.class),
            mock(SessionRevocationService.class), "https://ontology.example");

    @Test
    void actionTokenMustHaveExactRandomTokenShapeBeforeAnyDatabaseLookup() {
        assertThatThrownBy(() -> service.inspectActionToken("short"))
                .isInstanceOfSatisfying(OntologyApiException.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.getErrorCode())
                                .isEqualTo(ApiErrorCode.ACCOUNT_ACTION_INVALID));
    }

    @Test
    void passwordMustMeetMinimumLengthBeforeTokenConsumption() {
        assertThatThrownBy(() -> service.activate("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "too-short"))
                .isInstanceOfSatisfying(OntologyApiException.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.getErrorCode())
                                .isEqualTo(ApiErrorCode.PASSWORD_POLICY_VIOLATION));
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
