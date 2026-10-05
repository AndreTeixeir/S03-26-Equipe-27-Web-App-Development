package com.smarttrafficflow.backend.domain.trafficrecords.service;

import com.smarttrafficflow.backend.api.dto.CreateTrafficRecordRequest;
import com.smarttrafficflow.backend.api.dto.PagedTrafficRecordResponse;
import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.api.dto.TrafficRecordSummaryResponse;
import com.smarttrafficflow.backend.domain.streets.entity.Street;
import com.smarttrafficflow.backend.domain.streets.service.StreetService;
import com.smarttrafficflow.backend.domain.trafficrecords.entity.TrafficRecord;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficRecordRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrafficRecordService tests")
class TrafficRecordServiceTests {

    @Mock
    private TrafficRecordRepository trafficRecordRepository;

    @Mock
    private StreetService streetService;

    @InjectMocks
    private TrafficRecordService trafficRecordService;

    @Test
    @DisplayName("creates a record by resolving the street and mapping the saved entity")
    void createsRecordsWithResolvedStreetData() {
        Street street = street(101L, "Avenida Central");
        CreateTrafficRecordRequest request = new CreateTrafficRecordRequest(
                OffsetDateTime.of(2024, 6, 17, 8, 0, 0, 0, ZoneOffset.UTC),
                "ARTERIAL",
                120,
                "RUSH_HOUR",
                "SUNNY",
                101L
        );
        when(streetService.getByOsmWayId(101L)).thenReturn(street);
        when(trafficRecordRepository.save(any())).thenAnswer(invocation -> {
            TrafficRecord record = invocation.getArgument(0);
            record.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            return record;
        });

        TrafficRecordResponse response = trafficRecordService.create(request);

        assertThat(response.roadType()).isEqualTo("ARTERIAL");
        assertThat(response.vehicleVolume()).isEqualTo(120);
        assertThat(response.streetOsmWayId()).isEqualTo(101L);
        assertThat(response.streetName()).isEqualTo("Avenida Central");

        ArgumentCaptor<TrafficRecord> captor = ArgumentCaptor.forClass(TrafficRecord.class);
        verify(trafficRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getStreet()).isEqualTo(street);
    }

    @Test
    @DisplayName("maps all street-based fields when reading all records")
    void mapsStreetBasedFieldsWhenFindingAllRecords() {
        when(trafficRecordRepository.findAllWithStreet()).thenReturn(List.of(
                entity("ARTERIAL", 120, "Avenida Central", 101L),
                entity("HIGHWAY", 250, "Rodovia Norte", 202L)
        ));

        List<TrafficRecordResponse> records = trafficRecordService.findAll();

        assertThat(records).hasSize(2);
        assertThat(records.get(0).streetName()).isEqualTo("Avenida Central");
        assertThat(records.get(1).streetOsmWayId()).isEqualTo(202L);
    }

    @Test
    @DisplayName("returns paged records with normalized query and stable metadata")
    void returnsPagedRecords() {
        when(trafficRecordRepository.findPageWithStreet(any(), any())).thenReturn(new PageImpl<>(
                List.of(
                        entity("ARTERIAL", 120, "Avenida Central", 101L),
                        entity("HIGHWAY", 250, "Rodovia Norte", 202L)
                ),
                PageRequest.of(1, 2),
                5
        ));

        PagedTrafficRecordResponse response = trafficRecordService.findPage(1, 2, "  norte ");

        assertThat(response.items()).hasSize(2);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        verify(trafficRecordRepository).findPageWithStreet(org.mockito.ArgumentMatchers.eq("%norte%"), any());
    }

