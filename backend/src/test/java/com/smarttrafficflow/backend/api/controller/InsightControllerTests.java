package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.TrafficInsightResponse;
import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.insights.service.InsightService;
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

@WebMvcTest(InsightController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("InsightController MVC integration tests")
class InsightControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightService insightService;

    @Test
    @DisplayName("returns insights from the get endpoint")
    void returnsInsightsFromGetEndpoint() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(insightService.generateInsights(List.of(firstId, secondId)))
                .thenReturn(new TrafficInsightResponse(List.of(
                        "Horário de pico: 8h.",
                        "Tipo de via com maior volume: Via arterial."
                )));

        mockMvc.perform(get("/api/traffic-insights")
                        .param("recordIds", firstId.toString(), secondId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights[0]").value("Horário de pico: 8h."))
                .andExpect(jsonPath("$.insights[1]").value("Tipo de via com maior volume: Via arterial."));

        verify(insightService).generateInsights(List.of(firstId, secondId));
    }

    @Test
    @DisplayName("returns insights from the filter endpoint")
    void returnsInsightsFromFilterEndpoint() throws Exception {
        UUID recordId = UUID.randomUUID();
        when(insightService.generateInsights(List.of(recordId)))
                .thenReturn(new TrafficInsightResponse(List.of("Sem dados suficientes para gerar insights.")));

        mockMvc.perform(post("/api/traffic-insights/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recordIds": ["%s"]
                                }
                                """.formatted(recordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights[0]").value("Sem dados suficientes para gerar insights."));
    }
}
