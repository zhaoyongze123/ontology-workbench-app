package com.devin.ontology.strategy.storage.impl;

import com.devin.ontology.common.exception.ClientException;
import com.devin.ontology.domain.dto.NodeDTO;
import com.devin.ontology.common.utils.r.ResultEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2026/10/16 16:28:10.
 *
 * <p>
 * 验证 AGE 写入失败保留首个错误并回滚外层事务。
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
class AgeStrategyTransactionTest {

    /**
     * 验证首次 SQL 失败后不在已中止的事务内继续执行。
     *
     * @throws SQLException 模拟连接配置失败时抛出
     */
    @Test
    void shouldPreserveFirstFailureAndRollbackOuterTransaction() throws SQLException {
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AtomicBoolean aborted = new AtomicBoolean();
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (aborted.get()) {
                throw new UncategorizedSQLException("执行 SQL", sql,
                        new SQLException("current transaction is aborted", "25P02"));
            }
            if (sql.contains("ag_catalog.cypher")) {
                aborted.set(true);
                throw new UncategorizedSQLException("执行 SQL", sql,
                        new SQLException("unhandled cypher(cstring) function call", "XX000"));
            }
            return null;
        }).when(jdbcTemplate).execute(anyString());

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of("graph.database.type=age").applyTo(context);
            context.register(TransactionConfiguration.class);
            context.registerBean("transactionManager", DataSourceTransactionManager.class, () -> transactionManager);
            context.registerBean(AgeStrategy.class, () -> new AgeStrategy(jdbcTemplate, new ObjectMapper(), null, null));
            context.refresh();
            AgeStrategy strategy = context.getBean(AgeStrategy.class);
            NodeDTO nodeDTO = NodeDTO.builder().dbPrefix("ontology").databaseId(1L).id(1L)
                    .labels(List.of("设计原则")).properties(Map.of()).build();

            assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> strategy.createNode(nodeDTO)))
                    .isInstanceOfSatisfying(ClientException.class,
                            exception -> assertThat(exception.getErrMsg())
                                    .isEqualTo(ResultEnum.AGE_ERROR.getMessage())
                                    .doesNotContain("unhandled cypher(cstring) function call", "25P02"));
            verify(jdbcTemplate, times(2)).execute(anyString());
            verify(connection).rollback();
        }
    }

    /**
     * 2026/10/16 16:28:10.
     *
     * <p>
     * 启用与服务一致的事务和重试代理。
     * </p>
     *
     * @author <a href="https://github.com/wzh-devin">devin</a>
     * @version 1.0.0
     * @since 1.0.0
     */
    @Configuration(proxyBeanMethods = false)
    @EnableRetry(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfiguration {
    }
}
