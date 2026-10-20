package com.devin.ontology.common.configuration;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/** AGE 与业务表共用同一数据源，显式选择 JDBC 事务，避免 Neo4j 依赖参与管理器选择。 */
@Configuration
@ConditionalOnProperty(name = "graph.database.type", havingValue = "age")
public class RelationalTransactionConfiguration {
    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(final DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
