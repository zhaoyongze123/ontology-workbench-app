package com.devin.ontology.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devin.ontology.OntologyServerApplication;
import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ApiErrorCode;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.dao.OrganizationDao;
import com.devin.ontology.domain.dto.NodeDTO;
import com.devin.ontology.domain.entity.Organization;
import com.devin.ontology.domain.vo.CypherQueryVO;
import com.devin.ontology.domain.vo.NodeDeleteVO;
import com.devin.ontology.domain.vo.RelationshipDeleteVO;
import com.devin.ontology.project.ProjectMembershipService;
import com.devin.ontology.security.LoginUserPrincipal;
import com.devin.ontology.strategy.graph.GraphStrategyFactory;
import com.devin.ontology.strategy.storage.AgeSql;
import com.devin.ontology.strategy.storage.impl.AgeStrategy;
import com.alibaba.fastjson2.JSONException;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** #17 的只读查询真实 PostgreSQL/AGE 安全边界；只连接明确命名的隔离副本。 */
@SpringBootTest(classes = OntologyServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("multiuser")
@EnabledIfEnvironmentVariable(named = "ONTOLOGY_IT_JDBC_URL", matches = ".*ontology_multiuser_dev_20261020.*")
class SecurityBoundaryDatabaseIntegrationTest {
    private static final AtomicLong NEXT_ID = new AtomicLong(System.currentTimeMillis() * 100_000L);
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrganizationDao organizationDao;
    @Autowired private AgeStrategy age;
    @Autowired private ReadOnlyCypherService cypher;
    @Autowired private ProjectMembershipService membership;
    @Autowired private GraphStrategyFactory graphStrategies;

    private String ownerId;
    private String outsiderId;
    private BigInteger projectId;
    private BigInteger graphId;
    private final List<BigInteger> fixtureProjects = new ArrayList<>();
    private final List<BigInteger> fixtureGraphs = new ArrayList<>();
    private final List<String> fixtureUsers = new ArrayList<>();

    @DynamicPropertySource
    static void databaseProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SecurityBoundaryDatabaseIntegrationTest::jdbcUrlForOntologySchema);
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

    @BeforeEach
    void prepareIsolatedFixture() {
        assertDatabaseBoundary();
        long id = freshId();
        projectId = BigInteger.valueOf(id);
        graphId = BigInteger.valueOf(id + 1);
        fixtureProjects.clear();
        fixtureGraphs.clear();
        fixtureUsers.clear();
        fixtureProjects.add(projectId);
        fixtureGraphs.add(graphId);
        ownerId = UUID.randomUUID().toString();
        outsiderId = UUID.randomUUID().toString();
        fixtureUsers.add(ownerId);
        fixtureUsers.add(outsiderId);
        jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, enabled) VALUES (?, ?, 'IT owner', TRUE), (?, ?, 'IT outsider', TRUE)",
                ownerId, "security_it_" + ownerId, outsiderId, "security_it_" + outsiderId);
        Organization project = new Organization();
        project.setId(projectId);
        project.setName("security-it-" + UUID.randomUUID());
        project.setDescription("isolated security integration fixture");
        project.setCreateTime(new Date());
        project.setUpdateTime(new Date());
        organizationDao.save(project);
        jdbc.update("INSERT INTO ontology.og_project_member(project_id, user_id, role) VALUES (?, ?, 'OWNER')",
                projectId, ownerId);
        jdbc.update("INSERT INTO ontology.og_graph(id, name, type, organization_id) VALUES (?, ?, 'ONTOLOGY', ?)",
                graphId, "security-it-graph-" + graphId, projectId);
        age.createDatabase("ontology", graphId.longValueExact());
        authenticate(ownerId);
    }

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
        for (BigInteger graph : fixtureGraphs) {
            String graphName = AgeSql.graphName("ontology", graph.longValueExact());
            Boolean exists = jdbc.queryForObject("SELECT pg_catalog.to_regnamespace(?) IS NOT NULL", Boolean.class,
                    graphName);
            if (Boolean.TRUE.equals(exists)) age.dropDatabase(List.of(graph.longValueExact()), "ontology");
            jdbc.update("DELETE FROM ontology.og_entity_relationship WHERE graph_id = ?", graph);
            jdbc.update("DELETE FROM ontology.og_ontology_relationship WHERE graph_id = ?", graph);
            jdbc.update("DELETE FROM ontology.og_entity WHERE graph_id = ?", graph);
            jdbc.update("DELETE FROM ontology.og_ontology WHERE graph_id = ?", graph);
            jdbc.update("DELETE FROM ontology.og_graph WHERE id = ?", graph);
        }
        for (BigInteger project : fixtureProjects) {
            jdbc.update("DELETE FROM ontology.og_organization WHERE id = ?", project);
        }
        for (String user : fixtureUsers) jdbc.update("DELETE FROM ontology.og_user WHERE id = ?", user);
    }

    @Test
    void realAgeQueryEnforcesRowsCellsAndSerializedResponseAndLeavesConnectionUsable() {
        CypherQueryVO query = query("UNWIND range(1, 1000) AS value RETURN value AS value");
        assertThat(cypher.query(query)).hasSize(1000);
        query.setCypher("UNWIND range(1, 1001) AS value RETURN value AS value");
        assertQueryFailure(query, ApiErrorCode.QUERY_RESULT_LIMIT_EXCEEDED);

        addNode("OversizeCell", "p".repeat(256 * 1024 + 1));
        assertQueryFailure(query("MATCH (n:OversizeCell) RETURN n.payload AS payload"),
                ApiErrorCode.QUERY_RESULT_LIMIT_EXCEEDED);

        for (int i = 0; i < 9; i++) addNode("LargeResult", "r".repeat(240 * 1024));
        assertQueryFailure(query("MATCH (n:LargeResult) RETURN n.payload AS payload"),
                ApiErrorCode.QUERY_RESULT_LIMIT_EXCEEDED);

        String alias = "a".repeat(15_900);
        String queryText = "UNWIND range(1, 140) AS value RETURN value AS " + alias;
        assertQueryFailure(query(queryText), ApiErrorCode.QUERY_RESULT_LIMIT_EXCEEDED);
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("off");
    }

    @Test
    void queryIsBoundToAuthorizedGraphAndRejectsWriteFunctionsAndCrossProjectReads() {
        assertThat(cypher.query(query("UNWIND range(1, 2) AS value RETURN value AS value"))).hasSize(2);
        CypherQueryVO wrongType = query("RETURN 1 AS value");
        wrongType.setGraphType(GraphTypeEnum.ENTITY);
        assertThatThrownBy(() -> cypher.query(wrongType)).isInstanceOf(ValidateException.class);
        assertThatThrownBy(() -> cypher.query(query("CALL db.labels()")))
                .isInstanceOf(ValidateException.class);
        assertThatThrownBy(() -> cypher.query(query("CREATE (n) RETURN n")))
                .isInstanceOf(ValidateException.class);

        authenticate(outsiderId);
        assertThatThrownBy(() -> cypher.query(query("RETURN 1 AS value")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void graphAndBulkNodeRelationshipIdsCannotCrossProjectBoundaries() {
        long foreignProjectValue = freshId();
        BigInteger foreignProject = BigInteger.valueOf(foreignProjectValue);
        BigInteger foreignGraph = BigInteger.valueOf(foreignProjectValue + 1);
        fixtureProjects.add(foreignProject);
        fixtureGraphs.add(foreignGraph);
        BigInteger localNode = BigInteger.valueOf(freshId());
        BigInteger foreignNode = BigInteger.valueOf(freshId());
        BigInteger foreignRelationship = BigInteger.valueOf(freshId());
        jdbc.update("INSERT INTO ontology.og_organization(id, name) VALUES (?, ?)",
                foreignProject, "security-it-foreign-" + foreignProject);
        jdbc.update("INSERT INTO ontology.og_graph(id, name, type, organization_id) VALUES (?, ?, 'ONTOLOGY', ?)",
                foreignGraph, "security-it-foreign-graph-" + foreignGraph, foreignProject);
        jdbc.update("INSERT INTO ontology.og_ontology(id, name, graph_id) VALUES (?, 'local', ?)", localNode, graphId);
        jdbc.update("INSERT INTO ontology.og_ontology(id, name, graph_id) VALUES (?, 'foreign', ?)", foreignNode, foreignGraph);
        jdbc.update("INSERT INTO ontology.og_ontology_relationship(id, source_node_id, target_node_id, graph_id, name) "
                        + "VALUES (?, ?, ?, ?, 'foreign relationship')",
                foreignRelationship, foreignNode, foreignNode, foreignGraph);

        var ontology = graphStrategies.getStrategy(GraphTypeEnum.ONTOLOGY);
        NodeDeleteVO nodeDelete = new NodeDeleteVO();
        nodeDelete.setGraphId(graphId);
        nodeDelete.setGraphType(GraphTypeEnum.ONTOLOGY);
        nodeDelete.setIdList(List.of(localNode, foreignNode));
        assertThatThrownBy(() -> ontology.deleteNode(nodeDelete)).isInstanceOf(AccessDeniedException.class);

        RelationshipDeleteVO relationshipDelete = new RelationshipDeleteVO();
        relationshipDelete.setGraphId(graphId);
        relationshipDelete.setGraphType(GraphTypeEnum.ONTOLOGY);
        relationshipDelete.setIdList(List.of(foreignRelationship));
        assertThatThrownBy(() -> ontology.deleteRelationship(relationshipDelete)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> ontology.getGraphInfo(foreignGraph)).isInstanceOf(AccessDeniedException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_ontology WHERE id = ?", Integer.class, foreignNode))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_ontology_relationship WHERE id = ?",
                Integer.class, foreignRelationship)).isEqualTo(1);
    }

    @Test
    void malformedOrCrossTypeImportsAreRejectedBeforeDelegateAndForeignExportsAreDenied() {
        var ontology = graphStrategies.getStrategy(GraphTypeEnum.ONTOLOGY);
        assertThatThrownBy(() -> ontology.importGraph("{\"graph\":{\"type\":\"ENTITY\"},"
                + "\"ontologyList\":[],\"ontologyRelationshipList\":[],\"entityList\":[],"
                + "\"entityRelationshipList\":[]}", projectId))
                .isInstanceOf(AccessDeniedException.class);
        long foreignProjectValue = freshId();
        BigInteger foreignProject = BigInteger.valueOf(foreignProjectValue);
        BigInteger foreignGraph = BigInteger.valueOf(foreignProjectValue + 1);
        fixtureProjects.add(foreignProject);
        fixtureGraphs.add(foreignGraph);
        jdbc.update("INSERT INTO ontology.og_organization(id, name) VALUES (?, ?)",
                foreignProject, "security-it-export-" + foreignProject);
        jdbc.update("INSERT INTO ontology.og_graph(id, name, type, organization_id) VALUES (?, ?, 'ONTOLOGY', ?)",
                foreignGraph, "security-it-export-graph-" + foreignGraph, foreignProject);
        assertThatThrownBy(() -> ontology.exportGraph(foreignGraph)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void invalidRelationshipSchemaDuringImportRollsBackCreatedGraphAndPreviouslySavedNodes() {
        var ontology = graphStrategies.getStrategy(GraphTypeEnum.ONTOLOGY);
        String marker = "security-it-import-" + UUID.randomUUID();
        long oldNodeId = freshId();
        long oldRelationshipId = freshId();
        String source = "{\"graph\":{\"id\":" + freshId() + ",\"name\":\"" + marker
                + "\",\"type\":\"ONTOLOGY\"},\"ontologyList\":[{\"id\":" + oldNodeId
                + ",\"name\":\"" + marker + "_node\",\"propertySchemaJson\":\"[]\"}],"
                + "\"ontologyRelationshipList\":[{\"id\":" + oldRelationshipId + ",\"name\":\""
                + marker + "_relationship\",\"sourceNodeId\":" + oldNodeId + ",\"targetNodeId\":"
                + oldNodeId + ",\"propertySchemaJson\":\"[invalid\"}],\"entityList\":[],"
                + "\"entityRelationshipList\":[]}";
        JdbcTemplate adminJdbc = new JdbcTemplate(administrativeDataSource());
        Long ageGraphCount = adminJdbc.queryForObject("SELECT COUNT(*) FROM ag_catalog.ag_graph", Long.class);

        assertThatThrownBy(() -> ontology.importGraph(source, projectId)).isInstanceOf(JSONException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_graph WHERE name = ?", Long.class, marker)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_ontology WHERE name = ?", Long.class,
                marker + "_node")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_ontology_relationship WHERE name = ?",
                Long.class, marker + "_relationship")).isZero();
        assertThat(adminJdbc.queryForObject("SELECT COUNT(*) FROM ag_catalog.ag_graph", Long.class))
                .as("AGE graph catalog should roll back with the failed import transaction")
                .isEqualTo(ageGraphCount);
    }

    @Test
    void blockedAgeCatalogLookupTimesOutAndReleasesTheConnection() throws Exception {
        DriverManagerDataSource admin = administrativeDataSource();
        try (Connection lockConnection = admin.getConnection(); Statement lock = lockConnection.createStatement()) {
            lockConnection.setAutoCommit(false);
            // 由维护账号取得目录锁；自由查询仍只使用非 superuser runtime 账号。
            lock.execute("LOCK TABLE ag_catalog.ag_graph IN ACCESS EXCLUSIVE MODE");
            CompletableFuture<Throwable> blocked = CompletableFuture.supplyAsync(() -> {
                authenticate(ownerId);
                try {
                    cypher.query(query("RETURN 1 AS value"));
                    return null;
                } catch (Throwable failure) {
                    return failure;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            try {
                Throwable failure = blocked.get(8, TimeUnit.SECONDS);
                assertThat(failure).isInstanceOfSatisfying(ValidateException.class,
                        invalid -> assertThat(invalid.getApiErrorCode()).isEqualTo(ApiErrorCode.QUERY_BUDGET_EXCEEDED));
            } catch (Exception failure) {
                throw new AssertionError("受控 AGE 锁等待未在查询超时内结束", failure);
            } finally {
                lockConnection.rollback();
            }
        }
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void competingOwnershipTransfersKeepExactlyOneOwner() throws Exception {
        String firstTarget = addMember("first-target");
        String secondTarget = addMember("second-target");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();
        CompletableFuture<Void> first = transferAsync(firstTarget, ready, start, rejected);
        CompletableFuture<Void> second = transferAsync(secondTarget, ready, start, rejected);
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        CompletableFuture.allOf(first, second).get(15, TimeUnit.SECONDS);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_project_member WHERE project_id = ? AND role = 'OWNER'",
                Integer.class, projectId)).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ontology.og_project_member WHERE project_id = ? AND role = 'ADMIN'",
                Integer.class, projectId)).isEqualTo(1);
    }

    private void addNode(final String label, final String payload) {
        NodeDTO node = NodeDTO.builder().dbPrefix("ontology").databaseId(graphId.longValueExact())
                .id(freshId()).labels(List.of(label)).properties(Map.of("payload", payload)).build();
        age.createNode(node);
    }

    private CypherQueryVO query(final String text) {
        CypherQueryVO result = new CypherQueryVO();
        result.setGraphId(graphId);
        result.setGraphType(GraphTypeEnum.ONTOLOGY);
        result.setCypher(text);
        return result;
    }

    private void assertQueryFailure(final CypherQueryVO query, final ApiErrorCode expectedCode) {
        assertThatThrownBy(() -> cypher.query(query)).isInstanceOfSatisfying(ValidateException.class,
                invalid -> assertThat(invalid.getApiErrorCode()).isEqualTo(expectedCode));
    }

    private void authenticate(final String id) {
        String username = jdbc.queryForObject("SELECT username FROM ontology.og_user WHERE id = ?", String.class, id);
        long version = jdbc.queryForObject("SELECT auth_version FROM ontology.og_user WHERE id = ?", Long.class, id);
        LoginUserPrincipal principal = new LoginUserPrincipal(id, username, username, false, version, System.currentTimeMillis());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private String addMember(final String label) {
        String id = UUID.randomUUID().toString();
        fixtureUsers.add(id);
        jdbc.update("INSERT INTO ontology.og_user(id, username, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                id, "security_it_" + id, "IT " + label);
        jdbc.update("INSERT INTO ontology.og_project_member(project_id, user_id, role) VALUES (?, ?, 'EDITOR')",
                projectId, id);
        return id;
    }

    private CompletableFuture<Void> transferAsync(final String target, final CountDownLatch ready,
            final CountDownLatch start, final AtomicInteger rejected) {
        return CompletableFuture.runAsync(() -> {
            authenticate(ownerId);
            ready.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent transfer did not start");
                membership.transferOwner(projectId, target);
            } catch (RuntimeException failure) {
                rejected.incrementAndGet();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private void assertDatabaseBoundary() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("ontology_multiuser_dev_20261020");
        Boolean superuser = jdbc.queryForObject(
                "SELECT r.rolsuper FROM pg_catalog.pg_roles r WHERE r.rolname = current_user", Boolean.class);
        assertThat(superuser).isFalse();
    }

    private DriverManagerDataSource administrativeDataSource() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("org.postgresql.Driver");
        source.setUrl(requiredEnvironment("ONTOLOGY_IT_JDBC_URL"));
        source.setUsername("ontology_it_admin");
        source.setPassword(requiredEnvironment("ONTOLOGY_IT_ADMIN_PASSWORD"));
        return source;
    }

    private static long freshId() {
        return NEXT_ID.incrementAndGet();
    }

    private static String jdbcUrlForOntologySchema() {
        String url = requiredEnvironment("ONTOLOGY_IT_JDBC_URL");
        if (!url.matches("(?i)^jdbc:postgresql://[^/]+/ontology_multiuser_dev_20261020(?:\\?.*)?$")) {
            throw new IllegalStateException("IT JDBC URL must target ontology_multiuser_dev_20261020");
        }
        return url + (url.contains("?") ? "&currentSchema=ontology" : "?currentSchema=ontology");
    }

    private static String requiredEnvironment(final String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required for database IT");
        return value;
    }
}
