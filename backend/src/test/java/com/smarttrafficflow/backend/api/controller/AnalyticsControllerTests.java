package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.TrafficStatsResponse;
import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.analytics.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("AnalyticsController MVC integration tests")
class AnalyticsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("returns grouped stats for the get endpoint")
    void returnsStatsFromGetEndpoint() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(analyticsService.getStats("hour", List.of(firstId, secondId)))
                .thenReturn(new TrafficStatsResponse(List.of("08", "17"), List.of(150L, 200L)));

        mockMvc.perform(get("/api/traffic-stats")
                        .param("groupBy", "hour")
                        .param("recordIds", firstId.toString(), secondId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels[0]").value("08"))
                .andExpect(jsonPath("$.values[1]").value(200));

        verify(analyticsService).getStats("hour", List.of(firstId, secondId));
    }

    @Test
    @DisplayName("returns grouped stats for the filter endpoint")
    void returnsStatsFromFilterEndpoint() throws Exception {
        UUID recordId = UUID.randomUUID();
        when(analyticsService.getStats("roadType", List.of(recordId)))
                .thenReturn(new TrafficStatsResponse(List.of("ARTERIAL"), List.of(180L)));

        mockMvc.perform(post("/api/traffic-stats/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupBy": "roadType",
                                  "recordIds": ["%s"]
                                }
                                """.formatted(recordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels[0]").value("ARTERIAL"))
                .andExpect(jsonPath("$.values[0]").value(180));
    }

    @Test
    @DisplayName("rejects filter requests without a group by value")
    void rejectsInvalidFilterRequests() throws Exception {
        mockMvc.perform(post("/api/traffic-stats/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupBy": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("groupBy")));
    }

    @Test
    @DisplayName("surfaces invalid group by values reported by the service")
    void surfacesInvalidGroupByErrors() throws Exception {
        when(analyticsService.getStats("invalid", null))
                .thenThrow(new IllegalArgumentException("groupBy invalido: invalid"));

        mockMvc.perform(get("/api/traffic-stats")
                        .param("groupBy", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("groupBy invalido: invalid"));
    }
}
