package com.devin.ontology.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devin.ontology.OntologyServerApplication;
import com.devin.ontology.identity.BootstrapService.BootstrapResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/**
 * 仅连接明确命名的一次性空库，验证生产首管初始化及事务回滚。
 * 运行前设置 ONTOLOGY_PRODUCTION_BOOTSTRAP_IT_JDBC_URL 指向 ontology_*_it 数据库，
 * 并设置既有 ONTOLOGY_IT_BOOTSTRAP_USER/PASSWORD；执行
 * mvn -f apps/server/ontology-core/pom.xml -Dtest=ProductionBootstrapDatabaseIntegrationTest test。
 */
@SpringBootTest(classes = OntologyServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("multiuser")
@EnabledIfEnvironmentVariable(named = "ONTOLOGY_PRODUCTION_BOOTSTRAP_IT_JDBC_URL",
        matches = ".*/ontology_[a-zA-Z0-9_]*_it(?:\\?.*)?")
class ProductionBootstrapDatabaseIntegrationTest {
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ProductionBootstrapDatabaseIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", () -> required("ONTOLOGY_DB_USER"));
        registry.add("spring.datasource.password", () -> required("ONTOLOGY_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("graph.database.type", () -> "age");
        registry.add("graph.database.age.preloaded", () -> "true");
        registry.add("ontology.workbench.enabled", () -> "false");
    }

    @Test
    void initializesOnlyFirstAdminAndRejectsChangedInputWithoutPersistingTestData() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.postgresql.Driver");
        source.setUrl(jdbcUrl());
        source.setUsername(required("ONTOLOGY_IT_BOOTSTRAP_USER"));
        source.setPassword(required("ONTOLOGY_IT_BOOTSTRAP_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(source);
        Path directory = Files.createTempDirectory("ontology-production-bootstrap-it-");
        Path input = directory.resolve("admin.json");
        try {
            Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
            Files.writeString(input, "{\"schemaVersion\":1,\"username\":\"root-admin\","
                    + "\"displayName\":\"Production Admin\",\"password\":\"a-random-production-password\"}");
            Files.setPosixFilePermissions(input, Set.of(PosixFilePermission.OWNER_READ));

            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            BootstrapService service = new BootstrapService(jdbc, transaction, passwordEncoder, objectMapper);
            verifyConcurrentCallWaitsForBootstrapLock(source, transaction, service, input);
            transaction.execute(status -> {
                assertThat(service.applyProduction(input).applied()).isTrue();
                assertThat(service.applyProduction(input).applied()).isFalse();
                assertThat(jdbc.queryForObject("SELECT COUNT(username) FROM ontology.og_user WHERE username = ?",
                        Integer.class, "root-admin")).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT COUNT(id) FROM ontology.og_organization", Integer.class)).isZero();
                assertThatThrownBy(() -> {
                    try {
                        Files.setPosixFilePermissions(input, Set.of(PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE));
                        Files.writeString(input, "{\"schemaVersion\":1,\"username\":\"root-admin\","
                                + "\"displayName\":\"Changed\",\"password\":\"another-random-password\"}");
                        Files.setPosixFilePermissions(input, Set.of(PosixFilePermission.OWNER_READ));
                        service.applyProduction(input);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }).hasRootCauseMessage("production bootstrap 已执行且输入不一致；拒绝修改现有身份");
                status.setRollbackOnly();
                return null;
            });
            assertThat(jdbc.queryForObject("SELECT COUNT(username) FROM ontology.og_user WHERE username = ?",
                    Integer.class, "root-admin")).isZero();
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    private static void verifyConcurrentCallWaitsForBootstrapLock(
            final DriverManagerDataSource source,
            final TransactionTemplate transaction,
            final BootstrapService service,
            final Path input) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection lockConnection = source.getConnection()) {
            lockConnection.setAutoCommit(false);
            try (PreparedStatement statement = lockConnection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(7194563108322024)")) {
                statement.execute();
            }
            CountDownLatch started = new CountDownLatch(1);
            Future<BootstrapResult> blockedCall = executor.submit(() -> {
                started.countDown();
                return transaction.execute(status -> {
                    BootstrapResult result = service.applyProduction(input);
                    status.setRollbackOnly();
                    return result;
                });
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(200);
            assertThat(blockedCall.isDone()).isFalse();
            lockConnection.commit();
            BootstrapResult result = blockedCall.get(10, TimeUnit.SECONDS);
            assertThat(result.applied()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private static String jdbcUrl() {
        String url = required("ONTOLOGY_PRODUCTION_BOOTSTRAP_IT_JDBC_URL");
        if (!url.matches("(?i)^jdbc:postgresql://[^/]+/ontology_[a-z0-9_]*_it(?:\\?.*)?$")) {
            throw new IllegalStateException("Production bootstrap integration requires its dedicated empty database");
        }
        return url;
    }

    private static String required(final String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required production bootstrap integration setting is missing");
        }
        return value;
    }
}
