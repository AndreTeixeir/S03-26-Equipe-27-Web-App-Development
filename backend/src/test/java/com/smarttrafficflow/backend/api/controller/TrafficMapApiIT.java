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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The traffic map through the whole stack, with a real PostgreSQL/PostGIS database and a small ceiling (3 records)
 * to prove the cut.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@TestPropertySource(properties = "app.map.max-records=3")
@Transactional
@DisplayName("Traffic map API with a real database")
class TrafficMapApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TrafficTestData data;
    private final List<UUID> recordsOldestFirst = new ArrayList<>();

    @BeforeEach
    void setUp() {
        data = new TrafficTestData(jdbcTemplate);
        data.clear();
        recordsOldestFirst.clear();
        UUID street = data.insertStreet(101L, "Avenida Central");
        for (int day = 10; day <= 14; day++) {
            recordsOldestFirst.add(data.insertRecord(street, "2024-06-" + day + "T08:00:00Z", "LOCAL", day * 10));
        }
    }

    @Test
    @DisplayName("without selection, shows the most recent records up to the ceiling, newest first")
    void showsTheMostRecentRecordsWithoutSelection() throws Exception {
        mockMvc.perform(get("/api/traffic-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(3))
                .andExpect(jsonPath("$.features[0].properties.recordId").value(recordsOldestFirst.get(4).toString()))
                .andExpect(jsonPath("$.features[1].properties.recordId").value(recordsOldestFirst.get(3).toString()))
                .andExpect(jsonPath("$.features[2].properties.recordId").value(recordsOldestFirst.get(2).toString()))
                .andExpect(jsonPath("$.meta.scope").value("latest"))
                .andExpect(jsonPath("$.meta.limit").value(3))
                .andExpect(jsonPath("$.meta.count").value(3));
    }

    @Test
    @DisplayName("a POST without ids shows the most recent records too")
    void aPostWithoutIdsShowsTheMostRecentRecords() throws Exception {
        mockMvc.perform(post("/api/traffic-map/filter").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(3))
                .andExpect(jsonPath("$.meta.scope").value("latest"));
        mockMvc.perform(post("/api/traffic-map/filter").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordIds\":[]}"))
                .andExpect(jsonPath("$.features.length()").value(3));
    }

    @Test
    @DisplayName("with a selection, shows exactly the selected records, even the old ones")
    void showsTheSelection() throws Exception {
        List<UUID> selected = List.of(recordsOldestFirst.get(0), recordsOldestFirst.get(1));

        String body = mockMvc.perform(post("/api/traffic-map/filter").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recordIds", selected))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.scope").value("selection"))
                .andExpect(jsonPath("$.meta.count").value(2))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode features = objectMapper.readTree(body).get("features");
        assertThat(features).hasSize(2);
        assertThat(List.of(
                features.get(0).get("properties").get("recordId").asText(),
                features.get(1).get("properties").get("recordId").asText()))
                .containsExactlyInAnyOrder(selected.get(0).toString(), selected.get(1).toString());
    }

    @Test
    @DisplayName("a selection is not cut by the ceiling, as before")
    void aSelectionIsNotCut() throws Exception {
        mockMvc.perform(get("/api/traffic-map").param("recordIds",
                        recordsOldestFirst.stream().map(UUID::toString).toArray(String[]::new)))
                .andExpect(jsonPath("$.features.length()").value(5))
                .andExpect(jsonPath("$.meta.scope").value("selection"))
                .andExpect(jsonPath("$.meta.count").value(5));
    }

    @Test
    @DisplayName("describes each street with its volume, level, color and GeoJSON geometry")
    void describesEachFeature() throws Exception {
        mockMvc.perform(get("/api/traffic-map"))
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].properties.streetName").value("Avenida Central"))
                .andExpect(jsonPath("$.features[0].properties.streetOsmWayId").value(101))
                .andExpect(jsonPath("$.features[0].properties.vehicleVolume").value(140))
                .andExpect(jsonPath("$.features[0].properties.trafficLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.features[0].properties.color").value("#FFB24A"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("LineString"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates.length()").value(2));
    }

    @Test
    @DisplayName("returns an empty collection with a count of zero when there are no records")
    void returnsAnEmptyCollection() throws Exception {
        data.clear();

        mockMvc.perform(get("/api/traffic-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isEmpty())
                .andExpect(jsonPath("$.meta.scope").value("latest"))
                .andExpect(jsonPath("$.meta.count").value(0));
    }
}
