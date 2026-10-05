package com.smarttrafficflow.backend.domain.simulations.service;

import com.smarttrafficflow.backend.api.dto.CreateTrafficRecordRequest;
import com.smarttrafficflow.backend.api.dto.SimulationRequest;
import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import com.smarttrafficflow.backend.domain.streets.service.StreetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationService tests")
class SimulationServiceTests {

    private static final Set<String> VALID_ROAD_TYPES = Set.of("LOCAL", "ARTERIAL", "HIGHWAY");
    private static final Set<String> VALID_WEATHER_TYPES = Set.of("SUNNY", "RAIN", "CLOUDY");

    @Mock
    private TrafficRecordService trafficRecordService;

    @Mock
    private StreetService streetService;

    @InjectMocks
    private SimulationService simulationService;

    @Test
    @DisplayName("creates exactly the requested number of records")
    void createsExactlyTheRequestedNumberOfRecords() {
        when(streetService.getRandomStreetOsmWayIds(anyInt())).thenReturn(List.of(101L));
        when(trafficRecordService.create(any())).thenAnswer(invocation -> responseFrom(invocation.getArgument(0)));

        List<TrafficRecordResponse> generated = simulationService.generate(new SimulationRequest(5, "Teste"));

        assertThat(generated).hasSize(5);
        verify(trafficRecordService, times(5)).create(any());
        verify(streetService, times(1)).getRandomStreetOsmWayIds(5);
    }

    @Test
    @DisplayName("uses the scenario name as the generated event type")
    void usesTheScenarioNameAsEventType() {
        when(streetService.getRandomStreetOsmWayIds(anyInt())).thenReturn(List.of(101L));
        when(trafficRecordService.create(any())).thenAnswer(invocation -> responseFrom(invocation.getArgument(0)));

        simulationService.generate(new SimulationRequest(3, "Varredura Noturna"));

        ArgumentCaptor<CreateTrafficRecordRequest> captor = ArgumentCaptor.forClass(CreateTrafficRecordRequest.class);
        verify(trafficRecordService, times(3)).create(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(request -> {
            assertThat(request.eventType()).isEqualTo("Varredura Noturna");
            assertThat(request.streetOsmWayId()).isEqualTo(101L);
        });
    }

    @Test
    @DisplayName("assigns every record a street from the sampled streets")
    void assignsStreetsFromTheSampledStreets() {
        when(streetService.getRandomStreetOsmWayIds(anyInt())).thenReturn(List.of(101L, 202L, 303L));
        when(trafficRecordService.create(any())).thenAnswer(invocation -> responseFrom(invocation.getArgument(0)));

        simulationService.generate(new SimulationRequest(20, "Teste"));

        ArgumentCaptor<CreateTrafficRecordRequest> captor = ArgumentCaptor.forClass(CreateTrafficRecordRequest.class);
        verify(trafficRecordService, times(20)).create(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CreateTrafficRecordRequest::streetOsmWayId)
                .allMatch(Set.of(101L, 202L, 303L)::contains);
        verify(streetService, times(1)).getRandomStreetOsmWayIds(20);
    }

    @Test
    @DisplayName("generates only supported road types weather values and volume ranges")
    void generatesSupportedRandomValues() {
        when(streetService.getRandomStreetOsmWayIds(anyInt())).thenReturn(List.of(101L));
        when(trafficRecordService.create(any())).thenAnswer(invocation -> responseFrom(invocation.getArgument(0)));

        OffsetDateTime before = OffsetDateTime.now();
        simulationService.generate(new SimulationRequest(20, "Teste"));
        OffsetDateTime after = OffsetDateTime.now();

        ArgumentCaptor<CreateTrafficRecordRequest> captor = ArgumentCaptor.forClass(CreateTrafficRecordRequest.class);
        verify(trafficRecordService, times(20)).create(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(request -> {
            assertThat(VALID_ROAD_TYPES).contains(request.roadType());
            assertThat(VALID_WEATHER_TYPES).contains(request.weather());
            assertThat(request.vehicleVolume()).isBetween(20, 300);
            assertThat(request.timestamp()).isAfterOrEqualTo(before.minusHours(168));
            assertThat(request.timestamp()).isBeforeOrEqualTo(after);
        });
    }

    @Test
    @DisplayName("propagates the street import state error when no street is available")
    void propagatesStreetAvailabilityErrors() {
        when(streetService.getRandomStreetOsmWayIds(anyInt()))
                .thenThrow(new IllegalStateException("Nenhuma rua real importada. Importe GeoJSON antes de simular."));

        assertThatThrownBy(() -> simulationService.generate(new SimulationRequest(1, "Teste")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nenhuma rua real importada");
    }

    private TrafficRecordResponse responseFrom(CreateTrafficRecordRequest request) {
        return new TrafficRecordResponse(
                UUID.randomUUID(),
                request.timestamp(),
                request.roadType(),
                request.vehicleVolume(),
                request.eventType(),
                request.weather(),
                UUID.randomUUID(),
                request.streetOsmWayId(),
                "Rua Simulada"
        );
    }
}
