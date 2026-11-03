package com.smarttrafficflow.backend.domain.analytics.service;

import com.smarttrafficflow.backend.api.dto.TrafficStatsResponse;
import com.smarttrafficflow.backend.domain.streets.entity.Street;
import com.smarttrafficflow.backend.domain.trafficrecords.entity.TrafficRecord;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService tests")
class AnalyticsServiceTests {

    @Mock
    private TrafficRecordService trafficRecordService;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("aggregates hourly volume from all records when no filter is provided")
    void aggregatesHourStatsFromAllRecords() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of(
                recordAt(8, "ARTERIAL", 100),
                recordAt(8, "HIGHWAY", 50),
                recordAt(17, "LOCAL", 200)
        ));

        TrafficStatsResponse response = analyticsService.getStats("hour", List.of());

        assertThat(response.labels()).containsExactly("08", "17");
        assertThat(response.values()).containsExactly(150L, 200L);
    }

    @Test
    @DisplayName("aggregates weekday stats from selected records")
    void aggregatesWeekdayStatsFromSelectedRecords() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(trafficRecordService.findEntitiesByIds(recordIds)).thenReturn(List.of(
                recordOnDay(DayOfWeek.MONDAY, 100),
                recordOnDay(DayOfWeek.MONDAY, 80),
                recordOnDay(DayOfWeek.FRIDAY, 200)
        ));

        TrafficStatsResponse response = analyticsService.getStats("weekday", recordIds);

        assertThat(response.labels()).containsExactly("MONDAY", "FRIDAY");
        assertThat(response.values()).containsExactly(180L, 200L);
        verify(trafficRecordService).findEntitiesByIds(recordIds);
    }

    @Test
    @DisplayName("uses UNKNOWN for blank or null road types")
    void usesUnknownForMissingRoadTypes() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of(
                recordAt(8, null, 50),
                recordAt(9, "   ", 30)
        ));

        TrafficStatsResponse response = analyticsService.getStats("roadType", null);

        assertThat(response.labels()).containsExactly("UNKNOWN");
        assertThat(response.values()).containsExactly(80L);
    }

    @Test
    @DisplayName("aggregates selected records by road type")
    void aggregatesRoadTypeStatsFromSelectedRecords() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(trafficRecordService.findEntitiesByIds(recordIds)).thenReturn(List.of(
                recordAt(8, "ARTERIAL", 100),
                recordAt(9, "ARTERIAL", 80),
                recordAt(10, "HIGHWAY", 200)
        ));

        TrafficStatsResponse response = analyticsService.getStats("roadType", recordIds);

        assertThat(response.labels()).containsExactly("ARTERIAL", "HIGHWAY");
        assertThat(response.values()).containsExactly(180L, 200L);
        verify(trafficRecordService).findEntitiesByIds(recordIds);
    }

    @Test
    @DisplayName("returns empty labels and values when there are no records")
    void returnsEmptyStatsWhenThereAreNoRecords() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of());

        TrafficStatsResponse response = analyticsService.getStats("hour", null);

        assertThat(response.labels()).isEmpty();
        assertThat(response.values()).isEmpty();
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
        lenient().when(trafficRecordService.findAllEntities()).thenReturn(List.of());
        lenient().when(trafficRecordService.findEntitiesByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> analyticsService.getStats("xyz", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupBy invalido: xyz");
        assertThatThrownBy(() -> analyticsService.getStats("xyz", List.of(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(trafficRecordService);
    }

    @Test
    @DisplayName("rejects a missing group by without reading any record")
    void rejectsMissingGroupBy() {
        assertThatThrownBy(() -> analyticsService.getStats(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("groupBy invalido");

        verifyNoInteractions(trafficRecordService);
    }

    @Test
    @DisplayName("sums volumes beyond Integer.MAX_VALUE without overflowing")
    void sumsVolumesBeyondIntegerRange() {
        when(trafficRecordService.findAllEntities()).thenReturn(List.of(
                recordAt(8, "ARTERIAL", Integer.MAX_VALUE),
                recordAt(8, "ARTERIAL", Integer.MAX_VALUE)
        ));

        TrafficStatsResponse response = analyticsService.getStats("hour", null);

        assertThat(response.labels()).containsExactly("08");
        assertThat(response.values()).containsExactly(2L * Integer.MAX_VALUE);
    }

    private TrafficRecord recordAt(int hour, String roadType, int volume) {
        TrafficRecord record = new TrafficRecord();
        record.setId(UUID.randomUUID());
        record.setTimestamp(OffsetDateTime.of(2024, 6, 17, hour, 0, 0, 0, ZoneOffset.UTC));
        record.setRoadType(roadType);
        record.setVehicleVolume(volume);
        record.setStreet(street(100L + hour, "Rua " + hour));
        return record;
    }

    private TrafficRecord recordOnDay(DayOfWeek dayOfWeek, int volume) {
        OffsetDateTime monday = OffsetDateTime.of(2024, 6, 10, 8, 0, 0, 0, ZoneOffset.UTC);
        TrafficRecord record = new TrafficRecord();
        record.setId(UUID.randomUUID());
        record.setTimestamp(monday.plusDays(dayOfWeek.getValue() - DayOfWeek.MONDAY.getValue()));
        record.setRoadType("ARTERIAL");
        record.setVehicleVolume(volume);
        record.setStreet(street(200L + dayOfWeek.getValue(), dayOfWeek.name()));
        return record;
    }

    private Street street(long osmWayId, String name) {
        Street street = new Street();
        street.setId(UUID.randomUUID());
        street.setOsmWayId(osmWayId);
        street.setName(name);
        return street;
    }
}
