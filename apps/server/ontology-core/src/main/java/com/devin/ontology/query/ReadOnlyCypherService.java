package com.devin.ontology.query;

import com.devin.ontology.common.enums.GraphTypeEnum;
import com.devin.ontology.common.exception.ValidateException;
import com.devin.ontology.dao.GraphDao;
import com.devin.ontology.domain.entity.Graph;
import com.devin.ontology.domain.vo.CypherQueryVO;
import com.devin.ontology.project.ProjectAccessService;
import com.devin.ontology.project.ProjectCapability;
import com.devin.ontology.strategy.storage.AgeSql;
import com.devin.ontology.strategy.storage.ChartStorageStrategy;
import com.devin.ontology.strategy.storage.impl.AgeStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionTemplate;

/** 对既有图谱执行受限、限时、只读的自由 Cypher 查询。 */
@Service
public class ReadOnlyCypherService {
    private static final int MAX_ROWS = 1_000;
    private static final int MAX_CELL_BYTES = 256 * 1024;
    private static final long MAX_RESULT_BYTES = 2L * 1024 * 1024;

    private final ProjectAccessService projectAccessService;
    private final GraphDao graphDao;
    private final JdbcTemplate jdbcTemplate;
    private final ChartStorageStrategy storageStrategy;
    private final CypherReadOnlyValidator validator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ReadOnlyCypherService(final ProjectAccessService projectAccessService, final GraphDao graphDao,
            final JdbcTemplate jdbcTemplate, final PlatformTransactionManager transactionManager,
            final ChartStorageStrategy storageStrategy, final CypherReadOnlyValidator validator,
            final ObjectMapper objectMapper) {
        this.projectAccessService = projectAccessService;
        this.graphDao = graphDao;
        this.jdbcTemplate = jdbcTemplate;
        this.storageStrategy = storageStrategy;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setReadOnly(true);
        this.transactionTemplate.setTimeout(5);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Collection<Map<String, Object>> query(final CypherQueryVO query) {
        if (query == null || query.getGraphId() == null || query.getGraphType() == null) {
            throw new ValidateException("图谱编号和类型不能为空");
        }
        List<String> outputNames = validator.validateAndGetColumns(query.getCypher());
        try {
            return transactionTemplate.execute(status -> executeReadOnly(query, outputNames));
        } catch (AccessDeniedException | ValidateException expected) {
            throw expected;
        } catch (DataAccessException | TransactionTimedOutException databaseFailure) {
            if (isQueryTimeout(databaseFailure)) {
                throw new ValidateException("查询超过 5 秒限制");
            }
            throw new ValidateException("查询语法不受支持或执行失败");
        }
    }

    private Collection<Map<String, Object>> executeReadOnly(final CypherQueryVO query,
            final List<String> outputNames) {
        String readOnly = jdbcTemplate.queryForObject("SHOW transaction_read_only", String.class);
        if (!"on".equalsIgnoreCase(readOnly)) {
            throw new IllegalStateException("数据库没有进入只读事务");
        }
        jdbcTemplate.queryForObject("SELECT set_config('statement_timeout', ?, true)", String.class, "5000ms");

        Graph graph = graphDao.getById(query.getGraphId());
        if (graph == null) {
            // 不区分资源不存在与无权访问，避免通过此查询入口枚举图谱。
            throw new AccessDeniedException("图谱不可访问");
        }
        projectAccessService.requireProject(graph.getOrganizationId(), ProjectCapability.READ);

        GraphTypeEnum actualType;
        try {
            actualType = GraphTypeEnum.valueOf(graph.getType());
        } catch (RuntimeException invalidStoredType) {
            throw new ValidateException("图谱存储类型无效");
        }
        if (actualType != query.getGraphType()) {
            throw new ValidateException("请求图谱类型与实际图谱不匹配");
        }

        final long graphId;
        try {
            graphId = graph.getId().longValueExact();
        } catch (ArithmeticException | NullPointerException invalidId) {
            throw new ValidateException("图谱编号超出支持范围");
        }
        final String graphName;
        try {
            graphName = AgeSql.graphName(actualType.getDbPrefix(), graphId);
        } catch (IllegalArgumentException invalidGraphId) {
            throw new ValidateException("图谱编号无效");
        }
        if (!(storageStrategy instanceof AgeStrategy ageStrategy)) {
            throw new ValidateException("当前图存储不支持自由 Cypher 查询");
        }

        // 先用应用默认 search_path 读取 og_graph/ACL；仅在 AGE 执行前收紧函数解析路径。
        jdbcTemplate.queryForObject("SELECT set_config('search_path', ?, true)", String.class,
                "ag_catalog,pg_catalog");
        String definitions = IntStream.range(0, outputNames.size())
                .mapToObj(index -> AgeSql.identifier("c" + index) + " ag_catalog.agtype")
                .collect(Collectors.joining(", "));
        String sql = AgeSql.cypher(graphName, query.getCypher(), definitions) + " LIMIT " + (MAX_ROWS + 1);
        PreparedStatementCreator statementCreator = connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            // PostgreSQL 只有在事务中并设置 fetch size 时才会用游标分批拉取结果。
            statement.setFetchSize(100);
            return statement;
        };
        return jdbcTemplate.query(statementCreator, (ResultSetExtractor<Collection<Map<String, Object>>>) rows -> {
            long totalBytes = 2; // JSON 数组的左右方括号
            List<Map<String, Object>> results = new ArrayList<>();
            int rowCount = 0;
            while (rows.next()) {
                if (++rowCount > MAX_ROWS) {
                    cancelStatement(rows);
                    throw new ValidateException("查询结果超过 1000 行上限");
                }
                Map<String, Object> rawRow = new LinkedHashMap<>();
                for (int index = 0; index < outputNames.size(); index++) {
                    String rawName = "c" + index;
                    Object value = rows.getObject(rawName);
                    int cellBytes = rawCellSize(value);
                    if (cellBytes > MAX_CELL_BYTES) {
                        cancelStatement(rows);
                        throw new ValidateException("查询单元格超过 256 KiB 上限");
                    }
                    rawRow.put(rawName, value);
                }
                Map<String, Object> converted = ageStrategy.convertRecord(rawRow);
                Map<String, Object> namedRow = new LinkedHashMap<>();
                for (int index = 0; index < outputNames.size(); index++) {
                    String rawName = "c" + index;
                    if (!converted.containsKey(rawName)) {
                        throw new IllegalStateException("AGE 返回列与 Cypher 投影不匹配");
                    }
                    namedRow.put(outputNames.get(index), converted.get(rawName));
                }
                try {
                    // 以将要返回给调用方的 JSON 行计量，覆盖别名、转义和分隔符开销。
                    totalBytes += objectMapper.writeValueAsBytes(namedRow).length;
                } catch (JsonProcessingException serializationFailure) {
                    throw new ValidateException("查询结果无法序列化");
                }
                if (rowCount > 1) totalBytes++; // JSON 数组中的行间逗号
                if (totalBytes > MAX_RESULT_BYTES) {
                    cancelStatement(rows);
                    throw new ValidateException("查询结果超过 2 MiB 上限");
                }
                results.add(namedRow);
            }
            return results;
        });
    }

    private void cancelStatement(final ResultSet rows) {
        try {
            Statement statement = rows.getStatement();
            if (statement != null) statement.cancel();
        } catch (SQLException ignored) {
            // JdbcTemplate 随后关闭 ResultSet 和 Statement，原始资源超限错误优先返回。
        }
    }

    private boolean isQueryTimeout(final Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof QueryTimeoutException || cause instanceof TransactionTimedOutException
                    || cause instanceof java.sql.SQLTimeoutException
                    || (cause instanceof SQLException sqlException && "57014".equals(sqlException.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    private int rawCellSize(final Object value) {
        if (value == null) return 0;
        if (value instanceof byte[] bytes) return bytes.length;
        Object measure = value instanceof PGobject pgObject ? pgObject.getValue() : value;
        if (measure == null) return 0;
        return measure.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
