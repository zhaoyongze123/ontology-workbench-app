package com.devin.ontology.common.configuration;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026/10/16 16:30:54.
 *
 * <p>
 * 验证 AGE 连接初始化仅在 AGE 模式生效，并保留已有初始化语句。
 * </p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0.0
 * @since 1.0.0
 */
class AgeConnectionInitializerTest {

    /** 数据库已预加载时，普通运行账号不再执行需要额外权限的 LOAD。 */
    @Test
    void shouldSkipLoadWhenServerAlreadyPreloadsAge() {
        try (DruidDataSource dataSource = new DruidDataSource()) {
            dataSource.setConnectionInitSqls(List.of("SET application_name = 'ontology-test'"));
            new ApplicationContextRunner()
                    .withUserConfiguration(AgeConnectionInitializer.class)
                    .withBean("dataSource", DruidDataSource.class, () -> dataSource)
                    .withPropertyValues("graph.database.type=age", "graph.database.age.preloaded=true")
                    .run(context -> assertThat(context.getBean(DruidDataSource.class).getConnectionInitSqls())
                            .containsExactly("SET application_name = 'ontology-test'"));
        }
    }

    /**
     * 验证先加载 AGE，再执行已有初始化语句。
     */
    @Test
    void shouldLoadAgeBeforeExistingConnectionSql() {
        try (DruidDataSource dataSource = new DruidDataSource()) {
            dataSource.setConnectionInitSqls(List.of("SET application_name = 'ontology-test'"));
            new ApplicationContextRunner()
                    .withUserConfiguration(AgeConnectionInitializer.class)
                    .withBean("dataSource", DruidDataSource.class, () -> dataSource)
                    .withPropertyValues("graph.database.type=age")
                    .run(context -> assertThat(context.getBean(DruidDataSource.class).getConnectionInitSqls())
                            .containsExactly("LOAD 'age';", "SET application_name = 'ontology-test'"));
        }
    }

    /**
     * 验证 Neo4j 模式不向关系数据库连接添加 AGE 语句。
     */
    @Test
    void shouldLeaveNeo4jConnectionsUnchanged() {
        try (DruidDataSource dataSource = new DruidDataSource()) {
            new ApplicationContextRunner()
                    .withUserConfiguration(AgeConnectionInitializer.class)
                    .withBean("dataSource", DruidDataSource.class, () -> dataSource)
                    .withPropertyValues("graph.database.type=neo4j")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(AgeConnectionInitializer.class);
                        assertThat(dataSource.getConnectionInitSqls()).isEmpty();
                    });
        }
    }

    /**
     * 验证其他组件保持原样。
     */
    @Test
    void shouldLeaveOtherBeansUnchanged() {
        Object bean = new Object();
        assertThat(new AgeConnectionInitializer().postProcessBeforeInitialization(bean, "otherBean"))
                .isSameAs(bean);
    }
}
