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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traffic statistics through the whole stack, with a real PostgreSQL/PostGIS database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@Transactional
@DisplayName("Traffic statistics API with a real database")
class TrafficStatsApiIT {

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
    @DisplayName("rejects an invalid groupBy with 400 even when the database is empty")
    void rejectsInvalidGroupByWithEmptyDatabase() throws Exception {
        data.clear();

        mockMvc.perform(get("/api/traffic-stats").param("groupBy", "xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(containsString("groupBy invalido")));

        mockMvc.perform(post("/api/traffic-stats/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupBy\":\"xyz\",\"recordIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sums volumes beyond Integer.MAX_VALUE")
    void sumsVolumesBeyondIntegerRange() throws Exception {
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "ARTERIAL", Integer.MAX_VALUE);
        data.insertRecord(streetId, "2024-06-17T12:30:00Z", "ARTERIAL", Integer.MAX_VALUE);

        Map<String, Long> byRoadType = stats("roadType");

        assertThat(byRoadType).containsExactlyEntriesOf(Map.of("ARTERIAL", 2L * Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("groups by road type and treats blank road types as UNKNOWN")
    void groupsByRoadType() throws Exception {
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "LOCAL", 100);
        data.insertRecord(streetId, "2024-06-17T13:00:00Z", "LOCAL", 20);
        data.insertRecord(streetId, "2024-06-17T14:00:00Z", "HIGHWAY", 250);
        data.insertRecord(streetId, "2024-06-17T15:00:00Z", "   ", 30);

        Map<String, Long> byRoadType = stats("roadType");

        assertThat(byRoadType).containsAllEntriesOf(Map.of("LOCAL", 120L, "HIGHWAY", 250L, "UNKNOWN", 30L));
        assertThat(byRoadType).hasSize(3);
    }

    @Test
    @DisplayName("groups by weekday")
    void groupsByWeekday() throws Exception {
        // 2024-06-10 is a Monday and 2024-06-14 a Friday; noon UTC is the same day in any Brazilian time zone.
        data.insertRecord(streetId, "2024-06-10T12:00:00Z", "LOCAL", 100);
        data.insertRecord(streetId, "2024-06-10T15:00:00Z", "LOCAL", 80);
        data.insertRecord(streetId, "2024-06-14T12:00:00Z", "LOCAL", 200);

        Map<String, Long> byWeekday = stats("weekday");

        assertThat(byWeekday).hasSize(7);
        assertThat(nonZero(byWeekday)).containsExactlyInAnyOrderEntriesOf(Map.of("MONDAY", 180L, "FRIDAY", 200L));
    }

    @Test
    @DisplayName("restricts the statistics to the selected records, by GET and by POST")
    void restrictsToSelectedRecords() throws Exception {
        UUID selected = data.insertRecord(streetId, "2024-06-17T12:00:00Z", "LOCAL", 100);
        data.insertRecord(streetId, "2024-06-17T13:00:00Z", "HIGHWAY", 999);

        String viaGet = mockMvc.perform(get("/api/traffic-stats")
                        .param("groupBy", "roadType").param("recordIds", selected.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String viaPost = mockMvc.perform(post("/api/traffic-stats/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("groupBy", "roadType", "recordIds", List.of(selected)))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(toMap(viaGet)).containsExactlyEntriesOf(Map.of("LOCAL", 100L));
        assertThat(toMap(viaPost)).containsExactlyEntriesOf(Map.of("LOCAL", 100L));
    }

    @Test
    @DisplayName("sums the volume per hour, observed in America/Sao_Paulo")
    void sumsVolumePerHour() throws Exception {
        // São Paulo is UTC-3: 08:00Z and 08:30Z are 05h; 17:00Z is 14h.
        data.insertRecord(streetId, "2024-06-17T08:00:00Z", "LOCAL", 100);
        data.insertRecord(streetId, "2024-06-17T08:30:00Z", "LOCAL", 50);
        data.insertRecord(streetId, "2024-06-17T17:00:00Z", "LOCAL", 200);

        Map<String, Long> byHour = stats("hour");

        assertThat(byHour).hasSize(24);
        assertThat(nonZero(byHour)).containsExactlyInAnyOrderEntriesOf(Map.of("05", 150L, "14", 200L));
    }

    @Test
    @DisplayName("observes the hour and the weekday in America/Sao_Paulo by default (day rollover)")
    void observesTheDayRolloverInSaoPaulo() throws Exception {
        // 2026-11-03T01:30:00Z is Tuesday 01:30 in UTC, but Monday 22:30 in São Paulo.
        data.insertRecord(streetId, "2026-11-03T01:30:00Z", "LOCAL", 100);

        assertThat(nonZero(stats("hour"))).containsExactlyEntriesOf(Map.of("22", 100L));
        assertThat(nonZero(stats("weekday"))).containsExactlyEntriesOf(Map.of("MONDAY", 100L));
    }

    @Test
    @DisplayName("returns the 24 hours and the 7 weekdays in order, with zeros, even with an empty database")
    void returnsFullOrderedBucketsWithEmptyDatabase() throws Exception {
        data.clear();

        mockMvc.perform(get("/api/traffic-stats").param("groupBy", "hour"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(24))
                .andExpect(jsonPath("$.labels[0]").value("00"))
                .andExpect(jsonPath("$.labels[23]").value("23"))
                .andExpect(jsonPath("$.values.length()").value(24))
                .andExpect(jsonPath("$.values[?(@ != 0)]").isEmpty());
        mockMvc.perform(get("/api/traffic-stats").param("groupBy", "weekday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(7))
                .andExpect(jsonPath("$.values.length()").value(7))
                .andExpect(jsonPath("$.labels[0]").value("MONDAY"))
                .andExpect(jsonPath("$.labels[6]").value("SUNDAY"));
        mockMvc.perform(get("/api/traffic-stats").param("groupBy", "roadType"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels").isEmpty());
    }

    @Test
    @DisplayName("returns road types in the fixed order with UNKNOWN last")
    void returnsRoadTypesInTheFixedOrder() throws Exception {
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "HIGHWAY", 10);
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", " ", 20);
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "LOCAL", 30);
        data.insertRecord(streetId, "2024-06-17T12:00:00Z", "ARTERIAL", 40);

        mockMvc.perform(get("/api/traffic-stats").param("groupBy", "roadType"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels[0]").value("LOCAL"))
                .andExpect(jsonPath("$.labels[1]").value("ARTERIAL"))
                .andExpect(jsonPath("$.labels[2]").value("HIGHWAY"))
                .andExpect(jsonPath("$.labels[3]").value("UNKNOWN"))
                .andExpect(jsonPath("$.values[0]").value(30))
                .andExpect(jsonPath("$.values[3]").value(20));
    }

    private static Map<String, Long> nonZero(Map<String, Long> buckets) {
        Map<String, Long> result = new LinkedHashMap<>(buckets);
        result.values().removeIf(total -> total == 0L);
        return result;
    }

    private Map<String, Long> stats(String groupBy) throws Exception {
        String body = mockMvc.perform(get("/api/traffic-stats").param("groupBy", groupBy))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return toMap(body);
    }

    private Map<String, Long> toMap(String body) throws Exception {
        JsonNode json = objectMapper.readTree(body);
        Map<String, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < json.get("labels").size(); i++) {
            result.put(json.get("labels").get(i).asText(), json.get("values").get(i).asLong());
        }
        return result;
    }
}
