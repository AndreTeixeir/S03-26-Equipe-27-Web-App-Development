package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.api.dto.TrafficRecordSummaryResponse;
import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.simulations.service.SimulationService;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SimulationController.class, TrafficRecordController.class})
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "app.rate-limit.simulations-per-minute=2",
        "app.rate-limit.traffic-records-per-minute=1"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Write rate limit MVC integration tests")
class WriteRateLimitITTests {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SimulationService simulationService;

    @MockitoBean
    private TrafficRecordService trafficRecordService;

    @Test
    @DisplayName("rejects simulations above the per-minute limit with 429 and Retry-After")
    void rejectsSimulationsAboveLimit() throws Exception {
        when(simulationService.generate(any())).thenReturn(List.of());

        mockMvc.perform(simulation("203.0.113.10")).andExpect(status().isOk());
        mockMvc.perform(simulation("203.0.113.10")).andExpect(status().isOk());
        mockMvc.perform(simulation("203.0.113.10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        verify(simulationService, times(2)).generate(any());
    }

    @Test
    @DisplayName("counts each client IP separately")
    void countsEachClientSeparately() throws Exception {
        when(simulationService.generate(any())).thenReturn(List.of());

        mockMvc.perform(simulation("203.0.113.10")).andExpect(status().isOk());
        mockMvc.perform(simulation("203.0.113.10")).andExpect(status().isOk());
        mockMvc.perform(simulation("203.0.113.20")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("keeps CORS headers on rate limited responses so the browser can read them")
    void keepsCorsHeadersOnRateLimitedResponses() throws Exception {
        when(simulationService.generate(any())).thenReturn(List.of());

        mockMvc.perform(simulation("203.0.113.10").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN));
        mockMvc.perform(simulation("203.0.113.10").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN));
        mockMvc.perform(simulation("203.0.113.10").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
    }

    @Test
    @DisplayName("rejects traffic record creation above the per-minute limit")
    void rejectsTrafficRecordCreationAboveLimit() throws Exception {
        when(trafficRecordService.create(any())).thenReturn(recordResponse());

        mockMvc.perform(trafficRecord("203.0.113.10")).andExpect(status().isCreated());
        mockMvc.perform(trafficRecord("203.0.113.10")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("does not limit other POST routes under traffic records")
    void doesNotLimitSummaryFilter() throws Exception {
        when(trafficRecordService.findSummary(any())).thenReturn(
                new TrafficRecordSummaryResponse(0, 0, 0, 0, null));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/traffic-records/summary/filter")
                            .with(remoteAddr("203.0.113.10"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recordIds\":[]}"))
                    .andExpect(status().isOk());
        }
    }

    private MockHttpServletRequestBuilder simulation(String clientIp) {
        return post("/api/simulations/generate")
                .with(remoteAddr(clientIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordsToGenerate\":1,\"scenarioName\":\"Teste\"}");
    }

    private MockHttpServletRequestBuilder trafficRecord(String clientIp) {
        return post("/api/traffic-records")
                .with(remoteAddr(clientIp))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "timestamp": "2024-06-17T08:00:00Z",
                          "roadType": "ARTERIAL",
                          "vehicleVolume": 120,
                          "streetOsmWayId": 101
                        }
                        """);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddr(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }

    private TrafficRecordResponse recordResponse() {
        return new TrafficRecordResponse(
                UUID.randomUUID(),
                OffsetDateTime.parse("2024-06-17T08:00:00Z"),
                "ARTERIAL",
                120,
                null,
                null,
                UUID.randomUUID(),
                101L,
                "Avenida Central"
        );
    }
}
