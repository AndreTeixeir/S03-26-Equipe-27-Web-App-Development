package com.smarttrafficflow.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.map.service.TrafficMapService;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficRecordRepository;
import org.assertj.core.api.SoftAssertions;
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MapController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("MapController MVC integration tests")
class MapControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TrafficMapService trafficMapService;

    @Test
    @DisplayName("returns an empty feature collection with its meta when the base has no records")
    void returnsEmptyFeatureCollectionWithoutRecords() throws Exception {
        when(trafficMapService.findContent(null))
                .thenReturn(new TrafficMapService.MapContent(TrafficMapService.SCOPE_LATEST, 250, List.of()));

        mockMvc.perform(get("/api/traffic-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features").isEmpty())
                .andExpect(jsonPath("$.meta.scope").value("latest"))
                .andExpect(jsonPath("$.meta.limit").value(250))
                .andExpect(jsonPath("$.meta.count").value(0));
    }

    @Test
    @DisplayName("returns colored map features from the get endpoint")
    void returnsMapFeaturesFromGetEndpoint() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID streetId = UUID.randomUUID();
        TrafficRecordRepository.TrafficMapFeatureView featureView = view(recordId, streetId, 101L, "Avenida Central", 220,
                """
                {"type":"LineString","coordinates":[[-46.63,-23.55],[-46.62,-23.54]]}
                """);
        when(trafficMapService.findContent(List.of(recordId))).thenReturn(selection(featureView));

        mockMvc.perform(get("/api/traffic-map")
                        .param("recordIds", recordId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].properties.recordId").value(recordId.toString()))
                .andExpect(jsonPath("$.features[0].properties.streetName").value("Avenida Central"))
                .andExpect(jsonPath("$.features[0].properties.trafficLevel").value("HIGH"))
                .andExpect(jsonPath("$.features[0].properties.color").value("#E04545"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("LineString"))
                .andExpect(jsonPath("$.meta.scope").value("selection"))
                .andExpect(jsonPath("$.meta.limit").doesNotExist())
                .andExpect(jsonPath("$.meta.count").value(1));

        verify(trafficMapService).findContent(List.of(recordId));
    }

    @Test
    @DisplayName("returns map features from the filter endpoint")
    void returnsMapFeaturesFromFilterEndpoint() throws Exception {
        UUID recordId = UUID.randomUUID();
        TrafficRecordRepository.TrafficMapFeatureView featureView = view(recordId, UUID.randomUUID(), 202L, "Rua Um", 120,
                """
                {"type":"LineString","coordinates":[[-46.61,-23.53],[-46.60,-23.52]]}
                """);
        when(trafficMapService.findContent(List.of(recordId))).thenReturn(selection(featureView));

        mockMvc.perform(post("/api/traffic-map/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("recordIds", List.of(recordId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.trafficLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.features[0].properties.color").value("#FFB24A"));
    }

    @Test
    @DisplayName("shows the most recent records when the filter endpoint receives no ids")
    void showsTheMostRecentRecordsFromTheFilterEndpointWithoutIds() throws Exception {
        UUID recordId = UUID.randomUUID();
        TrafficRecordRepository.TrafficMapFeatureView featureView = view(recordId, UUID.randomUUID(), 303L, "Rua Dois", 50,
                """
                {"type":"LineString","coordinates":[[-46.61,-23.53],[-46.60,-23.52]]}
                """);
        when(trafficMapService.findContent(null))
                .thenReturn(new TrafficMapService.MapContent(TrafficMapService.SCOPE_LATEST, 250, List.of(featureView)));

        mockMvc.perform(post("/api/traffic-map/filter").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.trafficLevel").value("LOW"))
                .andExpect(jsonPath("$.meta.scope").value("latest"))
                .andExpect(jsonPath("$.meta.limit").value(250))
                .andExpect(jsonPath("$.meta.count").value(1));
    }

    @Test
    @DisplayName("paints up to 100 vehicles as LOW, up to 200 as MEDIUM and above that as HIGH, at each limit")
    void paintsTheTrafficLevelsAtTheirLimits() throws Exception {
        // The map legend in the frontend (lib/labels.ts, trafficLevelScale) shows these same limits and colors.
        Object[][] expected = {
                {0, "LOW", "#2BA36A"},
                {100, "LOW", "#2BA36A"},
                {101, "MEDIUM", "#FFB24A"},
                {200, "MEDIUM", "#FFB24A"},
                {201, "HIGH", "#E04545"},
                {1000, "HIGH", "#E04545"}
        };
        SoftAssertions softly = new SoftAssertions();

        for (Object[] row : expected) {
            int volume = (int) row[0];
            UUID recordId = UUID.randomUUID();
            TrafficRecordRepository.TrafficMapFeatureView featureView = view(recordId, UUID.randomUUID(), 404L,
                    "Rua do Limite", volume, "{\"type\":\"LineString\",\"coordinates\":[[-46.63,-23.55],[-46.62,-23.54]]}");
            when(trafficMapService.findContent(List.of(recordId))).thenReturn(selection(featureView));

            String body = mockMvc.perform(get("/api/traffic-map").param("recordIds", recordId.toString()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode properties = objectMapper.readTree(body).get("features").get(0).get("properties");

            softly.assertThat(properties.get("trafficLevel").asText()).as("level of %d vehicles", volume).isEqualTo(row[1]);
            softly.assertThat(properties.get("color").asText()).as("color of %d vehicles", volume).isEqualTo(row[2]);
        }

        softly.assertAll();
    }

    @Test
    @DisplayName("surfaces invalid street geometry as a bad request")
    void surfacesInvalidGeometryErrors() throws Exception {
        UUID recordId = UUID.randomUUID();
        TrafficRecordRepository.TrafficMapFeatureView featureView =
                view(recordId, UUID.randomUUID(), 303L, "Rua Quebrada", 80, "{not-json}");
        when(trafficMapService.findContent(List.of(recordId))).thenReturn(selection(featureView));

        mockMvc.perform(get("/api/traffic-map")
                        .param("recordIds", recordId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Geometria invalida")));
    }

    private static TrafficMapService.MapContent selection(TrafficRecordRepository.TrafficMapFeatureView view) {
        return new TrafficMapService.MapContent(TrafficMapService.SCOPE_SELECTION, null, List.of(view));
    }

    private static TrafficRecordRepository.TrafficMapFeatureView view(
            UUID recordId, UUID streetId, long osmWayId, String streetName, int volume, String geometry) {
        TrafficRecordRepository.TrafficMapFeatureView view = mock(TrafficRecordRepository.TrafficMapFeatureView.class);
        when(view.getRecordId()).thenReturn(recordId);
        when(view.getStreetId()).thenReturn(streetId);
        when(view.getStreetOsmWayId()).thenReturn(osmWayId);
        when(view.getStreetName()).thenReturn(streetName);
        when(view.getVehicleVolume()).thenReturn(volume);
        when(view.getGeometry()).thenReturn(geometry);
        return view;
    }
}
