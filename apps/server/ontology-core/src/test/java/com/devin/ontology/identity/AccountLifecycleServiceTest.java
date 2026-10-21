package com.devin.ontology.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.OntologyApiException;
import com.devin.ontology.security.CurrentIdentity;
import com.devin.ontology.security.PublicOrigin;
import com.devin.ontology.security.SessionRevocationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

    @SuppressWarnings("unchecked")
    @Test
    void adminUserSearchUsesBoundedParameterizedNameMatching() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CurrentIdentity identity = mock(CurrentIdentity.class);
        when(identity.requireUser()).thenReturn(new InternalUser("admin-id", "admin", "Admin", true, true, 0));
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        AccountLifecycleService searchable = new AccountLifecycleService(jdbc,
                mock(TransactionOperations.class), identity, mock(PasswordEncoder.class),
                mock(SessionRevocationService.class), "https://ontology.example");

        searchable.listUsers(0, 20, "Alice%");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForObject(sql.capture(), eq(Long.class), parameters.capture());
        org.assertj.core.api.Assertions.assertThat(sql.getValue())
                .contains("username ILIKE ?", "display_name ILIKE ?", "ESCAPE");
        org.assertj.core.api.Assertions.assertThat(parameters.getValue())
                .containsExactly("%Alice\\%%", "%Alice\\%%");
    }
}
