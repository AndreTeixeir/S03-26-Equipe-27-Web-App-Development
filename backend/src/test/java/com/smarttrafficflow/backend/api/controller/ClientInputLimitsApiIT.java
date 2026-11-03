package com.smarttrafficflow.backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import com.smarttrafficflow.backend.support.TrafficTestData;
import org.junit.jupiter.api.AfterEach;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Input that is too large for the page offset or for the database columns is a client error (400), not a server
 * error (500). Uses a real database because the 500 only shows up when the query or the insert reaches PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@DisplayName("Client input limits with a real database")
class ClientInputLimitsApiIT {

    // Not @Transactional on purpose: inside a test transaction the INSERT is postponed and rolled back, so a text that
    // is too long for its column would never reach PostgreSQL. The data is removed after each test instead.

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TrafficTestData data = new TrafficTestData(jdbcTemplate);
        data.clear();
        data.insertStreet(101L, "Avenida Central");
    }

    @AfterEach
    void tearDown() {
        new TrafficTestData(jdbcTemplate).clear();
    }

    @Test
    @DisplayName("rejects a page number whose offset would not fit an integer")
    void rejectsAHugePageNumber() throws Exception {
        mockMvc.perform(get("/api/traffic-records").param("page", "100000000").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(containsString("page")));
    }

    @Test
    @DisplayName("still serves a high page number that fits")
    void servesAHighPageNumberThatFits() throws Exception {
        mockMvc.perform(get("/api/traffic-records").param("page", "10000000").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("rejects texts longer than the database columns, naming the field")
    void rejectsTextsLongerThanTheColumns() throws Exception {
        assertRecordRejected("roadType", 101);
        assertRecordRejected("eventType", 151);
        assertRecordRejected("weather", 101);
    }

    @Test
    @DisplayName("accepts texts that fill the columns exactly")
    void acceptsTextsThatFillTheColumns() throws Exception {
        Map<String, Object> body = validRecord();
        body.put("roadType", "R".repeat(100));
        body.put("eventType", "E".repeat(150));
        body.put("weather", "W".repeat(100));

        mockMvc.perform(post("/api/traffic-records").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("rejects a simulation scenario name longer than the event type column")
    void rejectsALongScenarioName() throws Exception {
        mockMvc.perform(post("/api/simulations/generate").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("recordsToGenerate", 1, "scenarioName", "S".repeat(151)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(containsString("scenarioName")));
    }

    private void assertRecordRejected(String field, int length) throws Exception {
        Map<String, Object> body = validRecord();
        body.put(field, "x".repeat(length));

        mockMvc.perform(post("/api/traffic-records").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(containsString(field)));
    }

    private Map<String, Object> validRecord() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", "2024-06-17T08:00:00Z");
        body.put("roadType", "LOCAL");
        body.put("vehicleVolume", 10);
        body.put("eventType", "Obra");
        body.put("weather", "Chuva");
        body.put("streetOsmWayId", 101);
        return body;
    }
}
