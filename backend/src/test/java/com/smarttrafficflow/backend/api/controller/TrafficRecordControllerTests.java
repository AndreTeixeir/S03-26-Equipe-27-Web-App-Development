package com.smarttrafficflow.backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.api.dto.PagedTrafficRecordResponse;
import com.smarttrafficflow.backend.api.dto.RecordIdsResponse;
import com.smarttrafficflow.backend.api.dto.TrafficRecordSummaryResponse;
import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrafficRecordController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("TrafficRecordController MVC integration tests")
class TrafficRecordControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrafficRecordService trafficRecordService;

    @Test
    @DisplayName("creates a traffic record when the payload is valid")
    void createsRecordWhenPayloadIsValid() throws Exception {
        UUID recordId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID streetId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        TrafficRecordResponse response = new TrafficRecordResponse(
                recordId,
                OffsetDateTime.parse("2024-06-17T08:00:00Z"),
                "ARTERIAL",
                120,
                "RUSH_HOUR",
                "SUNNY",
                streetId,
                101L,
                "Avenida Central"
        );
        when(trafficRecordService.create(any())).thenReturn(response);

        Map<String, Object> body = Map.of(
                "timestamp", "2024-06-17T08:00:00Z",
                "roadType", "ARTERIAL",
                "vehicleVolume", 120,
                "eventType", "RUSH_HOUR",
                "weather", "SUNNY",
                "streetOsmWayId", 101
        );

        mockMvc.perform(post("/api/traffic-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(recordId.toString()))
                .andExpect(jsonPath("$.roadType").value("ARTERIAL"))
                .andExpect(jsonPath("$.vehicleVolume").value(120))
                .andExpect(jsonPath("$.streetOsmWayId").value(101))
                .andExpect(jsonPath("$.streetName").value("Avenida Central"));
    }

    @Test
    @DisplayName("rejects requests without the required road type")
    void rejectsRecordWhenRoadTypeIsMissing() throws Exception {
        Map<String, Object> body = Map.of(
                "timestamp", "2024-06-17T08:00:00Z",
                "vehicleVolume", 120,
                "streetOsmWayId", 101
        );

        mockMvc.perform(post("/api/traffic-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("roadType")));
    }

    @Test
    @DisplayName("rejects requests with a non-positive street osm way id")
    void rejectsRecordWhenStreetOsmWayIdIsInvalid() throws Exception {
        Map<String, Object> body = Map.of(
                "timestamp", "2024-06-17T08:00:00Z",
                "roadType", "LOCAL",
                "vehicleVolume", 120,
                "streetOsmWayId", 0
        );

        mockMvc.perform(post("/api/traffic-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("streetOsmWayId")));
    }

    @Test
    @DisplayName("surfaces invalid street ids reported by the service")
    void rejectsRecordWhenStreetServiceCannotResolveTheStreet() throws Exception {
        when(trafficRecordService.create(any())).thenThrow(new IllegalArgumentException("streetOsmWayId invalido: 999"));

        Map<String, Object> body = Map.of(
                "timestamp", "2024-06-17T08:00:00Z",
                "roadType", "LOCAL",
                "vehicleVolume", 120,
                "streetOsmWayId", 999
        );

        mockMvc.perform(post("/api/traffic-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("streetOsmWayId invalido: 999"));
    }

    @Test
    @DisplayName("returns an empty paged payload when there are no stored records")
    void returnsEmptyListWhenThereAreNoRecords() throws Exception {
        when(trafficRecordService.findPage(0, 20, null)).thenReturn(new PagedTrafficRecordResponse(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/traffic-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("returns paged records exposed by the service")
    void returnsAllRecordsWhenDataExists() throws Exception {
        when(trafficRecordService.findPage(1, 10, "norte")).thenReturn(new PagedTrafficRecordResponse(
                List.of(
                        new TrafficRecordResponse(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                                OffsetDateTime.parse("2024-06-17T08:00:00Z"),
                                "ARTERIAL",
                                120,
                                "RUSH_HOUR",
                                "SUNNY",
                                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                                101L,
                                "Avenida Central"
                        ),
                        new TrafficRecordResponse(
                                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                                OffsetDateTime.parse("2024-06-17T17:30:00Z"),
                                "HIGHWAY",
                                250,
                                null,
                                "RAIN",
                                UUID.fromString("00000000-0000-0000-0000-000000000202"),
                                202L,
                                "Rodovia Norte"
                        )
                ),
                1,
                10,
                12,
                2
        ));

        mockMvc.perform(get("/api/traffic-records")
                        .param("page", "1")
                        .param("size", "10")
                        .param("query", "norte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].streetName").value("Avenida Central"))
                .andExpect(jsonPath("$.items[1].roadType").value("HIGHWAY"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").value(12))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("returns the ids of the filtered records in one response, flagging a cut")
    void returnsTheFilteredIds() throws Exception {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(trafficRecordService.findFilteredIds("norte"))
                .thenReturn(new RecordIdsResponse(List.of(first, second), 12_345, 10_000, true));

        mockMvc.perform(get("/api/traffic-records/ids").param("query", "norte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordIds[0]").value(first.toString()))
                .andExpect(jsonPath("$.recordIds[1]").value(second.toString()))
                .andExpect(jsonPath("$.total").value(12345))
                .andExpect(jsonPath("$.limit").value(10000))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    @DisplayName("accepts the ids request without a filter")
    void returnsTheIdsWithoutAFilter() throws Exception {
        when(trafficRecordService.findFilteredIds(null)).thenReturn(new RecordIdsResponse(List.of(), 0, 10_000, false));

        mockMvc.perform(get("/api/traffic-records/ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordIds").isEmpty())
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    @DisplayName("returns summary metrics for all records")
    void returnsSummaryMetricsForAllRecords() throws Exception {
        when(trafficRecordService.findSummary(null)).thenReturn(new TrafficRecordSummaryResponse(
                2,
                370,
                2,
                185,
                OffsetDateTime.parse("2024-06-17T17:30:00Z")
        ));

        mockMvc.perform(get("/api/traffic-records/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(2))
                .andExpect(jsonPath("$.totalVehicleVolume").value(370))
                .andExpect(jsonPath("$.uniqueStreetCount").value(2))
                .andExpect(jsonPath("$.averageVehicleVolume").value(185))
                .andExpect(jsonPath("$.latestTimestamp").value("2024-06-17T17:30:00Z"));
    }

    @Test
    @DisplayName("returns summary metrics for selected records")
    void returnsSummaryMetricsForSelectedRecords() throws Exception {
        List<UUID> recordIds = List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );
        when(trafficRecordService.findSummary(eq(recordIds))).thenReturn(new TrafficRecordSummaryResponse(
                2,
                370,
                2,
                185,
                OffsetDateTime.parse("2024-06-17T17:30:00Z")
        ));

        mockMvc.perform(post("/api/traffic-records/summary/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recordIds", recordIds))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordCount").value(2))
                .andExpect(jsonPath("$.totalVehicleVolume").value(370));
    }
}
