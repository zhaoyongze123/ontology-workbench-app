package com.devin.ontology.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 在一次性 PostgreSQL/AGE 库中验证迁移、运行及未来对象权限边界。 */
@SpringBootTest(classes = com.devin.ontology.OntologyServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("multiuser")
@EnabledIfEnvironmentVariable(named = "ONTOLOGY_IT_JDBC_URL", matches = ".*ontology_multiuser_dev_20261020.*")
class DatabasePrivilegesIntegrationTest {
    private static final Set<String> APPLICATION_TABLES = Set.of(
            "og_account_action_token", "og_app_authorization", "og_app_authorization_consent",
            "og_app_client", "og_app_refresh_token_history", "og_audit_event", "og_audit_retention_policy",
            "og_bootstrap_run", "og_credential_request_bucket", "og_entity", "og_entity_relationship",
            "og_external_identity", "og_graph", "og_login_attempt_bucket", "og_ontology",
            "og_ontology_relationship", "og_organization", "og_password_credential",
            "og_project_invitation", "og_project_member", "og_user", "spring_session",
            "spring_session_attributes");
    private static final Set<String> PRIVATE_TABLES = Set.of(
            "og_audit_retention_policy", "og_bootstrap_run", "flyway_schema_history");

    @Autowired
    private JdbcTemplate runtimeJdbc;

    @DynamicPropertySource
    static void runtimeProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DatabasePrivilegesIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> requiredEnvironment("JDBC_USER"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("JDBC_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("graph.database.type", () -> "age");
        registry.add("graph.database.age.preloaded", () -> "true");
        registry.add("ontology.workbench.enabled", () -> "false");
    }

    @Test
    void runtimeLoginIsRestrictedAndApplicationTablesHaveExactAcl() {
        assertThat(runtimeJdbc.queryForObject("SELECT session_user", String.class))
                .isEqualTo(requiredEnvironment("JDBC_USER"));
        assertThat(runtimeJdbc.queryForObject("SELECT current_user", String.class))
                .isEqualTo("ontology_app_runtime");
        assertThat(runtimeJdbc.queryForMap("SELECT rolcanlogin, rolsuper, rolcreatedb, rolcreaterole, "
                + "rolbypassrls, rolreplication FROM pg_catalog.pg_roles WHERE rolname = session_user"))
                .containsEntry("rolcanlogin", true)
                .containsEntry("rolsuper", false)
                .containsEntry("rolcreatedb", false)
                .containsEntry("rolcreaterole", false)
                .containsEntry("rolbypassrls", false)
                .containsEntry("rolreplication", false);
        assertThat(runtimeJdbc.queryForMap("SELECT rolsuper, rolcreatedb, rolcreaterole, rolbypassrls, "
                + "rolreplication FROM pg_catalog.pg_roles WHERE rolname = current_user"))
                .containsEntry("rolsuper", false)
                .containsEntry("rolcreatedb", false)
                .containsEntry("rolcreaterole", false)
                .containsEntry("rolbypassrls", false)
                .containsEntry("rolreplication", false);
        assertThat(hasDatabasePrivilege("CONNECT")).isTrue();
        assertThat(hasDatabasePrivilege("CREATE")).isFalse();
        assertThat(hasDatabasePrivilege("TEMP")).isFalse();
        assertThat(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.pg_has_role(session_user, 'ontology_app_runtime', 'MEMBER')", Boolean.class))
                .isTrue();
        assertThat(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.pg_has_role(session_user, 'ontology_app_runtime', 'USAGE')", Boolean.class))
                .isFalse();
        assertThat(hasSchemaPrivilege("ontology", "USAGE")).isTrue();
        assertThat(hasSchemaPrivilege("ontology", "CREATE")).isFalse();
        assertThat(hasSchemaPrivilege("ag_catalog", "USAGE")).isTrue();
        assertThat(hasSchemaPrivilege("ag_catalog", "CREATE")).isFalse();
        assertThat(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.pg_has_role(current_user, 'ontology_app_runtime', 'MEMBER')", Boolean.class))
                .isTrue();
        assertThat(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.pg_has_role(current_user, 'ontology_migrator', 'MEMBER')", Boolean.class))
                .isFalse();
        assertThat(runtimeJdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_class c "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'ontology' AND c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S') "
                + "AND c.relowner IN (SELECT oid FROM pg_catalog.pg_roles "
                + "WHERE rolname IN (session_user, current_user, 'ontology_app_runtime')))", Boolean.class)).isFalse();
        assertThat(runtimeJdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_class c "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                + "CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(c.relacl, "
                + "pg_catalog.acldefault(CASE WHEN c.relkind = 'S' THEN 's'::\"char\" ELSE 'r'::\"char\" END, c.relowner))) a "
                + "WHERE n.nspname = 'ontology' AND a.grantee = (SELECT oid FROM pg_catalog.pg_roles "
                + "WHERE rolname = session_user))", Boolean.class)).isFalse();
        assertThat(runtimeJdbc.queryForObject("SELECT EXISTS ("
                + "SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                + "CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(c.relacl, pg_catalog.acldefault("
                + "CASE WHEN c.relkind = 'S' THEN 's'::\"char\" ELSE 'r'::\"char\" END, c.relowner))) a "
                + "WHERE n.nspname = 'ontology' AND a.grantee = 0 UNION ALL "
                + "SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
                + "CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(p.proacl, "
                + "pg_catalog.acldefault('f'::\"char\", p.proowner))) a "
                + "WHERE n.nspname = 'ontology' AND a.grantee = 0)", Boolean.class)).isFalse();

        Set<String> observed = new HashSet<>(runtimeJdbc.queryForList(
                "SELECT c.relname FROM pg_catalog.pg_class c "
                        + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'ontology' AND c.relkind IN ('r', 'p', 'v', 'm', 'f')",
                String.class));
        Set<String> expected = new HashSet<>(APPLICATION_TABLES);
        if (observed.contains("flyway_schema_history")) expected.add("flyway_schema_history");
        assertThat(observed).containsExactlyInAnyOrderElementsOf(expected);

        for (String table : APPLICATION_TABLES) {
            boolean privateTable = PRIVATE_TABLES.contains(table);
            boolean auditTable = "og_audit_event".equals(table);
            assertPrivilege(table, "SELECT", !privateTable);
            assertPrivilege(table, "INSERT", !privateTable);
            assertPrivilege(table, "UPDATE", !privateTable && !auditTable);
            assertPrivilege(table, "DELETE", !privateTable && !auditTable);
            assertPrivilege(table, "TRUNCATE", false);
            assertPrivilege(table, "REFERENCES", false);
            assertPrivilege(table, "TRIGGER", false);
        }
        if (observed.contains("flyway_schema_history")) {
            for (String privilege : Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE")) {
                assertPrivilege("flyway_schema_history", privilege, false);
            }
        }

        Set<String> sequences = new HashSet<>(runtimeJdbc.queryForList(
                "SELECT c.relname FROM pg_catalog.pg_class c "
                        + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'ontology' AND c.relkind = 'S'", String.class));
        assertThat(sequences).containsExactly("og_audit_event_id_seq");
        assertSequencePrivilege("og_audit_event_id_seq", "USAGE", true);
        assertSequencePrivilege("og_audit_event_id_seq", "SELECT", true);
        assertSequencePrivilege("og_audit_event_id_seq", "UPDATE", false);
    }

    @Test
    void migrationLoginCanCreateFutureObjectsWithoutElevatedDatabasePrivileges() {
        JdbcTemplate migrator = migratorJdbc();
        assertThat(migrator.queryForMap("SELECT rolsuper, rolcreatedb, rolcreaterole, rolbypassrls, "
                + "rolreplication FROM pg_catalog.pg_roles WHERE rolname = current_user"))
                .containsEntry("rolsuper", false)
                .containsEntry("rolcreatedb", false)
                .containsEntry("rolcreaterole", false)
                .containsEntry("rolbypassrls", false)
                .containsEntry("rolreplication", false);
        assertThat(migrator.queryForObject(
                "SELECT pg_catalog.has_database_privilege(current_user, current_database(), 'CREATE')",
                Boolean.class)).isFalse();
        assertThat(migrator.queryForObject(
                "SELECT pg_catalog.has_schema_privilege(current_user, 'ontology', 'CREATE')", Boolean.class))
                .isTrue();
        assertThat(migrator.queryForObject(
                "SELECT pg_catalog.pg_has_role(current_user, 'ontology_age_graph_maintainer', 'MEMBER')",
                Boolean.class)).isFalse();
        assertThat(migrator.queryForObject(
                "SELECT pg_catalog.pg_has_role(current_user, 'ontology_audit_maintenance', 'MEMBER')",
                Boolean.class)).isFalse();

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String table = "future_acl_probe_" + suffix;
        String sequence = "future_acl_seq_" + suffix;
        String function = "future_acl_fn_" + suffix;
        try {
            migrator.execute("CREATE SEQUENCE ontology." + sequence);
            migrator.execute("CREATE TABLE ontology." + table + " (id BIGINT DEFAULT nextval('ontology."
                    + sequence + "'::regclass), value TEXT)");
            migrator.execute("CREATE FUNCTION ontology." + function
                    + "() RETURNS INTEGER LANGUAGE SQL IMMUTABLE AS 'SELECT 1'");

            assertThat(hasTablePrivilege(table, "SELECT")).isTrue();
            assertThat(hasTablePrivilege(table, "INSERT")).isTrue();
            assertThat(hasTablePrivilege(table, "UPDATE")).isTrue();
            assertThat(hasTablePrivilege(table, "DELETE")).isTrue();
            assertThat(hasTablePrivilege(table, "TRUNCATE")).isFalse();
            assertThat(hasSequencePrivilege(sequence, "USAGE")).isTrue();
            assertThat(hasSequencePrivilege(sequence, "SELECT")).isTrue();
            assertThat(hasSequencePrivilege(sequence, "UPDATE")).isFalse();
            assertThat(runtimeJdbc.queryForObject("SELECT pg_catalog.has_function_privilege(current_user, "
                    + "'ontology." + function + "()', 'EXECUTE')", Boolean.class)).isFalse();
            assertThat(runtimeJdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_proc p "
                    + "JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
                    + "CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(p.proacl, "
                    + "pg_catalog.acldefault('f', p.proowner))) a "
                    + "WHERE n.nspname = 'ontology' AND p.proname = ? AND a.grantee = 0 "
                    + "AND a.privilege_type = 'EXECUTE')", Boolean.class, function)).isFalse();

            runtimeJdbc.update("INSERT INTO ontology." + table + " (value) VALUES (?)", "future table access");
            assertThat(runtimeJdbc.queryForObject("SELECT value FROM ontology." + table, String.class))
                    .isEqualTo("future table access");
            JdbcTemplate bootstrap = bootstrapJdbc();
            assertThat(bootstrap.queryForObject("SELECT pg_catalog.has_table_privilege(current_user, ?, 'SELECT')",
                    Boolean.class, "ontology." + table)).isFalse();
            assertThatThrownBy(() -> bootstrap.queryForList("SELECT * FROM ontology." + table))
                    .hasStackTraceContaining("permission denied");
        } finally {
            migrator.execute("DROP FUNCTION IF EXISTS ontology." + function + "()");
            migrator.execute("DROP TABLE IF EXISTS ontology." + table);
            migrator.execute("DROP SEQUENCE IF EXISTS ontology." + sequence);
        }
    }

    @Test
    void runtimeCannotCreateDatabaseObjectsOrCallRawAgeGraphDdl() {
        assertThatThrownBy(() -> runtimeJdbc.execute("CREATE SCHEMA ontology_runtime_probe"))
                .hasStackTraceContaining("permission denied for database");
        assertThatThrownBy(() -> runtimeJdbc.execute("CREATE TABLE ontology.runtime_probe (id INTEGER)"))
                .hasStackTraceContaining("permission denied for schema ontology");
        assertThatThrownBy(() -> runtimeJdbc.queryForObject(
                "SELECT ag_catalog.create_graph(?::name)", Long.class, "ontology987654321"))
                .hasStackTraceContaining("permission denied for function create_graph");
        assertThatThrownBy(() -> runtimeJdbc.queryForObject(
                "SELECT ag_catalog.drop_graph(?::name, TRUE)", Boolean.class, "ontology987654321"))
                .hasStackTraceContaining("permission denied for function drop_graph");
    }

    @Test
    void runtimeCanManageOntologyGraphsOnlyThroughConstrainedOwnerFunctions() {
        String graphName = "ontology" + System.currentTimeMillis();
        String probeLabel = "future_label_" + UUID.randomUUID().toString().replace("-", "");
        String probeFunction = "future_fn_" + UUID.randomUUID().toString().replace("-", "");
        runtimeJdbc.execute("SELECT ontology.create_age_graph('" + graphName + "')");
        try {
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.pg_get_userbyid(n.nspowner) FROM pg_catalog.pg_namespace n "
                            + "WHERE n.nspname = ?", String.class, graphName))
                    .isEqualTo("ontology_age_graph_maintainer");
            assertThat(hasSchemaPrivilege(graphName, "USAGE")).isTrue();
            assertThat(hasSchemaPrivilege(graphName, "CREATE")).isTrue();
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.pg_get_userbyid(c.relowner) FROM pg_catalog.pg_class c "
                            + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE n.nspname = ? AND c.relname = '_ag_label_vertex'",
                    String.class, graphName)).isEqualTo("ontology_app_runtime");
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.has_sequence_privilege(current_user, ?, 'USAGE')",
                    Boolean.class, graphName + "._ag_label_vertex_id_seq")).isTrue();
            assertNoDirectOrPublicGraphAcl(graphName);

            // AGE 在写入时会动态创建标签表；新对象 owner 必须是 NOLOGIN 运行组。
            runtimeJdbc.execute("CREATE TABLE " + graphName + "." + probeLabel
                    + " (id BIGSERIAL PRIMARY KEY, value TEXT)");
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.pg_get_userbyid(c.relowner) FROM pg_catalog.pg_class c "
                            + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE n.nspname = ? AND c.relname = ?",
                    String.class, graphName, probeLabel)).isEqualTo("ontology_app_runtime");
            assertNoDirectOrPublicGraphAcl(graphName);
            runtimeJdbc.execute("CREATE FUNCTION " + graphName + "." + probeFunction
                    + "() RETURNS INTEGER LANGUAGE SQL IMMUTABLE AS 'SELECT 1'");
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.pg_get_userbyid(p.proowner) FROM pg_catalog.pg_proc p "
                            + "JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
                            + "WHERE n.nspname = ? AND p.proname = ?",
                    String.class, graphName, probeFunction)).isEqualTo("ontology_app_runtime");
            assertNoDirectOrPublicGraphAcl(graphName);
            assertThatThrownBy(() -> runtimeJdbc.execute("DROP SCHEMA " + graphName + " CASCADE"))
                    .hasStackTraceContaining("must be owner of schema");
        } finally {
            runtimeJdbc.execute("DROP FUNCTION IF EXISTS " + graphName + "." + probeFunction + "()");
            runtimeJdbc.execute("DROP TABLE IF EXISTS " + graphName + "." + probeLabel);
            runtimeJdbc.execute("SELECT ontology.drop_age_graph('" + graphName + "')");
        }
        assertThat(runtimeJdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_namespace WHERE nspname = ?)",
                Boolean.class, graphName)).isFalse();
    }

    private void assertPrivilege(final String table, final String privilege, final boolean expected) {
        assertThat(hasTablePrivilege(table, privilege))
                .as("runtime %s on ontology.%s", privilege, table)
                .isEqualTo(expected);
    }

    private boolean hasTablePrivilege(final String table, final String privilege) {
        return runtimeJdbc.queryForObject(
                "SELECT pg_catalog.has_table_privilege(current_user, ?, ?)", Boolean.class,
                "ontology." + table, privilege);
    }

    private void assertSequencePrivilege(final String sequence, final String privilege, final boolean expected) {
        assertThat(hasSequencePrivilege(sequence, privilege))
                .as("runtime %s on ontology.%s", privilege, sequence)
                .isEqualTo(expected);
    }

    private boolean hasSequencePrivilege(final String sequence, final String privilege) {
        return runtimeJdbc.queryForObject(
                "SELECT pg_catalog.has_sequence_privilege(current_user, ?, ?)", Boolean.class,
                "ontology." + sequence, privilege);
    }

    private boolean hasDatabasePrivilege(final String privilege) {
        return runtimeJdbc.queryForObject(
                "SELECT pg_catalog.has_database_privilege(session_user, current_database(), ?)",
                Boolean.class, privilege);
    }

    private void assertNoDirectOrPublicGraphAcl(final String graphSchema) {
        assertThat(runtimeJdbc.queryForObject("SELECT EXISTS ("
                + "SELECT 1 FROM pg_catalog.pg_namespace n "
                + "CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(n.nspacl, "
                + "pg_catalog.acldefault('n'::\"char\", n.nspowner))) a "
                + "WHERE n.nspname = ? AND (a.grantee = (SELECT oid FROM pg_catalog.pg_roles "
                + "WHERE rolname = session_user) OR a.grantee = 0) "
                + "UNION ALL "
                + "SELECT 1 FROM pg_catalog.pg_class c "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace "
                + "CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(c.relacl, "
                + "pg_catalog.acldefault(CASE WHEN c.relkind = 'S' THEN 's'::\"char\" ELSE 'r'::\"char\" END, c.relowner))) a "
                + "WHERE n.nspname = ? AND (a.grantee = (SELECT oid FROM pg_catalog.pg_roles "
                + "WHERE rolname = session_user) OR a.grantee = 0) "
                + "UNION ALL "
                + "SELECT 1 FROM pg_catalog.pg_proc p "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace "
                + "CROSS JOIN LATERAL pg_catalog.aclexplode(COALESCE(p.proacl, "
                + "pg_catalog.acldefault('f'::\"char\", p.proowner))) a "
                + "WHERE n.nspname = ? AND (a.grantee = (SELECT oid FROM pg_catalog.pg_roles "
                + "WHERE rolname = session_user) OR a.grantee = 0))", Boolean.class,
                graphSchema, graphSchema, graphSchema)).isFalse();
    }

    private boolean hasSchemaPrivilege(final String schema, final String privilege) {
        return runtimeJdbc.queryForObject(
                "SELECT pg_catalog.has_schema_privilege(current_user, ?, ?)", Boolean.class, schema, privilege);
    }

    private static JdbcTemplate migratorJdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(jdbcUrl());
        dataSource.setUsername("ontology_migrator");
        dataSource.setPassword(requiredEnvironment("ONTOLOGY_IT_MIGRATOR_PASSWORD"));
        return new JdbcTemplate(dataSource);
    }

    private static JdbcTemplate bootstrapJdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(jdbcUrl());
        dataSource.setUsername(requiredEnvironment("ONTOLOGY_IT_BOOTSTRAP_USER"));
        dataSource.setPassword(requiredEnvironment("ONTOLOGY_IT_BOOTSTRAP_PASSWORD"));
        return new JdbcTemplate(dataSource);
    }

    private static String jdbcUrl() {
        String url = requiredEnvironment("ONTOLOGY_IT_JDBC_URL");
        if (!url.matches("(?i)^jdbc:postgresql://[^/]+/ontology_multiuser_dev_20261020(?:\\?.*)?$")) {
            throw new IllegalStateException("Database privilege tests require the named isolated development copy");
        }
        String withoutCurrentSchema = url.replaceAll("(?i)([?&])currentSchema=[^&]*", "")
                .replaceAll("[?&]$", "");
        return withoutCurrentSchema + (withoutCurrentSchema.contains("?") ? "&" : "?")
                + "currentSchema=ontology";
    }

    private static String requiredEnvironment(final String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required database privilege-test setting is missing");
        }
        return value;
    }
}