    @Test
    @DisplayName("caps the requested page size at 100 records")
    void capsRequestedPageSize() {
        when(trafficRecordRepository.findPageWithStreet(any(), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));

        PagedTrafficRecordResponse oversized = trafficRecordService.findPage(0, 1000, null);
        PagedTrafficRecordResponse atLimit = trafficRecordService.findPage(0, 100, null);

        ArgumentCaptor<org.springframework.data.domain.Pageable> pageables =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(trafficRecordRepository, org.mockito.Mockito.times(2)).findPageWithStreet(any(), pageables.capture());
        assertThat(pageables.getAllValues()).extracting(org.springframework.data.domain.Pageable::getPageSize)
                .containsExactly(100, 100);
        assertThat(oversized.size()).isEqualTo(100);
        assertThat(atLimit.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("summarizes all records when no ids are provided")
    void summarizesAllRecordsWhenNoIdsProvided() {
        TrafficRecordRepository.TrafficRecordSummaryView summaryView = mock(TrafficRecordRepository.TrafficRecordSummaryView.class);
        when(summaryView.getRecordCount()).thenReturn(4L);
        when(summaryView.getTotalVehicleVolume()).thenReturn(640L);
        when(summaryView.getUniqueStreetCount()).thenReturn(3L);
        when(summaryView.getLatestTimestamp()).thenReturn(OffsetDateTime.parse("2024-06-17T17:30:00Z"));
        when(trafficRecordRepository.summarizeAll()).thenReturn(summaryView);

        TrafficRecordSummaryResponse response = trafficRecordService.findSummary(List.of());

        assertThat(response.recordCount()).isEqualTo(4);
        assertThat(response.totalVehicleVolume()).isEqualTo(640);
        assertThat(response.uniqueStreetCount()).isEqualTo(3);
        assertThat(response.averageVehicleVolume()).isEqualTo(160);
        assertThat(response.latestTimestamp()).isEqualTo(OffsetDateTime.parse("2024-06-17T17:30:00Z"));
        verify(trafficRecordRepository).summarizeAll();
        verify(trafficRecordRepository, never()).summarizeByIds(any());
    }

    @Test
    @DisplayName("summarizes selected records when ids are provided")
    void summarizesSelectedRecordsWhenIdsProvided() {
        List<UUID> recordIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        TrafficRecordRepository.TrafficRecordSummaryView summaryView = mock(TrafficRecordRepository.TrafficRecordSummaryView.class);
        when(summaryView.getRecordCount()).thenReturn(2L);
        when(summaryView.getTotalVehicleVolume()).thenReturn(370L);
        when(summaryView.getUniqueStreetCount()).thenReturn(2L);
        when(summaryView.getLatestTimestamp()).thenReturn(OffsetDateTime.parse("2024-06-17T08:00:00Z"));
        when(trafficRecordRepository.summarizeByIds(recordIds)).thenReturn(summaryView);

        TrafficRecordSummaryResponse response = trafficRecordService.findSummary(recordIds);

        assertThat(response.recordCount()).isEqualTo(2);
        assertThat(response.totalVehicleVolume()).isEqualTo(370);
        assertThat(response.uniqueStreetCount()).isEqualTo(2);
        assertThat(response.averageVehicleVolume()).isEqualTo(185);
        verify(trafficRecordRepository).summarizeByIds(recordIds);
        verify(trafficRecordRepository, never()).summarizeAll();
    }

    @Test
    @DisplayName("returns an empty list when no ids are provided for filtered entity lookup")
    void returnsEmptyListForEmptyIdFilter() {
        assertThat(trafficRecordService.findEntitiesByIds(List.of())).isEmpty();
        verify(trafficRecordRepository, never()).findAllByIdInWithStreet(any());
    }

    @Test
    @DisplayName("delegates filtered entity lookup to the repository when ids are provided")
    void delegatesFilteredEntityLookup() {
        List<UUID> recordIds = List.of(UUID.randomUUID());
        when(trafficRecordRepository.findAllByIdInWithStreet(recordIds)).thenReturn(List.of(
                entity("LOCAL", 80, "Rua Um", 303L)
        ));

        List<TrafficRecord> entities = trafficRecordService.findEntitiesByIds(recordIds);

        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).getStreet().getName()).isEqualTo("Rua Um");
        verify(trafficRecordRepository).findAllByIdInWithStreet(recordIds);
    }

    @Test
    @DisplayName("returns an empty list when no ids are provided for map features")
    void returnsEmptyMapFeaturesForEmptyIdFilter() {
        assertThat(trafficRecordService.findMapFeaturesByRecordIds(List.of())).isEmpty();
        verify(trafficRecordRepository, never()).findMapFeaturesByRecordIds(any());
    }

    @Test
    @DisplayName("delegates map feature queries when ids are provided")
    void delegatesMapFeatureQueries() {
        List<UUID> recordIds = List.of(UUID.randomUUID());
        TrafficRecordRepository.TrafficMapFeatureView featureView = mock(TrafficRecordRepository.TrafficMapFeatureView.class);
        when(trafficRecordRepository.findMapFeaturesByRecordIds(recordIds)).thenReturn(List.of(featureView));

        List<TrafficRecordRepository.TrafficMapFeatureView> features =
                trafficRecordService.findMapFeaturesByRecordIds(recordIds);

        assertThat(features).containsExactly(featureView);
        verify(trafficRecordRepository).findMapFeaturesByRecordIds(recordIds);
    }

    private TrafficRecord entity(String roadType, int volume, String streetName, long streetOsmWayId) {
        TrafficRecord record = new TrafficRecord();
        record.setId(UUID.randomUUID());
        record.setTimestamp(OffsetDateTime.of(2024, 6, 17, 8, 0, 0, 0, ZoneOffset.UTC));
        record.setRoadType(roadType);
        record.setVehicleVolume(volume);
        record.setEventType("RUSH_HOUR");
        record.setWeather("SUNNY");
        record.setStreet(street(streetOsmWayId, streetName));
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
