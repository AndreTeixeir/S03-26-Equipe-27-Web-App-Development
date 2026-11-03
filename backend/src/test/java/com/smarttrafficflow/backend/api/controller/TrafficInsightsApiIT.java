package com.smarttrafficflow.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import com.smarttrafficflow.backend.support.TrafficTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Insights through the whole stack, with a real PostgreSQL/PostGIS database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@Transactional
@DisplayName("Traffic insights API with a real database")
class TrafficInsightsApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TrafficTestData data;
    private UUID streetId;

    @BeforeEach
    void setUp() {
        data = new TrafficTestData(jdbcTemplate);
        data.clear();
        streetId = data.insertStreet(101L, "Avenida Central");
    }

    @Test
    @DisplayName("returns the fallback insight when there are no records")
    void returnsFallbackWithoutRecords() throws Exception {
        data.clear();

        mockMvc.perform(get("/api/traffic-insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(1))
                .andExpect(jsonPath("$.insights[0]").value("Sem dados suficientes para gerar insights."));
    }

    @Test
    @DisplayName("describes the peak hour and the busiest road type in Portuguese")
    void describesThePeakInPortuguese() throws Exception {
        // São Paulo is UTC-3: 21:00Z is 18h.
        data.insertRecord(streetId, "2024-06-17T21:00:00Z", "LOCAL", 300);
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "HIGHWAY", 100);

        mockMvc.perform(get("/api/traffic-insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.length()").value(2))
                .andExpect(jsonPath("$.insights[0]").value("Horário de pico: 18h."))
                .andExpect(jsonPath("$.insights[1]").value("Tipo de via com maior volume: Via local."));
    }

    @Test
    @DisplayName("observes the peak hour in the configured time zone (day rollover)")
    void observesThePeakInTheConfiguredZone() throws Exception {
        data.insertRecord(streetId, "2026-11-03T01:30:00Z", "ARTERIAL", 100);

        mockMvc.perform(get("/api/traffic-insights"))
                .andExpect(jsonPath("$.insights[0]").value("Horário de pico: 22h."))
                .andExpect(jsonPath("$.insights[1]").value("Tipo de via com maior volume: Via arterial."));
    }

    @Test
    @DisplayName("names blank road types as 'Não informado' and highways as 'Via expressa'")
    void translatesUnknownAndHighway() throws Exception {
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "  ", 500);
        mockMvc.perform(get("/api/traffic-insights"))
                .andExpect(jsonPath("$.insights[1]").value("Tipo de via com maior volume: Não informado."));

        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "HIGHWAY", 900);
        mockMvc.perform(get("/api/traffic-insights"))
                .andExpect(jsonPath("$.insights[1]").value("Tipo de via com maior volume: Via expressa."));
    }

    @Test
    @DisplayName("the peak hour matches the highest value of the hourly statistics")
    void peakMatchesTheHourlyStatistics() throws Exception {
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "LOCAL", 120);
        data.insertRecord(streetId, "2024-06-17T12:30:00Z", "LOCAL", 40);
        data.insertRecord(streetId, "2024-06-17T15:00:00Z", "ARTERIAL", 130);
        data.insertRecord(streetId, "2024-06-17T23:00:00Z", "HIGHWAY", 90);

        JsonNode stats = objectMapper.readTree(mockMvc.perform(get("/api/traffic-stats").param("groupBy", "hour"))
                .andReturn().getResponse().getContentAsString());
        int peak = 0;
        for (int hour = 1; hour < 24; hour++) {
            if (stats.get("values").get(hour).asLong() > stats.get("values").get(peak).asLong()) {
                peak = hour;
            }
        }
        String insight = objectMapper.readTree(mockMvc.perform(get("/api/traffic-insights"))
                        .andReturn().getResponse().getContentAsString())
                .get("insights").get(0).asText();

        assertThat(insight).isEqualTo("Horário de pico: " + peak + "h.");
    }

    @Test
    @DisplayName("keeps the earliest hour on a tie, so the result is deterministic")
    void tieKeepsTheEarliestHour() throws Exception {
        data.insertRecord(streetId, "2024-06-17T15:00:00Z", "LOCAL", 100);
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "ARTERIAL", 100);

        mockMvc.perform(get("/api/traffic-insights"))
                .andExpect(jsonPath("$.insights[0]").value("Horário de pico: 9h."))
                .andExpect(jsonPath("$.insights[1]").value("Tipo de via com maior volume: Via local."));
    }

    @Test
    @DisplayName("restricts the insights to the selected records, by GET and by POST")
    void restrictsToSelectedRecords() throws Exception {
        UUID selected = data.insertRecord(streetId, "2024-06-17T21:00:00Z", "LOCAL", 100);
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "HIGHWAY", 999);

        mockMvc.perform(get("/api/traffic-insights").param("recordIds", selected.toString()))
                .andExpect(jsonPath("$.insights[0]").value("Horário de pico: 18h."))
                .andExpect(jsonPath("$.insights[1]").value("Tipo de via com maior volume: Via local."));
        mockMvc.perform(post("/api/traffic-insights/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recordIds", List.of(selected)))))
                .andExpect(jsonPath("$.insights[0]").value("Horário de pico: 18h."));
    }

    @Test
    @DisplayName("finds the peak when the summed volume exceeds Integer.MAX_VALUE")
    void findsThePeakBeyondTheIntegerRange() throws Exception {
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "ARTERIAL", Integer.MAX_VALUE);
        data.insertRecord(streetId, "2024-06-17T12:10:00Z", "ARTERIAL", Integer.MAX_VALUE);
        data.insertRecord(streetId, "2024-06-17T15:00:00Z", "LOCAL", 1000);

        mockMvc.perform(get("/api/traffic-insights"))
                .andExpect(jsonPath("$.insights[0]").value("Horário de pico: 9h."))
                .andExpect(jsonPath("$.insights[1]").value("Tipo de via com maior volume: Via arterial."));
    }
}
