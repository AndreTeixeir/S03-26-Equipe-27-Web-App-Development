package com.smarttrafficflow.backend.domain.map.service;

import com.smarttrafficflow.backend.config.RecordLimits;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficRecordRepository;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrafficMapService tests")
class TrafficMapServiceTests {

    @Mock
    private TrafficRecordService trafficRecordService;

    private TrafficMapService trafficMapService;

    @BeforeEach
    void setUp() {
        trafficMapService = new TrafficMapService(trafficRecordService, new RecordLimits("10000", "250"));
    }

    @Test
    @DisplayName("without ids, shows the most recent records up to the map ceiling")
    void showsTheMostRecentRecordsWithoutIds() {
        TrafficRecordRepository.TrafficMapFeatureView feature = mock(TrafficRecordRepository.TrafficMapFeatureView.class);
        when(trafficRecordService.findLatestMapFeatures(250)).thenReturn(List.of(feature));

        TrafficMapService.MapContent content = trafficMapService.findContent(null);

        assertThat(content.scope()).isEqualTo("latest");
        assertThat(content.limit()).isEqualTo(250);
        assertThat(content.features()).containsExactly(feature);
        verify(trafficRecordService, never()).findMapFeaturesByRecordIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("treats an empty id list as no selection")
    void treatsAnEmptyListAsNoSelection() {
        when(trafficRecordService.findLatestMapFeatures(250)).thenReturn(List.of());

        TrafficMapService.MapContent content = trafficMapService.findContent(List.of());

        assertThat(content.scope()).isEqualTo("latest");
        assertThat(content.features()).isEmpty();
    }

    @Test
    @DisplayName("with ids, shows the selection without applying the ceiling")
    void showsTheSelectionWithoutACeiling() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        TrafficRecordRepository.TrafficMapFeatureView feature = mock(TrafficRecordRepository.TrafficMapFeatureView.class);
        when(trafficRecordService.findMapFeaturesByRecordIds(recordIds)).thenReturn(List.of(feature));

        TrafficMapService.MapContent content = trafficMapService.findContent(recordIds);

        assertThat(content.scope()).isEqualTo("selection");
        assertThat(content.limit()).isNull();
        assertThat(content.features()).containsExactly(feature);
        verify(trafficRecordService, never()).findLatestMapFeatures(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("uses the configured map ceiling")
    void usesTheConfiguredCeiling() {
        TrafficMapService small = new TrafficMapService(trafficRecordService, new RecordLimits("10000", "40"));
        when(trafficRecordService.findLatestMapFeatures(40)).thenReturn(List.of());

        assertThat(small.findContent(null).limit()).isEqualTo(40);
    }
}
