package com.devin.ontology.common.configuration;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 2025/11/23 20:41.
 *
 * <p>
 * 图数据库配置
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Configuration
// 初始化身份的维护账号没有图谱权限；bootstrap 不承担日常图数据库连接和校验职责。
@Profile("!bootstrap")
public class GraphDatabaseConfiguration {

    private final JdbcTemplate jdbcTemplate;
    private final String databaseType;

    public GraphDatabaseConfiguration(
            final JdbcTemplate jdbcTemplate,
            @Value("${graph.database.type:age}") final String databaseType
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseType = databaseType;
    }

    /**
     * 初始化Neo4j驱动.
     *
     * @param uri      Neo4j URI
     * @param username Neo4j 用户名
     * @param password Neo4j 密码
     * @return Driver
     */
    @Bean
    @ConditionalOnProperty(name = "graph.database.type", havingValue = "neo4j")
    public Driver neo4jDriver(
            @Value("${spring.neo4j.uri}") final String uri,
            @Value("${spring.neo4j.authentication.username}") final String username,
            @Value("${spring.neo4j.authentication.password}") final String password
    ) {
        log.info("初始化Neo4j驱动");
        Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
        log.info("<====== Neo4j驱动初始化完成");
        return driver;
    }

    /**
     * 初始化Age插件.
     */
    @PostConstruct
    public void initAge() {
        if (!"age".equals(databaseType)) {
            return;
        }
        // 安装扩展属于数据库维护职责。日常服务只核验依赖，失败即终止启动。
        Integer installed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'age'", Integer.class);
        if (installed == null || installed != 1) {
            throw new IllegalStateException("AGE 扩展未安装，请由数据库维护账号完成安装");
        }
        jdbcTemplate.execute("SELECT NULL::ag_catalog.agtype");
        log.info("AGE 扩展可用性核验完成");
    }
}
