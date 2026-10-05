package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.simulations.service.SimulationService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SimulationController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("SimulationController MVC integration tests")
class SimulationControllerITTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationService simulationService;

    @Test
    @DisplayName("returns generated records for valid simulation requests")
    void returnsGeneratedRecords() throws Exception {
        when(simulationService.generate(any())).thenReturn(List.of(
                recordResponse("ARTERIAL", 120, "Teste", "SUNNY", 101L, "Avenida Central"),
                recordResponse("LOCAL", 80, "Teste", "RAIN", 202L, "Rua Um")
        ));

        mockMvc.perform(post("/api/simulations/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordsToGenerate": 2,
                                  "scenarioName": "Teste"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("Teste"))
                .andExpect(jsonPath("$[1].streetOsmWayId").value(202));
    }

    @Test
    @DisplayName("rejects simulation requests below the minimum amount")
    void rejectsInvalidSimulationRequests() throws Exception {
        mockMvc.perform(post("/api/simulations/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordsToGenerate": 0,
                                  "scenarioName": "Teste"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("recordsToGenerate")));
    }

    @Test
    @DisplayName("rejects simulation requests above the maximum amount")
    void rejectsSimulationRequestsAboveMaximum() throws Exception {
        mockMvc.perform(post("/api/simulations/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordsToGenerate": 251,
                                  "scenarioName": "Teste"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("recordsToGenerate")));

        verifyNoInteractions(simulationService);
    }

    @Test
    @DisplayName("accepts simulation requests at the maximum amount")
    void acceptsSimulationRequestsAtMaximum() throws Exception {
        when(simulationService.generate(any())).thenReturn(List.of());

        mockMvc.perform(post("/api/simulations/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordsToGenerate": 250,
                                  "scenarioName": "Teste"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("surfaces simulation state errors reported by the service")
    void surfacesSimulationStateErrors() throws Exception {
        when(simulationService.generate(any()))
                .thenThrow(new IllegalStateException("Nenhuma rua real importada. Importe GeoJSON antes de simular."));

        mockMvc.perform(post("/api/simulations/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordsToGenerate": 1,
                                  "scenarioName": "Teste"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Nenhuma rua real importada")));
    }

    private TrafficRecordResponse recordResponse(
            String roadType,
            int volume,
            String eventType,
            String weather,
            long streetOsmWayId,
            String streetName
    ) {
        return new TrafficRecordResponse(
                UUID.randomUUID(),
                OffsetDateTime.parse("2024-06-17T08:00:00Z"),
                roadType,
                volume,
                eventType,
                weather,
                UUID.randomUUID(),
                streetOsmWayId,
                streetName
        );
    }
}
