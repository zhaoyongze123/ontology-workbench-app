package com.devin.ontology.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.identity.BootstrapService.BootstrapInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class BootstrapServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void productionInputRequiresOneNamedAdminAndStrongNonDefaultPassword() {
        BootstrapService.ProductionBootstrapInput valid = new BootstrapService.ProductionBootstrapInput(
                1, "owner", "Production Owner", "a-long-random-production-password");
        BootstrapService.validateProduction(valid);

        assertThatThrownBy(() -> BootstrapService.validateProduction(
                new BootstrapService.ProductionBootstrapInput(1, "admin", "Admin", "admin")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BootstrapService.validateProduction(
                new BootstrapService.ProductionBootstrapInput(1, "owner", "Owner", "short")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BootstrapService.validateProduction(
                new BootstrapService.ProductionBootstrapInput(2, "owner", "Owner", "a-long-random-password")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAutomaticOrPublicBootstrapInput() {
        BootstrapInput input = input(true, false);
        assertThatThrownBy(() -> BootstrapService.validate(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("受控初始化契约");
    }

    @Test
    void rejectsInputOutsideLocalUsernamePasswordPolicy() {
        BootstrapInput nonLocalInput = read(validJson().replace(
                "\"environment\":\"local-development\"", "\"environment\":\"production\""));
        assertThatThrownBy(() -> BootstrapService.validate(nonLocalInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("受控初始化契约");

        BootstrapInput mismatchedInput = read(validJson()
                .replace("\"password\":\"admin\"", "\"password\":\"changed\""));
        assertThatThrownBy(() -> BootstrapService.validate(mismatchedInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("密码与用户名相同");
    }

    @Test
    void requiresUniqueOwnerPerProjectAndMatchingOwnerUsername() {
        String json = validJson().replace(
                "{\"projectKey\":\"p1\",\"role\":\"OWNER\"}",
                "{\"projectKey\":\"p1\",\"role\":\"EDITOR\"}");
        BootstrapInput input = read(json);
        assertThatThrownBy(() -> BootstrapService.validate(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OWNER 成员关系");
    }

    @Test
    void repeatedSameInputOnlyVerifiesLedgerAndNeverWrites(@TempDir Path tempDir) throws Exception {
        byte[] bytes = validJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path file = tempDir.resolve("bootstrap.json");
        Files.write(file, bytes);
        Files.setPosixFilePermissions(tempDir, Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
        Files.setPosixFilePermissions(file, Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(jdbc.query(contains("SELECT input_digest"), any(RowMapper.class), eq("multiuser-bootstrap-v1")))
                .thenReturn(List.of(digest));
        when(jdbc.query(contains("jsonb_array_elements_text"), any(RowMapper.class), eq("multiuser-bootstrap-v1")))
                .thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        BootstrapService service = new BootstrapService(jdbc, transactions, encoder, objectMapper);
        BootstrapService.BootstrapResult result = service.apply(file);

        assertThat(result.applied()).isFalse();
        assertThat(result.userCount()).isEqualTo(7);
        assertThat(result.projectCount()).isEqualTo(2);
        verify(jdbc).execute("SELECT pg_advisory_xact_lock(7194563108322024)");
        verify(transactions).execute(any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(encoder, never()).encode(anyString());
        verify(jdbc, never()).queryForObject(contains("og_project_member"), eq(Integer.class), any(Object[].class));
    }

    private BootstrapInput input(boolean auto, boolean publicEnabled) {
        return read(validJson().replace("\"applyAutomatically\":false", "\"applyAutomatically\":" + auto)
                .replace("\"publicBootstrapEnabled\":false", "\"publicBootstrapEnabled\":" + publicEnabled));
    }

    private BootstrapInput read(String json) {
        try {
            return objectMapper.readValue(json, BootstrapInput.class);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String validJson() {
        return """
                {"schemaVersion":1,"executionStatus":"not_applied","applyAutomatically":false,
                 "environment":"local-development","initialPasswordPolicy":"username_as_password_for_local_tests",
                 "publicBootstrapEnabled":false,"neverResetExistingPasswords":true,
                 "onExistingIdentityConflict":"fail_and_review","legacyProjectOwnerUsername":"admin",
                 "users":[
                   {"username":"admin","displayName":"Admin","password":"admin","systemAdmin":true,"memberships":[]},
                   {"username":"u2","displayName":"U2","password":"u2","systemAdmin":false,"memberships":[{"projectKey":"p1","role":"OWNER"}]},
                   {"username":"u3","displayName":"U3","password":"u3","systemAdmin":false,"memberships":[{"projectKey":"p1","role":"ADMIN"}]},
                   {"username":"u4","displayName":"U4","password":"u4","systemAdmin":false,"memberships":[]},
                   {"username":"u5","displayName":"U5","password":"u5","systemAdmin":false,"memberships":[]},
                   {"username":"u6","displayName":"U6","password":"u6","systemAdmin":false,"memberships":[]},
                   {"username":"u7","displayName":"U7","password":"u7","systemAdmin":false,"memberships":[{"projectKey":"p2","role":"OWNER"}]}
                 ],
                 "testProjects":[{"key":"p1","name":"Project 1","ownerUsername":"u2"},
                                 {"key":"p2","name":"Project 2","ownerUsername":"u7"}]}
                """;
    }
}
