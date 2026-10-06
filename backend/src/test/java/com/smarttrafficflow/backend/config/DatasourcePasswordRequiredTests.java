package com.smarttrafficflow.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Loads the real {@code application.yml} (and {@code application-dev.yml}) and checks that the datasource
 * password has no fallback value: without {@code SPRING_DATASOURCE_PASSWORD} the application must refuse to start.
 * <p>
 * Skipped when the variable is set in the environment running the tests, since it would satisfy the placeholder.
 */
@DisplayName("Datasource password has no default value")
class DatasourcePasswordRequiredTests {

    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() {
        assumeTrue(System.getenv("SPRING_DATASOURCE_PASSWORD") == null,
                "SPRING_DATASOURCE_PASSWORD is set in the environment; the missing-variable case cannot be reproduced");
        runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withUserConfiguration(DatasourcePasswordGuard.class);
    }

    @Test
    @DisplayName("refuses to start in the default profile without SPRING_DATASOURCE_PASSWORD")
    void refusesToStartWithoutPasswordInDefaultProfile() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("SPRING_DATASOURCE_PASSWORD");
        });
    }

    @Test
    @DisplayName("refuses to start in the dev profile without SPRING_DATASOURCE_PASSWORD")
    void refusesToStartWithoutPasswordInDevProfile() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("SPRING_DATASOURCE_PASSWORD");
        });
    }

    @Test
    @DisplayName("refuses to start when SPRING_DATASOURCE_PASSWORD is empty")
    void refusesToStartWithEmptyPassword() {
        runner.withPropertyValues("SPRING_DATASOURCE_PASSWORD=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("SPRING_DATASOURCE_PASSWORD");
        });
    }

    @Test
    @DisplayName("starts and uses the password from SPRING_DATASOURCE_PASSWORD when it is provided")
    void startsWhenPasswordIsProvided() {
        runner.withPropertyValues("SPRING_DATASOURCE_PASSWORD=senha-de-teste").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JdbcConnectionDetails.class).getPassword()).isEqualTo("senha-de-teste");
        });
    }
}
