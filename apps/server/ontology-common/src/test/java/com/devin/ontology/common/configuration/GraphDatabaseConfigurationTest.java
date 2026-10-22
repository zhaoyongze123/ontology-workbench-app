package com.devin.ontology.common.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class GraphDatabaseConfigurationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JdbcTemplate.class, () -> jdbc)
            .withUserConfiguration(GraphDatabaseConfiguration.class)
            .withPropertyValues("graph.database.type=age");

    @Test
    void bootstrapDoesNotLoadGraphDatabaseOrRequireGraphPermissions() {
        runner.withPropertyValues("spring.profiles.active=bootstrap,multiuser").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(GraphDatabaseConfiguration.class);
            verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class));
            verify(jdbc, never()).execute(anyString());
        });
    }

    @Test
    void runtimeStillVerifiesInstalledAgeAndTypeAccess() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'age'", Integer.class))
                .thenReturn(1);
        runner.withPropertyValues("spring.profiles.active=multiuser").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(GraphDatabaseConfiguration.class);
            verify(jdbc).execute("SELECT NULL::ag_catalog.agtype");
        });
    }

    @Test
    void runtimeFailsWhenAgeIsNotInstalled() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM pg_extension WHERE extname = 'age'", Integer.class))
                .thenReturn(0);
        runner.run(context -> assertThat(context).hasFailed());
    }
}
