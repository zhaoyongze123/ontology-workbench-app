package com.devin.ontology.strategy.storage.impl;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import com.devin.ontology.common.configuration.AgeConnectionInitializer;
import com.devin.ontology.domain.dto.NodeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 2026/10/16 16:30:54.
 *
 * <p>
 * 使用独立 PostgreSQL AGE 测试库验证物理连接初始化和写入原子性。
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "AGE_TEST_JDBC_URL", matches = ".+")
class AgeStrategyIntegrationTest {

    /**
     * 初始化独立测试库；URL 必须指向可清理的测试环境。
     *
     * @throws SQLException 数据库初始化失败时抛出
     */
    @BeforeAll
    static void prepareDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(System.getenv("AGE_TEST_JDBC_URL"), "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS age");
            statement.execute("CREATE SCHEMA IF NOT EXISTS ontology");
            statement.execute("CREATE TABLE IF NOT EXISTS ontology.node_write_audit (id bigint PRIMARY KEY)");
        }
    }

    /**
     * 验证池内多条连接及淘汰后的新连接均能执行图查询。
     */
    @Test
    void shouldInitializeEveryPhysicalConnectionAndReplacement() {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            AgeStrategy strategy = context.getBean(AgeStrategy.class);
            strategy.createDatabase("ontology", 101L);
            DruidDataSource dataSource = context.getBean(DruidDataSource.class);
            assertThat(dataSource.getConnectionInitSqls())
                    .containsExactly("LOAD 'age';", "SET application_name = 'ontology-age-regression'");
            Set<Integer> backendIdSet = new HashSet<>();
            try (Connection first = dataSource.getConnection();
                 Connection second = dataSource.getConnection();
                 Connection third = dataSource.getConnection()) {
                for (Connection connection : List.of(first, second, third)) {
                    try (Statement statement = connection.createStatement()) {
                        try (ResultSet resultSet = statement.executeQuery("SELECT pg_backend_pid()")) {
                            assertThat(resultSet.next()).isTrue();
                            backendIdSet.add(resultSet.getInt(1));
                        }
                        statement.execute("SET search_path = ag_catalog, ontology");
                        try (ResultSet resultSet = statement.executeQuery(
                                "SELECT * FROM ag_catalog.cypher('ontology101', $$RETURN 1$$) AS (n ag_catalog.agtype)")) {
                            assertThat(resultSet.next()).isTrue();
                            assertThat(resultSet.getString(1)).isEqualTo("1");
                        }
                    }
                }
            }
            assertThat(backendIdSet).hasSize(3);
            try (Connection replacement = dataSource.getConnection();
                 Statement statement = replacement.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT pg_backend_pid()")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(backendIdSet).doesNotContain(resultSet.getInt(1));
                statement.execute("SET search_path = ag_catalog, ontology");
                statement.execute("SELECT * FROM ag_catalog.cypher('ontology101', $$RETURN 1$$) AS (n ag_catalog.agtype)");
            }
        });
    }

    /**
     * 验证连续写入中文节点成功，包含报告中失败的设计原则节点。
     */
    @Test
    void shouldCreateChineseNodesAcrossTransactions() {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            AgeStrategy strategy = context.getBean(AgeStrategy.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            TransactionTemplate transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            strategy.createDatabase("ontology", 102L);
            List<String> nameList = List.of("公路项目", "路基工程", "设计原则");
            for (int index = 0; index < nameList.size(); index++) {
                NodeDTO nodeDTO = NodeDTO.builder().dbPrefix("ontology").databaseId(102L).id(1020L + index)
                        .labels(List.of(nameList.get(index))).properties(Map.of("名称", nameList.get(index))).build();
                transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update("INSERT INTO ontology.node_write_audit VALUES (?)", nodeDTO.getId());
                    strategy.createNode(nodeDTO);
                });
            }
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ontology.node_write_audit WHERE id BETWEEN 1020 AND 1022", Long.class))
                    .isEqualTo(3L);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ontology102._ag_label_vertex", Long.class))
                    .isEqualTo(3L);
        });
    }

    /**
     * 验证图写入失败会回滚关系表和已写图节点，且下一次请求可继续写入。
     */
    @Test
    void shouldRollbackAllWritesAndAllowNextTransaction() {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            AgeStrategy strategy = context.getBean(AgeStrategy.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            TransactionTemplate transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            strategy.createDatabase("ontology", 103L);
            NodeDTO validNode = NodeDTO.builder().dbPrefix("ontology").databaseId(103L).id(1030L)
                    .labels(List.of("设计原则")).properties(Map.of()).build();
            NodeDTO invalidNode = NodeDTO.builder().dbPrefix("ontology").databaseId(103L).id(1031L)
                    .labels(List.of("设计原则")).properties(Map.of("invalid key", "value")).build();

            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("INSERT INTO ontology.node_write_audit VALUES (1030)");
                strategy.createNode(validNode);
                strategy.createNode(invalidNode);
            })).isInstanceOf(DataAccessException.class);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ontology.node_write_audit WHERE id = 1030", Long.class))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ontology103._ag_label_vertex", Long.class))
                    .isZero();

            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update("INSERT INTO ontology.node_write_audit VALUES (1030)");
                strategy.createNode(validNode);
            });
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ontology.node_write_audit WHERE id = 1030", Long.class))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ontology103._ag_label_vertex", Long.class))
                    .isEqualTo(1L);
        });
    }

    /**
     * 构建只加载图存储、连接池和事务的测试上下文。
     *
     * @return 测试上下文执行器
     */
    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        DruidDataSourceAutoConfigure.class, JdbcTemplateAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class))
                .withUserConfiguration(AgeConnectionInitializer.class, IntegrationConfiguration.class)
                .withPropertyValues("graph.database.type=age",
                        "spring.datasource.url=" + System.getenv("AGE_TEST_JDBC_URL"),
                        "spring.datasource.username=postgres", "spring.datasource.password=",
                        "spring.datasource.driver-class-name=org.postgresql.Driver",
                        "spring.datasource.druid.initial-size=2", "spring.datasource.druid.min-idle=0",
                        "spring.datasource.druid.max-active=3", "spring.datasource.druid.phy-max-use-count=1",
                        "spring.datasource.druid.connection-init-sqls[0]=SET application_name = 'ontology-age-regression'");
    }

    /**
     * 2026/10/16 16:30:54.
     *
     * <p>
     * 使用生产存储实现及事务、重试代理。
     * </p>
     *
     * @author <a href="https://github.com/wzh-devin">devin</a>
     * @version 1.0.0
     * @since 1.0.0
     */
    @TestConfiguration(proxyBeanMethods = false)
    @EnableRetry(proxyTargetClass = true)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class IntegrationConfiguration {

        /**
         * 创建真实 AGE 存储策略；本次写入路径不依赖关系数据访问组件。
         *
         * @param jdbcTemplate 数据库访问组件
         * @return AGE 存储策略
         */
        @Bean
        AgeStrategy ageStrategy(final JdbcTemplate jdbcTemplate) {
            return new AgeStrategy(jdbcTemplate, new ObjectMapper(), null, null);
        }
    }
}
