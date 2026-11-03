package com.smarttrafficflow.backend.domain.analytics.service;

import com.smarttrafficflow.backend.api.dto.TrafficStatsResponse;
import com.smarttrafficflow.backend.config.AppTimeZone;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficAggregationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService tests")
class AnalyticsServiceTests {

    private static final String ZONE = "America/Sao_Paulo";

    @Mock
    private TrafficAggregationRepository aggregationRepository;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(aggregationRepository, new AppTimeZone(ZONE));
    }

    @Test
    @DisplayName("aggregates hourly volume from all records when no filter is provided")
    void aggregatesHourStatsFromAllRecords() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, ZONE, List.of()))
                .thenReturn(totals("8", 150L, "17", 200L));

        TrafficStatsResponse response = analyticsService.getStats("hour", List.of());

        assertThat(response.labels().get(8)).isEqualTo("08");
        assertThat(response.labels().get(17)).isEqualTo("17");
        assertThat(response.values().get(8)).isEqualTo(150L);
        assertThat(response.values().get(17)).isEqualTo(200L);
    }

    @Test
    @DisplayName("aggregates weekday stats from selected records")
    void aggregatesWeekdayStatsFromSelectedRecords() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.WEEKDAY, ZONE, recordIds))
                .thenReturn(totals("1", 180L, "5", 200L));

        TrafficStatsResponse response = analyticsService.getStats("weekday", recordIds);

        assertThat(response.labels().get(0)).isEqualTo("MONDAY");
        assertThat(response.labels().get(4)).isEqualTo("FRIDAY");
        assertThat(response.values().get(0)).isEqualTo(180L);
        assertThat(response.values().get(4)).isEqualTo(200L);
        verify(aggregationRepository).sumVolumeBy(StatsGroupBy.WEEKDAY, ZONE, recordIds);
    }

    @Test
    @DisplayName("aggregates selected records by road type")
    void aggregatesRoadTypeStatsFromSelectedRecords() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, ZONE, recordIds))
                .thenReturn(totals("ARTERIAL", 180L, "HIGHWAY", 200L));

        TrafficStatsResponse response = analyticsService.getStats("roadType", recordIds);

        assertThat(response.labels()).containsExactly("ARTERIAL", "HIGHWAY");
        assertThat(response.values()).containsExactly(180L, 200L);
        verify(aggregationRepository).sumVolumeBy(StatsGroupBy.ROAD_TYPE, ZONE, recordIds);
    }

    @Test
    @DisplayName("always returns the 24 hours, from 00 to 23, with zero for hours without records")
    void returnsAll24HoursWithZeros() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, ZONE, null))
                .thenReturn(totals("17", 200L, "8", 150L));

        TrafficStatsResponse response = analyticsService.getStats("hour", null);

        assertThat(response.labels()).hasSize(24).startsWith("00", "01", "02").endsWith("22", "23");
        assertThat(response.values()).hasSize(24);
        assertThat(response.values().get(8)).isEqualTo(150L);
        assertThat(response.values().get(17)).isEqualTo(200L);
        assertThat(response.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(350L);
    }

    @Test
    @DisplayName("returns the 24 hours with zeros when there are no records")
    void returnsAll24HoursWhenThereAreNoRecords() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, ZONE, null)).thenReturn(Map.of());

        TrafficStatsResponse response = analyticsService.getStats("hour", null);

        assertThat(response.labels()).hasSize(24);
        assertThat(response.values()).hasSize(24).containsOnly(0L);
    }

    @Test
    @DisplayName("always returns the 7 weekdays, from MONDAY to SUNDAY, with zero for days without records")
    void returnsAll7WeekdaysWithZeros() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.WEEKDAY, ZONE, null))
                .thenReturn(totals("7", 30L, "1", 180L));

        TrafficStatsResponse response = analyticsService.getStats("weekday", null);

        assertThat(response.labels()).containsExactly(
                "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        assertThat(response.values()).containsExactly(180L, 0L, 0L, 0L, 0L, 0L, 30L);
    }

    @Test
    @DisplayName("orders road types as LOCAL, ARTERIAL, HIGHWAY, other types, UNKNOWN last, only those present")
    void ordersRoadTypesInAFixedOrder() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, ZONE, null))
                .thenReturn(totals("UNKNOWN", 2L, "HIGHWAY", 1L, "RURAL", 3L, "LOCAL", 4L, "BETA", 6L));

        TrafficStatsResponse response = analyticsService.getStats("roadType", null);

        assertThat(response.labels()).containsExactly("LOCAL", "HIGHWAY", "BETA", "RURAL", "UNKNOWN");
        assertThat(response.values()).containsExactly(4L, 1L, 6L, 3L, 2L);
    }

    @Test
    @DisplayName("does not invent road types that have no records")
    void omitsRoadTypesWithoutRecords() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, ZONE, null)).thenReturn(totals("HIGHWAY", 9L));

        assertThat(analyticsService.getStats("roadType", null).labels()).containsExactly("HIGHWAY");
    }

    @Test
    @DisplayName("observes the statistics in the configured time zone")
    void usesTheConfiguredTimeZone() {
        AnalyticsService inUtc = new AnalyticsService(aggregationRepository, new AppTimeZone("UTC"));
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, "UTC", null)).thenReturn(Map.of());

        inUtc.getStats("hour", null);

        verify(aggregationRepository).sumVolumeBy(StatsGroupBy.HOUR, "UTC", null);
    }

    @Test
    @DisplayName("returns empty labels and values for road types when there are no records")
    void returnsEmptyStatsWhenThereAreNoRecords() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, ZONE, null)).thenReturn(Map.of());

        TrafficStatsResponse response = analyticsService.getStats("roadType", null);

        assertThat(response.labels()).isEmpty();
        assertThat(response.values()).isEmpty();
    }

    @Test
    @DisplayName("keeps totals beyond Integer.MAX_VALUE")
    void sumsVolumesBeyondIntegerRange() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, ZONE, null))
                .thenReturn(totals("8", 2L * Integer.MAX_VALUE));

        TrafficStatsResponse response = analyticsService.getStats("hour", null);

        assertThat(response.labels().get(8)).isEqualTo("08");
        assertThat(response.values().get(8)).isEqualTo(2L * Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("rejects unsupported group by values")
    void rejectsUnsupportedGroupByValues() {
        assertThatThrownBy(() -> analyticsService.getStats("invalid", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupBy invalido");
    }

    @Test
    @DisplayName("rejects an unsupported group by even when there are no records")
    void rejectsUnsupportedGroupByWithEmptyDatabase() {
        lenient().when(aggregationRepository.sumVolumeBy(any(), anyString(), isNull())).thenReturn(Map.of());

        assertThatThrownBy(() -> analyticsService.getStats("xyz", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupBy invalido: xyz");
        assertThatThrownBy(() -> analyticsService.getStats("xyz", List.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(aggregationRepository);
    }

    @Test
    @DisplayName("rejects a missing group by without reading any record")
    void rejectsMissingGroupBy() {
        assertThatThrownBy(() -> analyticsService.getStats(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupBy invalido");

        verifyNoInteractions(aggregationRepository);
    }

    private static Map<String, Long> totals(Object... keysAndValues) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            totals.put((String) keysAndValues[i], (Long) keysAndValues[i + 1]);
        }
        return totals;
    }
}
