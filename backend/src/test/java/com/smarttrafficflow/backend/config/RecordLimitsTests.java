package com.smarttrafficflow.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("RecordLimits tests")
class RecordLimitsTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RecordLimits.class)
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    @DisplayName("accepts values from 1 to 50000")
    void acceptsValuesWithinTheRange() {
        assertThat(new RecordLimits("1", "1").exportMaxRecords()).isEqualTo(1);
        assertThat(new RecordLimits(" 300 ", "40").exportMaxRecords()).isEqualTo(300);
        assertThat(new RecordLimits("50000", "50000").exportMaxRecords()).isEqualTo(50_000);
        assertThat(new RecordLimits("10", " 250 ").mapMaxRecords()).isEqualTo(250);
    }

    @Test
    @DisplayName("rejects zero, negative, too large, empty and non-numeric values, naming the variable")
    void rejectsInvalidValues() {
        for (String invalid : new String[]{"0", "-5", "50001", "", "abc", "10.5"}) {
            assertThatThrownBy(() -> new RecordLimits(invalid, "250"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_EXPORT_MAX_RECORDS")
                    .hasMessageContaining("50000");
            assertThatThrownBy(() -> new RecordLimits("10000", invalid))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_MAP_MAX_RECORDS")
                    .hasMessageContaining("50000");
        }
    }

    @Test
    @DisplayName("uses the application.yml defaults: 10000 records per export and 250 on the map")
    void usesTheDefaults() {
        assumeTrue(System.getenv("APP_EXPORT_MAX_RECORDS") == null && System.getenv("APP_MAP_MAX_RECORDS") == null,
                "a limit variable is set in the environment running the tests");

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RecordLimits.class).exportMaxRecords()).isEqualTo(10_000);
            assertThat(context.getBean(RecordLimits.class).mapMaxRecords()).isEqualTo(250);
        });
    }

    @Test
    @DisplayName("reads the ceilings from APP_EXPORT_MAX_RECORDS and APP_MAP_MAX_RECORDS")
    void readsTheVariables() {
        runner.withPropertyValues("APP_EXPORT_MAX_RECORDS=300", "APP_MAP_MAX_RECORDS=40").run(context -> {
            assertThat(context.getBean(RecordLimits.class).exportMaxRecords()).isEqualTo(300);
            assertThat(context.getBean(RecordLimits.class).mapMaxRecords()).isEqualTo(40);
        });
    }

    @Test
    @DisplayName("prevents the application context from starting with an invalid ceiling")
    void invalidValueStopsTheStartup() {
        runner.withPropertyValues("APP_EXPORT_MAX_RECORDS=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_EXPORT_MAX_RECORDS");
        });
        runner.withPropertyValues("APP_MAP_MAX_RECORDS=abc").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_MAP_MAX_RECORDS");
        });
    }
}
