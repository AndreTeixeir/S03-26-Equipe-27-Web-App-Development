package com.smarttrafficflow.backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Database for tests that need PostgreSQL with PostGIS.
 * <p>
 * Starts a {@code postgis/postgis:15-3.4} container through a service connection,
 * unless {@code SPRING_DATASOURCE_URL} is set. In that case the tests use the
 * external database configured by the {@code SPRING_DATASOURCE_*} variables.
 */
@TestConfiguration(proxyBeanMethods = false)
@Conditional(PostgisTestcontainersConfiguration.NoExternalDatabase.class)
public class PostgisTestcontainersConfiguration {

    private static final DockerImageName POSTGIS_IMAGE =
            DockerImageName.parse("postgis/postgis:15-3.4").asCompatibleSubstituteFor("postgres");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgisContainer() {
        return new PostgreSQLContainer<>(POSTGIS_IMAGE);
    }

    static class NoExternalDatabase implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !StringUtils.hasText(context.getEnvironment().getProperty("SPRING_DATASOURCE_URL"));
        }
    }
}
