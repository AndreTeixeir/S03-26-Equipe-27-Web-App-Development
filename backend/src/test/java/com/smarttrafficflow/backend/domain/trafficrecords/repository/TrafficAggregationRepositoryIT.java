package com.smarttrafficflow.backend.domain.trafficrecords.repository;

import com.smarttrafficflow.backend.domain.analytics.service.StatsGroupBy;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import com.smarttrafficflow.backend.support.TrafficTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PostgisTestcontainersConfiguration.class, TrafficAggregationRepository.class})
@DisplayName("TrafficAggregationRepository integration tests")
class TrafficAggregationRepositoryIT {

    @Autowired
    private TrafficAggregationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TrafficTestData data;
    private UUID streetId;

    @BeforeEach
    void setUp() {
        data = new TrafficTestData(jdbcTemplate);
        data.clear();
        streetId = data.insertStreet(101L, "Avenida Central");
    }

    @Test
    @DisplayName("sums the volume per hour, ordered by hour")
    void sumsVolumePerHour() {
        data.insertRecord(streetId, "2024-06-17T17:00:00Z", "LOCAL", 200);
        data.insertRecord(streetId, "2024-06-17T08:00:00Z", "ARTERIAL", 100);
        data.insertRecord(streetId, "2024-06-17T08:30:00Z", "HIGHWAY", 50);

        Map<String, Long> totals = repository.sumVolumeBy(StatsGroupBy.HOUR, "UTC", null);

        assertThat(totals).containsExactly(Map.entry("8", 150L), Map.entry("17", 200L));
    }

    @Test
    @DisplayName("observes the hour and the weekday in the requested time zone")
    void observesHourAndWeekdayInTheRequestedZone() {
        // 2026-11-03T01:30:00Z is Tuesday 01:30 in UTC and Monday 22:30 in America/Sao_Paulo (UTC-3).
        data.insertRecord(streetId, "2026-11-03T01:30:00Z", "LOCAL", 100);

        assertThat(repository.sumVolumeBy(StatsGroupBy.HOUR, "UTC", null)).containsExactly(Map.entry("1", 100L));
        assertThat(repository.sumVolumeBy(StatsGroupBy.WEEKDAY, "UTC", null)).containsExactly(Map.entry("2", 100L));
        assertThat(repository.sumVolumeBy(StatsGroupBy.HOUR, "America/Sao_Paulo", null))
                .containsExactly(Map.entry("22", 100L));
        assertThat(repository.sumVolumeBy(StatsGroupBy.WEEKDAY, "America/Sao_Paulo", null))
                .containsExactly(Map.entry("1", 100L));
    }

    @Test
    @DisplayName("sums the volume per ISO weekday (1 = Monday)")
    void sumsVolumePerWeekday() {
        data.insertRecord(streetId, "2024-06-14T12:00:00Z", "LOCAL", 200); // Friday
        data.insertRecord(streetId, "2024-06-10T12:00:00Z", "LOCAL", 100); // Monday
        data.insertRecord(streetId, "2024-06-10T15:00:00Z", "LOCAL", 80);  // Monday

        Map<String, Long> totals = repository.sumVolumeBy(StatsGroupBy.WEEKDAY, "UTC", null);

        assertThat(totals).containsExactly(Map.entry("1", 180L), Map.entry("5", 200L));
    }

    @Test
    @DisplayName("uses UNKNOWN for blank road types")
    void usesUnknownForMissingRoadTypes() {
        data.insertRecord(streetId, "2024-06-17T08:00:00Z", "   ", 50);
        data.insertRecord(streetId, "2024-06-17T09:00:00Z", "", 30);
        data.insertRecord(streetId, "2024-06-17T10:00:00Z", "LOCAL", 20);

        Map<String, Long> totals = repository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, "UTC", null);

        assertThat(totals).containsOnly(Map.entry("UNKNOWN", 80L), Map.entry("LOCAL", 20L));
    }

    @Test
    @DisplayName("sums volumes beyond Integer.MAX_VALUE as long")
    void sumsVolumesBeyondIntegerRange() {
        data.insertRecord(streetId, "2024-06-17T08:00:00Z", "LOCAL", Integer.MAX_VALUE);
        data.insertRecord(streetId, "2024-06-17T08:10:00Z", "LOCAL", Integer.MAX_VALUE);

        Map<String, Long> totals = repository.sumVolumeBy(StatsGroupBy.HOUR, "UTC", null);

        assertThat(totals).containsExactly(Map.entry("8", 4_294_967_294L));
    }

    @Test
    @DisplayName("restricts the sums to the selected records")
    void restrictsToSelectedRecords() {
        UUID first = data.insertRecord(streetId, "2024-06-17T08:00:00Z", "LOCAL", 100);
        UUID second = data.insertRecord(streetId, "2024-06-17T17:00:00Z", "HIGHWAY", 250);
        data.insertRecord(streetId, "2024-06-17T17:30:00Z", "HIGHWAY", 999);

        assertThat(repository.sumVolumeBy(StatsGroupBy.HOUR, "UTC", List.of(first, second)))
                .containsExactly(Map.entry("8", 100L), Map.entry("17", 250L));
        assertThat(repository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, "UTC", List.of(second)))
                .containsExactly(Map.entry("HIGHWAY", 250L));
    }

    @Test
    @DisplayName("treats an empty id list as no filter")
    void treatsAnEmptyIdListAsNoFilter() {
        data.insertRecord(streetId, "2024-06-17T08:00:00Z", "LOCAL", 100);

        assertThat(repository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, "UTC", List.of()))
                .containsExactly(Map.entry("LOCAL", 100L));
    }

    @Test
    @DisplayName("returns no buckets for an empty table")
    void returnsNoBucketsForAnEmptyTable() {
        data.clear();

        assertThat(repository.sumVolumeBy(StatsGroupBy.HOUR, "UTC", null)).isEmpty();
        assertThat(repository.sumVolumeBy(StatsGroupBy.WEEKDAY, "UTC", null)).isEmpty();
        assertThat(repository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, "UTC", null)).isEmpty();
    }
}
