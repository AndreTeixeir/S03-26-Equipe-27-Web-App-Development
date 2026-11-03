package com.smarttrafficflow.backend.domain.insights.service;

import com.smarttrafficflow.backend.api.dto.TrafficInsightResponse;
import com.smarttrafficflow.backend.domain.streets.entity.Street;
import com.smarttrafficflow.backend.domain.trafficrecords.entity.TrafficRecord;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsightService tests")
class InsightServiceTests {

    @Mock
    private TrafficRecordService trafficRecordService;

    @InjectMocks
    private InsightService insightService;

    @Test
    @DisplayName("returns the fallback insight when there is no data")
    void returnsFallbackWhenThereIsNoData() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of());

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).containsExactly("Sem dados suficientes para gerar insights.");
    }

    @Test
    @DisplayName("builds peak hour and peak road type insights from all records")
    void buildsInsightsFromAllRecords() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of(
                recordAt(8, "ARTERIAL", 100),
                recordAt(8, "LOCAL", 80),
                recordAt(17, "HIGHWAY", 50)
        ));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).hasSize(2);
        assertThat(response.insights().get(0)).contains("8h");
        assertThat(response.insights().get(1)).contains("ARTERIAL");
    }

    @Test
    @DisplayName("uses selected record ids when a filter is provided")
    void buildsInsightsFromSelectedRecords() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(trafficRecordService.findEntitiesByIds(recordIds)).thenReturn(List.of(
                recordAt(10, "HIGHWAY", 300)
        ));

        TrafficInsightResponse response = insightService.generateInsights(recordIds);

        assertThat(response.insights()).hasSize(2);
        assertThat(response.insights().get(0)).contains("10h");
        assertThat(response.insights().get(1)).contains("HIGHWAY");
        verify(trafficRecordService).findEntitiesByIds(recordIds);
    }

    @Test
    @DisplayName("uses UNKNOWN when the road type is blank or null")
    void usesUnknownForMissingRoadType() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of(
                recordAt(8, null, 120),
                recordAt(9, "   ", 80)
        ));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights().get(1)).contains("UNKNOWN");
    }

    @Test
    @DisplayName("returns the same two insight messages when only one record exists")
    void returnsTwoInsightsForASingleRecord() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of(
                recordAt(10, "HIGHWAY", 300)
        ));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).hasSize(2);
        assertThat(response.insights().get(0)).contains("10h");
        assertThat(response.insights().get(1)).contains("HIGHWAY");
    }

    @Test
    @DisplayName("still returns insights when multiple groups tie on total volume")
    void returnsInsightsWhenThereAreTiedVolumes() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of(
                recordAt(8, "ARTERIAL", 100),
                recordAt(9, "HIGHWAY", 100)
        ));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights()).hasSize(2);
        assertThat(response.insights().get(0)).contains("Horario com maior volume agregado");
        assertThat(response.insights().get(1)).contains("Tipo de via com maior volume agregado");
    }

    @Test
    @DisplayName("finds the peak hour when the summed volume exceeds Integer.MAX_VALUE")
    void findsPeakWhenSumsExceedIntegerRange() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of(
                recordAt(8, "ARTERIAL", Integer.MAX_VALUE),
                recordAt(8, "ARTERIAL", Integer.MAX_VALUE),
                recordAt(9, "LOCAL", 1000)
        ));

        TrafficInsightResponse response = insightService.generateInsights(null);

        assertThat(response.insights().get(0)).contains("8h");
        assertThat(response.insights().get(1)).contains("ARTERIAL");
    }

    private TrafficRecord recordAt(int hour, String roadType, int volume) {
        TrafficRecord record = new TrafficRecord();
        record.setId(UUID.randomUUID());
        record.setTimestamp(OffsetDateTime.of(2024, 6, 17, hour, 0, 0, 0, ZoneOffset.UTC));
        record.setRoadType(roadType);
        record.setVehicleVolume(volume);
        record.setStreet(street());
        return record;
    }

    private Street street() {
        Street street = new Street();
        street.setId(UUID.randomUUID());
        street.setOsmWayId(101L);
        street.setName("Avenida Central");
        return street;
    }
}
