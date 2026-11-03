package com.smarttrafficflow.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("AppTimeZone tests")
class AppTimeZoneTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AppTimeZone.class);

    @Test
    @DisplayName("accepts identifiers of the time zone database")
    void acceptsTimeZoneDatabaseIdentifiers() {
        assertThat(new AppTimeZone("America/Sao_Paulo").id()).isEqualTo("America/Sao_Paulo");
        assertThat(new AppTimeZone("UTC").id()).isEqualTo("UTC");
    }

    @Test
    @DisplayName("rejects unknown, empty and offset-style zones with a message that names the variable")
    void rejectsInvalidZones() {
        for (String invalid : new String[]{"Foo/Bar", "", "  ", "UTC+3", "sao paulo"}) {
            assertThatThrownBy(() -> new AppTimeZone(invalid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_TIMEZONE")
                    .hasMessageContaining("America/Sao_Paulo");
        }
    }

    @Test
    @DisplayName("prevents the application context from starting with an invalid APP_TIMEZONE")
    void invalidZoneStopsTheStartup() {
        runner.withPropertyValues("app.timezone=Foo/Bar").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_TIMEZONE")
                    .hasMessageContaining("Foo/Bar");
        });
    }

    @Test
    @DisplayName("defaults to America/Sao_Paulo when APP_TIMEZONE is not set")
    void defaultsToSaoPaulo() {
        assumeTrue(System.getenv("APP_TIMEZONE") == null, "APP_TIMEZONE is set in the environment running the tests");

        runner.withInitializer(new ConfigDataApplicationContextInitializer()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AppTimeZone.class).id()).isEqualTo("America/Sao_Paulo");
        });
    }

    @Test
    @DisplayName("reads the zone from APP_TIMEZONE")
    void readsTheZoneFromTheVariable() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("APP_TIMEZONE=UTC")
                .run(context -> assertThat(context.getBean(AppTimeZone.class).id()).isEqualTo("UTC"));
    }
}
