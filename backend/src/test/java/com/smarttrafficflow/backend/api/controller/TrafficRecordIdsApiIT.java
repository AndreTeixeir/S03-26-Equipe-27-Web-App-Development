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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Select all filtered": the ids of every record that matches the table filter, in one request, with a ceiling
 * (3 here, to prove the cut) and a real PostgreSQL/PostGIS database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@TestPropertySource(properties = "app.export.max-records=3")
@Transactional
@DisplayName("Filtered record ids API with a real database")
class TrafficRecordIdsApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> oldestFirst = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TrafficTestData data = new TrafficTestData(jdbcTemplate);
        data.clear();
        oldestFirst.clear();
        UUID central = data.insertStreet(101L, "Avenida Central");
        UUID flores = data.insertStreet(202L, "Rua das Flores");
        oldestFirst.add(data.insertRecord(central, "2024-06-10T08:00:00Z", "LOCAL", 10, "Obra", "Chuva"));
        oldestFirst.add(data.insertRecord(flores, "2024-06-11T08:00:00Z", "HIGHWAY", 20, null, "Sol"));
        oldestFirst.add(data.insertRecord(central, "2024-06-12T08:00:00Z", "HIGHWAY", 30, "Show", null));
        oldestFirst.add(data.insertRecord(flores, "2024-06-13T08:00:00Z", "LOCAL", 40, null, null));
        oldestFirst.add(data.insertRecord(central, "2024-06-14T08:00:00Z", "ARTERIAL", 50, null, "Chuva"));
    }

    @Test
    @DisplayName("without a filter, returns the most recent ids up to the ceiling and tells the list was cut")
    void cutsTheListAtTheCeiling() throws Exception {
        mockMvc.perform(get("/api/traffic-records/ids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordIds.length()").value(3))
                .andExpect(jsonPath("$.recordIds[0]").value(oldestFirst.get(4).toString()))
                .andExpect(jsonPath("$.recordIds[1]").value(oldestFirst.get(3).toString()))
                .andExpect(jsonPath("$.recordIds[2]").value(oldestFirst.get(2).toString()))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    @DisplayName("applies the same filter as the records table: road type, street, weather and event")
    void appliesTheTableFilter() throws Exception {
        mockMvc.perform(get("/api/traffic-records/ids").param("query", "highway"))
                .andExpect(jsonPath("$.recordIds.length()").value(2))
                .andExpect(jsonPath("$.recordIds[0]").value(oldestFirst.get(2).toString()))
                .andExpect(jsonPath("$.recordIds[1]").value(oldestFirst.get(1).toString()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.truncated").value(false));
        mockMvc.perform(get("/api/traffic-records/ids").param("query", "  FLORES "))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/traffic-records/ids").param("query", "chuva"))
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/traffic-records/ids").param("query", "show"))
                .andExpect(jsonPath("$.recordIds[0]").value(oldestFirst.get(2).toString()));
    }

    @Test
    @DisplayName("counts the same records as the paged table for the same filter")
    void matchesThePagedTable() throws Exception {
        for (String query : new String[]{"", "central", "local", "inexistente", "chuva"}) {
            JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/traffic-records").param("query", query))
                    .andReturn().getResponse().getContentAsString());
            JsonNode ids = objectMapper.readTree(mockMvc.perform(get("/api/traffic-records/ids").param("query", query))
                    .andReturn().getResponse().getContentAsString());

            assertThat(ids.get("total").asLong()).as("query '%s'", query).isEqualTo(page.get("totalItems").asLong());
        }
    }

    @Test
    @DisplayName("returns an empty list and no truncation when nothing matches")
    void returnsNothingWhenNothingMatches() throws Exception {
        mockMvc.perform(get("/api/traffic-records/ids").param("query", "inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordIds").isEmpty())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    @DisplayName("is not truncated when the matches fit exactly in the ceiling")
    void isNotTruncatedAtTheCeiling() throws Exception {
        mockMvc.perform(get("/api/traffic-records/ids").param("query", "avenida"))
                .andExpect(jsonPath("$.recordIds.length()").value(3))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.truncated").value(false));
    }
}
