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

        assertThat(response.labels()).containsExactly("08", "17");
        assertThat(response.values()).containsExactly(150L, 200L);
    }

    @Test
    @DisplayName("aggregates weekday stats from selected records")
    void aggregatesWeekdayStatsFromSelectedRecords() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.WEEKDAY, ZONE, recordIds))
                .thenReturn(totals("1", 180L, "5", 200L));

        TrafficStatsResponse response = analyticsService.getStats("weekday", recordIds);

        assertThat(response.labels()).containsExactly("MONDAY", "FRIDAY");
        assertThat(response.values()).containsExactly(180L, 200L);
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
    @DisplayName("observes the statistics in the configured time zone")
    void usesTheConfiguredTimeZone() {
        AnalyticsService inUtc = new AnalyticsService(aggregationRepository, new AppTimeZone("UTC"));
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, "UTC", null)).thenReturn(Map.of());

        inUtc.getStats("hour", null);

        verify(aggregationRepository).sumVolumeBy(StatsGroupBy.HOUR, "UTC", null);
    }

    @Test
    @DisplayName("returns empty labels and values when there are no records")
    void returnsEmptyStatsWhenThereAreNoRecords() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, ZONE, null)).thenReturn(Map.of());

        TrafficStatsResponse response = analyticsService.getStats("hour", null);

        assertThat(response.labels()).isEmpty();
        assertThat(response.values()).isEmpty();
    }

    @Test
    @DisplayName("keeps totals beyond Integer.MAX_VALUE")
    void sumsVolumesBeyondIntegerRange() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, ZONE, null))
                .thenReturn(totals("8", 2L * Integer.MAX_VALUE));

        TrafficStatsResponse response = analyticsService.getStats("hour", null);

        assertThat(response.labels()).containsExactly("08");
        assertThat(response.values()).containsExactly(2L * Integer.MAX_VALUE);
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
