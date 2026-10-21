package com.devin.ontology.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.dao.GraphDao;
import com.devin.ontology.domain.entity.Graph;
import com.devin.ontology.domain.vo.CypherQueryVO;
import com.devin.ontology.project.ProjectAccessService;
import com.devin.ontology.project.ProjectCapability;
import com.devin.ontology.strategy.storage.ChartStorageStrategy;
import com.devin.ontology.strategy.storage.impl.AgeStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ReadOnlyCypherServiceTest {
    private ProjectAccessService access;
    private GraphDao graphDao;
    private JdbcTemplate jdbc;
    private PlatformTransactionManager transactionManager;
    private AgeStrategy ageStrategy;
    private CypherQueryVO query;
    private ReadOnlyCypherService service;

    @BeforeEach
    void setUp() throws Exception {
        access = mock(ProjectAccessService.class);
        graphDao = mock(GraphDao.class);
        jdbc = mock(JdbcTemplate.class);
        transactionManager = mock(PlatformTransactionManager.class);
        ageStrategy = mock(AgeStrategy.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForObject("SHOW transaction_read_only", String.class)).thenReturn("on");
        when(jdbc.queryForObject("SELECT set_config('search_path', ?, true)", String.class, "ag_catalog,pg_catalog"))
                .thenReturn("ag_catalog,pg_catalog");
        when(jdbc.queryForObject("SELECT set_config('statement_timeout', ?, true)", String.class, "5000ms"))
                .thenReturn("5000ms");
        when(graphDao.getById(BigInteger.valueOf(42))).thenReturn(graph());
        when(ageStrategy.convertRecord(anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ReadOnlyCypherService(access, graphDao, jdbc, transactionManager,
                (ChartStorageStrategy) ageStrategy, new CypherReadOnlyValidator(), new ObjectMapper());
        query = new CypherQueryVO();
        query.setGraphId(BigInteger.valueOf(42));
        query.setGraphType(GraphTypeEnum.ENTITY);
        query.setCypher("MATCH (n) RETURN n.name AS name");
    }

    @Test
    void readsOnlyTheStoredGraphAfterCheckingProjectReadAccess() throws Exception {
        ResultSet rows = resultSet(List.of("Alice"));
        when(jdbc.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> extract(invocation.getArgument(1), rows));

        Collection<Map<String, Object>> result = service.query(query);

        assertEquals(List.of(Map.of("name", "Alice")), new ArrayList<>(result));
        verify(access).requireProject(BigInteger.valueOf(9), ProjectCapability.READ);
        InOrder order = org.mockito.Mockito.inOrder(jdbc, graphDao, access);
        order.verify(jdbc).queryForObject("SHOW transaction_read_only", String.class);
        order.verify(jdbc).queryForObject(
                "SELECT set_config('statement_timeout', ?, true)", String.class, "5000ms");
        order.verify(graphDao).getById(BigInteger.valueOf(42));
        order.verify(access).requireProject(BigInteger.valueOf(9), ProjectCapability.READ);
        order.verify(jdbc).queryForObject(
                "SELECT set_config('search_path', ?, true)", String.class, "ag_catalog,pg_catalog");
        order.verify(jdbc).query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class));
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertEquals(true, definition.getValue().isReadOnly());
        assertEquals(5, definition.getValue().getTimeout());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getValue().getPropagationBehavior());

        ArgumentCaptor<PreparedStatementCreator> creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).query(creator.capture(), any(ResultSetExtractor.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(any(String.class))).thenReturn(statement);
        creator.getValue().createPreparedStatement(connection);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        verify(statement).setFetchSize(100);
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("cypher(E'entity42'"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("AS (\"c0\" ag_catalog.agtype) LIMIT 1001"));
    }

    @Test
    void rejectsRequestTypeThatDoesNotMatchStoredGraphAfterPermissionCheck() {
        query.setGraphType(GraphTypeEnum.ONTOLOGY);

        assertThrows(ValidateException.class, () -> service.query(query));

        verify(access).requireProject(BigInteger.valueOf(9), ProjectCapability.READ);
        verify(jdbc, never()).query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class));
    }

    @Test
    void checksProjectReadPermissionInsideTheSameTransactionAsQuery() {
        doThrow(new AccessDeniedException("denied"))
                .when(access).requireProject(BigInteger.valueOf(9), ProjectCapability.READ);

        assertThrows(AccessDeniedException.class, () -> service.query(query));

        verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        verify(jdbc, never()).query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class));
    }

    @Test
    void rejectsDatabaseConnectionThatDidNotEnterReadOnlyTransaction() {
        when(jdbc.queryForObject("SHOW transaction_read_only", String.class)).thenReturn("off");

        assertThrows(IllegalStateException.class, () -> service.query(query));

        verify(jdbc, never()).query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class));
    }

    @Test
    void explicitlyRejectsNonAgeStorageAfterTheReadOnlyPermissionChecks() {
        ChartStorageStrategy nonAgeStorage = mock(ChartStorageStrategy.class);
        ReadOnlyCypherService nonAgeService = new ReadOnlyCypherService(access, graphDao, jdbc, transactionManager,
                nonAgeStorage, new CypherReadOnlyValidator(), new ObjectMapper());

        assertThrows(ValidateException.class, () -> nonAgeService.query(query));

        verify(nonAgeStorage, never()).query(any(), any(), any());
        verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        verify(access).requireProject(BigInteger.valueOf(9), ProjectCapability.READ);
        verify(jdbc, never()).queryForObject("SELECT set_config('search_path', ?, true)", String.class,
                "ag_catalog,pg_catalog");
        verify(jdbc, never()).query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class));
    }

    @Test
    void failsInsteadOfSilentlyTruncatingRowsOrExceedingCellAndTotalBytes() throws Exception {
        List<String> tooManyRows = new ArrayList<>();
        for (int i = 0; i < 1001; i++) tooManyRows.add("x");
        assertThrows(ValidateException.class, () -> queryWithRows(tooManyRows));

        assertThrows(ValidateException.class, () -> queryWithRows(List.of("x".repeat(256 * 1024 + 1))));

        List<String> tooManyBytes = new ArrayList<>();
        for (int i = 0; i < 9; i++) tooManyBytes.add("x".repeat(256 * 1024));
        assertThrows(ValidateException.class, () -> queryWithRows(tooManyBytes));

        // 控制器返回的是 JSON；反斜杠转义会使线上响应体大于原始单元格字节之和。
        List<String> escapedJsonOverflow = new ArrayList<>();
        for (int i = 0; i < 9; i++) escapedJsonOverflow.add("\\".repeat(120 * 1024));
        assertThrows(ValidateException.class, () -> queryWithRows(escapedJsonOverflow));

        // 列名也在每一行 JSON 中重复，不能被原始值字节统计漏掉。
        query.setCypher("MATCH (n) RETURN n.name AS " + "a".repeat(15_900));
        List<String> smallValues = new ArrayList<>();
        for (int i = 0; i < 140; i++) smallValues.add("x");
        assertThrows(ValidateException.class, () -> queryWithRows(smallValues));
    }

    private Collection<Map<String, Object>> queryWithRows(List<String> values) throws Exception {
        ResultSet rows = resultSet(values);
        when(jdbc.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class)))
                .thenAnswer(invocation -> extract(invocation.getArgument(1), rows));
        return service.query(query);
    }

    private ResultSet resultSet(List<String> values) throws Exception {
        ResultSet rows = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(rows.getStatement()).thenReturn(statement);
        final int[] index = {-1};
        when(rows.next()).thenAnswer(invocation -> ++index[0] < values.size());
        when(rows.getObject("c0")).thenAnswer(invocation -> values.get(index[0]));
        return rows;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object extract(ResultSetExtractor extractor, ResultSet rows) throws Exception {
        return extractor.extractData(rows);
    }

    private Graph graph() {
        Graph graph = new Graph();
        graph.setId(BigInteger.valueOf(42));
        graph.setOrganizationId(BigInteger.valueOf(9));
        graph.setType("ENTITY");
        return graph;
    }
}
