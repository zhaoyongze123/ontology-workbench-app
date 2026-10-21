package com.devin.ontology.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devin.ontology.OntologyServerApplication;
import com.devin.ontology.identity.BootstrapService.BootstrapResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/** 在一次性 PostgreSQL/AGE 库中证明专用 bootstrap 身份可工作且不能越权。 */
@SpringBootTest(classes = OntologyServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("multiuser")
@EnabledIfEnvironmentVariable(named = "ONTOLOGY_IT_JDBC_URL", matches = ".*ontology_multiuser_dev_20261020.*")
class BootstrapDatabaseIntegrationTest {
    private static final String BOOTSTRAP_JSON = """
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

    @Autowired private JdbcTemplate runtimeJdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void runtimeProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", BootstrapDatabaseIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> requiredEnvironment("JDBC_USER"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("JDBC_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("graph.database.type", () -> "age");
        registry.add("graph.database.age.preloaded", () -> "true");
        registry.add("ontology.workbench.enabled", () -> "false");
    }

    @Test
    void dedicatedMaintenanceIdentityCanApplyAndVerifyBootstrapWithoutRuntimeLedgerAccess() throws Exception {
        DriverManagerDataSource bootstrapDataSource = new DriverManagerDataSource();
        bootstrapDataSource.setDriverClassName("org.postgresql.Driver");
        bootstrapDataSource.setUrl(jdbcUrl());
        bootstrapDataSource.setUsername(requiredEnvironment("ONTOLOGY_IT_BOOTSTRAP_USER"));
        bootstrapDataSource.setPassword(requiredEnvironment("ONTOLOGY_IT_BOOTSTRAP_PASSWORD"));
        JdbcTemplate bootstrapJdbc = new JdbcTemplate(bootstrapDataSource);

        assertThat(bootstrapJdbc.queryForObject("SELECT session_user", String.class))
                .isEqualTo(requiredEnvironment("ONTOLOGY_IT_BOOTSTRAP_USER"));
        assertThat(bootstrapJdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("ontology_bootstrap_maintenance");
        assertThat(bootstrapJdbc.queryForObject(
                "SELECT pg_catalog.has_database_privilege(current_user, current_database(), 'CREATE')", Boolean.class))
                .isFalse();
        assertThat(bootstrapJdbc.queryForObject(
                "SELECT pg_catalog.has_database_privilege(current_user, current_database(), 'TEMP')", Boolean.class))
                .isFalse();
        assertThat(bootstrapJdbc.queryForObject(
                "SELECT pg_catalog.has_schema_privilege(current_user, 'ontology', 'CREATE')", Boolean.class))
                .isFalse();
        assertColumnPrivilege(bootstrapJdbc, "og_bootstrap_run", "input_digest", "SELECT", true);
        assertColumnPrivilege(bootstrapJdbc, "og_bootstrap_run", "seed_key", "INSERT", true);
        assertColumnPrivilege(bootstrapJdbc, "og_user", "username", "SELECT", true);
        assertColumnPrivilege(bootstrapJdbc, "og_password_credential", "user_id", "SELECT", true);
        assertColumnPrivilege(bootstrapJdbc, "og_password_credential", "password_hash", "SELECT", false);
        assertColumnPrivilege(bootstrapJdbc, "og_organization", "bootstrap_key", "SELECT", true);
        assertColumnPrivilege(bootstrapJdbc, "og_project_member", "role", "SELECT", true);
        assertColumnPrivilege(bootstrapJdbc, "og_audit_event", "event_type", "INSERT", true);
        assertThat(bootstrapJdbc.queryForObject("SELECT pg_catalog.has_table_privilege(current_user, "
                + "'ontology.og_bootstrap_run', 'UPDATE')", Boolean.class)).isFalse();
        assertThat(bootstrapJdbc.queryForObject("SELECT pg_catalog.has_table_privilege(current_user, "
                + "'ontology.og_bootstrap_run', 'DELETE')", Boolean.class)).isFalse();
        assertThat(bootstrapJdbc.queryForObject("SELECT pg_catalog.has_table_privilege(current_user, "
                + "'ontology.og_app_client', 'SELECT')", Boolean.class)).isFalse();
        assertThat(bootstrapJdbc.queryForObject("SELECT pg_catalog.has_sequence_privilege(current_user, "
                + "'ontology.og_audit_event_id_seq', 'USAGE')", Boolean.class)).isTrue();
        assertThat(bootstrapJdbc.queryForObject("SELECT pg_catalog.has_sequence_privilege(current_user, "
                + "'ontology.og_audit_event_id_seq', 'UPDATE')", Boolean.class)).isFalse();

        Path privateDirectory = Files.createTempDirectory("ontology-bootstrap-it-");
        Path privateInput = privateDirectory.resolve("bootstrap.json");
        try {
            Files.setPosixFilePermissions(privateDirectory, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            Files.writeString(privateInput, BOOTSTRAP_JSON);
            Files.setPosixFilePermissions(privateInput, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));

            TransactionTemplate transaction = new TransactionTemplate(
                    new DataSourceTransactionManager(bootstrapDataSource));
            BootstrapService service = new BootstrapService(bootstrapJdbc, transaction, passwordEncoder, objectMapper);
            List<BootstrapResult> results = transaction.execute(status -> {
                BootstrapResult applied = service.apply(privateInput);
                BootstrapResult verified = service.apply(privateInput);
                assertThat(bootstrapJdbc.queryForObject(
                        "SELECT COUNT(seed_key) FROM ontology.og_bootstrap_run WHERE seed_key = ?",
                        Integer.class, "multiuser-bootstrap-v1")).isEqualTo(1);
                status.setRollbackOnly();
                return List.of(applied, verified);
            });

            assertThat(results).hasSize(2);
            assertThat(results.get(0).applied()).isTrue();
            assertThat(results.get(1).applied()).isFalse();
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM ontology.og_user WHERE username IN ('admin', 'u2', 'u3', 'u4', 'u5', 'u6', 'u7')",
                    Integer.class)).isZero();
            assertThatThrownBy(() -> runtimeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM ontology.og_bootstrap_run", Integer.class))
                    .hasStackTraceContaining("permission denied");
        } finally {
            Files.deleteIfExists(privateInput);
            Files.deleteIfExists(privateDirectory);
        }
    }

    private static void assertColumnPrivilege(final JdbcTemplate jdbc, final String table, final String column,
            final String privilege, final boolean expected) {
        assertThat(jdbc.queryForObject("SELECT pg_catalog.has_column_privilege(current_user, ?, ?, ?)",
                Boolean.class, "ontology." + table, column, privilege))
                .as("bootstrap %s on ontology.%s.%s", privilege, table, column)
                .isEqualTo(expected);
    }

    private static String jdbcUrl() {
        String url = requiredEnvironment("ONTOLOGY_IT_JDBC_URL");
        if (!url.matches("(?i)^jdbc:postgresql://[^/]+/ontology_multiuser_dev_20261020(?:\\?.*)?$")) {
            throw new IllegalStateException("Bootstrap integration requires the named isolated development copy");
        }
        String withoutCurrentSchema = url.replaceAll("(?i)([?&])currentSchema=[^&]*", "")
                .replaceAll("[?&]$", "");
        return withoutCurrentSchema + (withoutCurrentSchema.contains("?") ? "&" : "?")
                + "currentSchema=ontology";
    }

    private static String requiredEnvironment(final String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required bootstrap integration setting is missing");
        }
        return value;
    }
}
