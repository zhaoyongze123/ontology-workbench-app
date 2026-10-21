package com.devin.ontology.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.druid.filter.logging.Slf4jLogFilter;
import com.alibaba.druid.pool.DruidDataSource;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/** 确认 Druid 执行失败时不会把 SQL 正文或异常内容写入日志。 */
class DruidSqlLogSafetyTest {
    private static final String SECRET_MARKER = "fake-secret-never-log-7c34";

    @Test
    void configuredDruidFailureDoesNotLogSqlOrExceptionAndDruidCategoryIsDisabled() throws Exception {
        assertThat(applicationProperty("spring.datasource.druid.filter.slf4j.statement-log-enabled")).isEqualTo(false);
        assertThat(applicationProperty("spring.datasource.druid.filter.slf4j.statement-log-error-enabled")).isEqualTo(false);
        assertThat(applicationProperty("spring.datasource.druid.filter.stat.log-slow-sql")).isEqualTo(false);

        LoggerContext isolatedContext = new LoggerContext();
        Logger statementLogger;
        try (InputStream xml = new ClassPathResource("logback-spring.xml").getInputStream()) {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(isolatedContext);
            configurator.doConfigure(xml);
            statementLogger = isolatedContext.getLogger("druid.sql.Statement");
        }
        assertThat(statementLogger.getEffectiveLevel()).isEqualTo(Level.OFF);

        // 暂时放开分类级别，单独证明 Druid Filter 的错误日志开关有效。
        statementLogger.setLevel(Level.ERROR);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(isolatedContext);
        appender.start();
        statementLogger.addAppender(appender);

        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:secret-test:unit");
        dataSource.setDriver(new FailingSqlDriver());
        dataSource.setInitialSize(0);
        dataSource.setMinIdle(0);
        dataSource.setMaxActive(1);
        dataSource.setTestOnBorrow(false);
        dataSource.setTestWhileIdle(false);
        dataSource.setFilters("slf4j");
        try {
            dataSource.init();
            Slf4jLogFilter filter = dataSource.getProxyFilters().stream()
                    .filter(Slf4jLogFilter.class::isInstance)
                    .map(Slf4jLogFilter.class::cast)
                    .findFirst().orElseThrow();
            assertThat(filter.isStatementLogErrorEnabled()).isTrue(); // Druid 1.2.21 默认会单独记录错误。
            filter.setStatementLogger(statementLogger);
            filter.setStatementLogEnabled(false);
            filter.setStatementLogErrorEnabled(false);
            filter.setStatementExecutableSqlLogEnable(false);

            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute("SELECT '" + SECRET_MARKER + "'"))
                        .isInstanceOf(SQLException.class).hasMessageContaining(SECRET_MARKER);
            }

            String logged = appender.list.stream().map(event -> event.getFormattedMessage() + " "
                            + (event.getThrowableProxy() == null ? "" : event.getThrowableProxy().getMessage()))
                    .reduce("", (left, right) -> left + right);
            assertThat(logged).doesNotContain(SECRET_MARKER);

            statementLogger.setLevel(Level.OFF);
            appender.list.clear();
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                assertThatThrownBy(() -> statement.execute("SELECT '" + SECRET_MARKER + "'"))
                        .isInstanceOf(SQLException.class);
            }
            assertThat(appender.list).isEmpty();
        } finally {
            dataSource.close();
            statementLogger.detachAppender(appender);
            appender.stop();
            isolatedContext.stop();
        }
    }

    private Boolean applicationProperty(final String key) throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        var source = loader.load("application", new ClassPathResource("application.yaml")).get(0);
        return (Boolean) source.getProperty(key);
    }

    private static Object proxy(final Class<?> type, final InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        return 0D;
    }

    private static final class FailingSqlDriver implements Driver {
        @Override
        public Connection connect(final String url, final Properties info) {
            if (!acceptsURL(url)) return null;
            final boolean[] closed = {false};
            final Connection[] connection = new Connection[1];
            Statement statement = (Statement) proxy(Statement.class, (proxy, method, args) -> {
                if ("execute".equals(method.getName()) || "executeQuery".equals(method.getName())
                        || "executeUpdate".equals(method.getName())) {
                    throw new SQLException("fake database failure contains " + SECRET_MARKER);
                }
                if ("getConnection".equals(method.getName())) return connection[0];
                if ("isClosed".equals(method.getName())) return closed[0];
                if ("close".equals(method.getName())) { closed[0] = true; return null; }
                return defaultValue(method.getReturnType());
            });
            connection[0] = (Connection) proxy(Connection.class, (proxy, method, args) -> {
                if ("createStatement".equals(method.getName())) return statement;
                if ("getAutoCommit".equals(method.getName())) return true;
                if ("isClosed".equals(method.getName())) return closed[0];
                if ("close".equals(method.getName())) { closed[0] = true; return null; }
                if ("isValid".equals(method.getName())) return true;
                return defaultValue(method.getReturnType());
            });
            return connection[0];
        }

        @Override public boolean acceptsURL(final String url) { return url != null && url.startsWith("jdbc:secret-test:"); }
        @Override public DriverPropertyInfo[] getPropertyInfo(final String url, final Properties info) { return new DriverPropertyInfo[0]; }
        @Override public int getMajorVersion() { return 1; }
        @Override public int getMinorVersion() { return 0; }
        @Override public boolean jdbcCompliant() { return false; }
        @Override public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
}
