package com.devin.ontology.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devin.ontology.OntologyServerApplication;
import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.domain.dto.NodeDTO;
import com.devin.ontology.domain.entity.Organization;
import com.devin.ontology.strategy.storage.AgeSql;
import com.devin.ontology.strategy.storage.impl.AgeStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 仅对已准备好的 ontology_multiuser_dev_20261020 副本验证项目、AGE 与 JDBC 共享事务。
 * 测试不清理既有数据；每次生成全新的项目/图标识，验证后通过回滚撤销。
 */
@SpringBootTest(classes = OntologyServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("multiuser")
@EnabledIfEnvironmentVariable(named = "ONTOLOGY_IT_JDBC_URL", matches = ".*ontology_multiuser_dev_20261020.*")
class MultiuserDatabaseIntegrationTest {

    private static final String PROPERTY_VALUE = "$$ 'quoted' \\ slash\n中文 $properties";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OrganizationDao organizationDao;

    @Autowired
    private AgeStrategy ageStrategy;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void databaseProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MultiuserDatabaseIntegrationTest::jdbcUrlForOntologySchema);
        registry.add("spring.datasource.username", () -> requiredEnvironment("JDBC_USER"));
        registry.add("spring.datasource.password", () -> requiredEnvironment("JDBC_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.main.web-application-type", () -> "none");
        registry.add("spring.datasource.druid.initial-size", () -> "1");
        registry.add("spring.datasource.druid.min-idle", () -> "0");
        registry.add("spring.datasource.druid.max-active", () -> "5");
        registry.add("graph.database.type", () -> "age");
        registry.add("graph.database.age.preloaded", () -> "true");
        registry.add("ontology.workbench.enabled", () -> "false");
    }

    @Test
    void mybatisJdbcAndAgeWritesRollbackTogether() {
        assertRuntimeDatabaseBoundary();
        long uniqueId = freshPositiveId();
        BigInteger projectId = BigInteger.valueOf(uniqueId);
        String graphName = AgeSql.graphName("ontology", uniqueId);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            saveProject(projectId, "rollback-project-" + UUID.randomUUID());
            ageStrategy.createDatabase("ontology", uniqueId);
            ageStrategy.createNode(node(uniqueId, uniqueId, PROPERTY_VALUE));
            throw new DeliberateRollback();
        })).isInstanceOf(DeliberateRollback.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ontology.og_organization WHERE id = ?", Long.class, projectId)).isZero();
        assertThat(graphNamespaceExists(graphName)).isFalse();
    }

    @Test
    void ageRoundTripsHostileLookingTextExactlyAndRollbackLeavesNoGraph() throws Exception {
        assertRuntimeDatabaseBoundary();
        long uniqueId = freshPositiveId();
        String graphName = AgeSql.graphName("ontology", uniqueId);
        String label = "IdentityBoundary" + uniqueId;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ageStrategy.createDatabase("ontology", uniqueId);
            ageStrategy.createNode(node(uniqueId, uniqueId, PROPERTY_VALUE, label));
            String cypher = "MATCH (n:`" + label + "`) WHERE n._id = " + uniqueId
                    + " RETURN n.payload AS payload";
            String agtypeValue = jdbcTemplate.queryForObject(
                    AgeSql.cypherSql(graphName, cypher, List.of("payload")),
                    (resultSet, rowNum) -> resultSet.getString(1));
            String decodedValue = decodeAgtypeString(agtypeValue);
            assertThat(decodedValue).isEqualTo(PROPERTY_VALUE);

            // 显式标记回滚，AGE 图 schema 和节点都不应残留在测试副本中。
            status.setRollbackOnly();
        });

        assertThat(graphNamespaceExists(graphName)).isFalse();
    }

    private void assertRuntimeDatabaseBoundary() {
        assertThat(transactionManager).isInstanceOf(JdbcTransactionManager.class);
        assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("ontology_multiuser_dev_20261020");
        assertThat(jdbcTemplate.queryForObject("SELECT current_schema()", String.class))
                .isEqualTo("ontology");
        Boolean superuser = jdbcTemplate.queryForObject(
                "SELECT r.rolsuper FROM pg_catalog.pg_roles r WHERE r.rolname = current_user", Boolean.class);
        assertThat(superuser).isFalse();
    }

    private void saveProject(final BigInteger id, final String name) {
        Date now = new Date();
        Organization organization = new Organization();
        organization.setId(id);
        organization.setName(name);
        organization.setDescription("integration transaction rollback probe");
        organization.setCreateTime(now);
        organization.setUpdateTime(now);
        assertThat(organizationDao.save(organization)).isTrue();
    }

    private NodeDTO node(final long graphId, final long nodeId, final String value) {
        return node(graphId, nodeId, value, "IdentityBoundary" + graphId);
    }

    private NodeDTO node(final long graphId, final long nodeId, final String value, final String label) {
        return NodeDTO.builder().dbPrefix("ontology").databaseId(graphId).id(nodeId)
                .labels(List.of(label)).properties(Map.of("payload", value)).build();
    }

    private boolean graphNamespaceExists(final String graphName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT pg_catalog.to_regnamespace(?) IS NOT NULL", Boolean.class, graphName);
        return Boolean.TRUE.equals(exists);
    }

    private String decodeAgtypeString(final String agtypeValue) {
        try {
            return objectMapper.readValue(agtypeValue, String.class);
        } catch (Exception exception) {
            throw new AssertionError("AGE string result was not valid JSON string data", exception);
        }
    }

    private static String jdbcUrlForOntologySchema() {
        String url = requiredEnvironment("ONTOLOGY_IT_JDBC_URL");
        // 环境变量过滤器外再校验一次目标数据库名，避免仅在 query 参数中命中副本名称。
        if (!url.matches("(?i)^jdbc:postgresql://[^/]+/ontology_multiuser_dev_20261020(?:\\?.*)?$")) {
            throw new IllegalStateException("Database integration test is restricted to the named development copy");
        }
        String withoutCurrentSchema = url.replaceAll("(?i)([?&])currentSchema=[^&]*", "")
                .replaceAll("[?&]$", "");
        return withoutCurrentSchema + (withoutCurrentSchema.contains("?") ? "&" : "?")
                + "currentSchema=ontology";
    }

    private static String requiredEnvironment(final String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required database integration-test environment variable is missing");
        }
        return value;
    }

    private static long freshPositiveId() {
        long timePart = Math.multiplyExact(System.currentTimeMillis(), 1000L);
        long randomPart = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, 1000);
        return timePart + randomPart;
    }

    private static final class DeliberateRollback extends RuntimeException {
        private DeliberateRollback() {
            super("intentional integration-test rollback");
        }
    }
}
