package com.smarttrafficflow.backend.domain.insights.service;

import com.smarttrafficflow.backend.api.dto.TrafficInsightResponse;
import com.smarttrafficflow.backend.config.AppTimeZone;
import com.smarttrafficflow.backend.domain.analytics.service.StatsGroupBy;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsightService tests")
class InsightServiceTests {

    private static final String ZONE = "America/Sao_Paulo";

    @Mock
    private TrafficAggregationRepository aggregationRepository;

    private InsightService insightService;

    @BeforeEach
    void setUp() {
        insightService = new InsightService(aggregationRepository, new AppTimeZone(ZONE));
    }

    @Test
    @DisplayName("returns the fallback insight when there is no data")
    void returnsFallbackWhenThereIsNoData() {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, ZONE, null)).thenReturn(Map.of());

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).containsExactly("Sem dados suficientes para gerar insights.");
        verify(aggregationRepository, never()).sumVolumeBy(StatsGroupBy.ROAD_TYPE, ZONE, null);
    }

    @Test
    @DisplayName("builds peak hour and peak road type insights from all records")
    void buildsInsightsFromAllRecords() {
        stub(null, totals("8", 180L, "17", 50L), totals("ARTERIAL", 100L, "LOCAL", 80L, "HIGHWAY", 50L));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).containsExactly(
                "Horário de pico: 8h.",
                "Tipo de via com maior volume: Via arterial.");
    }

    @Test
    @DisplayName("uses selected record ids when a filter is provided")
    void buildsInsightsFromSelectedRecords() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        stub(recordIds, totals("10", 300L), totals("HIGHWAY", 300L));

        TrafficInsightResponse response = insightService.generateInsights(recordIds);

        assertThat(response.insights()).containsExactly(
                "Horário de pico: 10h.",
                "Tipo de via com maior volume: Via expressa.");
        verify(aggregationRepository).sumVolumeBy(StatsGroupBy.HOUR, ZONE, recordIds);
        verify(aggregationRepository).sumVolumeBy(StatsGroupBy.ROAD_TYPE, ZONE, recordIds);
    }

    @Test
    @DisplayName("names blank road types as 'Não informado'")
    void usesUnknownForMissingRoadType() {
        stub(null, totals("8", 200L), totals("UNKNOWN", 200L));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights().get(1)).isEqualTo("Tipo de via com maior volume: Não informado.");
    }

    @Test
    @DisplayName("shows free-text road types as they were registered")
    void showsFreeTextRoadTypesAsRegistered() {
        stub(null, totals("8", 200L), totals("RURAL", 200L));

        assertThat(insightService.generateInsights(null).insights().get(1))
                .isEqualTo("Tipo de via com maior volume: RURAL.");
    }

    @Test
    @DisplayName("returns the same two insight messages when only one record exists")
    void returnsTwoInsightsForASingleRecord() {
        stub(null, totals("10", 300L), totals("HIGHWAY", 300L));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).hasSize(2);
        assertThat(response.insights().get(0)).isEqualTo("Horário de pico: 10h.");
        assertThat(response.insights().get(1)).isEqualTo("Tipo de via com maior volume: Via expressa.");
    }

    @Test
    @DisplayName("keeps the earliest hour and the first road type in the fixed order when volumes tie")
    void returnsInsightsWhenThereAreTiedVolumes() {
        // The map order is deliberately the opposite of the expected winners.
        stub(null, totals("17", 100L, "9", 100L), totals("HIGHWAY", 100L, "UNKNOWN", 100L, "LOCAL", 100L));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).containsExactly(
                "Horário de pico: 9h.",
                "Tipo de via com maior volume: Via local.");
    }

    @Test
    @DisplayName("finds the peak when the summed volume exceeds Integer.MAX_VALUE")
    void findsPeakWhenSumsExceedIntegerRange() {
        stub(null, totals("8", 2L * Integer.MAX_VALUE, "9", 1000L), totals("ARTERIAL", 2L * Integer.MAX_VALUE));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights().get(0)).isEqualTo("Horário de pico: 8h.");
        assertThat(response.insights().get(1)).isEqualTo("Tipo de via com maior volume: Via arterial.");
    }

    @Test
    @DisplayName("falls back to the no-data insight if the two queries disagree (hours found, no road types)")
    void fallsBackWhenTheRoadTypesAreMissing() {
        stub(null, totals("8", 100L), totals());

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).containsExactly("Sem dados suficientes para gerar insights.");
    }

    @Test
    @DisplayName("observes the hours in the configured time zone")
    void usesTheConfiguredTimeZone() {
        InsightService inUtc = new InsightService(aggregationRepository, new AppTimeZone("UTC"));
        when(aggregationRepository.sumVolumeBy(any(), anyString(), any())).thenReturn(Map.of());

        inUtc.generateInsights(null);

        verify(aggregationRepository).sumVolumeBy(StatsGroupBy.HOUR, "UTC", null);
    }

    private void stub(List<UUID> recordIds, Map<String, Long> byHour, Map<String, Long> byRoadType) {
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, ZONE, recordIds)).thenReturn(byHour);
        when(aggregationRepository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, ZONE, recordIds)).thenReturn(byRoadType);
    }

    private static Map<String, Long> totals(Object... keysAndValues) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            totals.put((String) keysAndValues[i], (Long) keysAndValues[i + 1]);
        }
        return totals;
    }
}
