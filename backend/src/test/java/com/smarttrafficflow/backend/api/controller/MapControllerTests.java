package com.smarttrafficflow.backend.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficRecordRepository;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
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
    private TrafficRecordService trafficRecordService;

    @Test
    @DisplayName("returns an empty feature collection when no record ids are selected")
    void returnsEmptyFeatureCollectionWithoutSelectedRecordIds() throws Exception {
        mockMvc.perform(get("/api/traffic-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features").isEmpty());
    }

    @Test
    @DisplayName("returns colored map features from the get endpoint")
    void returnsMapFeaturesFromGetEndpoint() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID streetId = UUID.randomUUID();
        TrafficRecordRepository.TrafficMapFeatureView featureView = mock(TrafficRecordRepository.TrafficMapFeatureView.class);
        when(featureView.getRecordId()).thenReturn(recordId);
        when(featureView.getStreetId()).thenReturn(streetId);
        when(featureView.getStreetOsmWayId()).thenReturn(101L);
        when(featureView.getStreetName()).thenReturn("Avenida Central");
        when(featureView.getVehicleVolume()).thenReturn(220);
        when(featureView.getGeometry()).thenReturn("""
                {"type":"LineString","coordinates":[[-46.63,-23.55],[-46.62,-23.54]]}
                """);
        when(trafficRecordService.findMapFeaturesByRecordIds(List.of(recordId))).thenReturn(List.of(featureView));

        mockMvc.perform(get("/api/traffic-map")
                        .param("recordIds", recordId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].properties.recordId").value(recordId.toString()))
                .andExpect(jsonPath("$.features[0].properties.streetName").value("Avenida Central"))
                .andExpect(jsonPath("$.features[0].properties.trafficLevel").value("HIGH"))
                .andExpect(jsonPath("$.features[0].properties.color").value("#E04545"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("LineString"));

        verify(trafficRecordService).findMapFeaturesByRecordIds(List.of(recordId));
    }

    @Test
    @DisplayName("returns map features from the filter endpoint")
    void returnsMapFeaturesFromFilterEndpoint() throws Exception {
        UUID recordId = UUID.randomUUID();
        TrafficRecordRepository.TrafficMapFeatureView featureView = mock(TrafficRecordRepository.TrafficMapFeatureView.class);
        when(featureView.getRecordId()).thenReturn(recordId);
        when(featureView.getStreetId()).thenReturn(UUID.randomUUID());
        when(featureView.getStreetOsmWayId()).thenReturn(202L);
        when(featureView.getStreetName()).thenReturn("Rua Um");
        when(featureView.getVehicleVolume()).thenReturn(120);
        when(featureView.getGeometry()).thenReturn("""
                {"type":"LineString","coordinates":[[-46.61,-23.53],[-46.60,-23.52]]}
                """);
        when(trafficRecordService.findMapFeaturesByRecordIds(List.of(recordId))).thenReturn(List.of(featureView));

        mockMvc.perform(post("/api/traffic-map/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("recordIds", List.of(recordId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.trafficLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.features[0].properties.color").value("#FFB24A"));
    }

    @Test
    @DisplayName("surfaces invalid street geometry as a bad request")
    void surfacesInvalidGeometryErrors() throws Exception {
        UUID recordId = UUID.randomUUID();
        TrafficRecordRepository.TrafficMapFeatureView featureView = mock(TrafficRecordRepository.TrafficMapFeatureView.class);
        when(featureView.getRecordId()).thenReturn(recordId);
        when(featureView.getStreetId()).thenReturn(UUID.randomUUID());
        when(featureView.getStreetOsmWayId()).thenReturn(303L);
        when(featureView.getStreetName()).thenReturn("Rua Quebrada");
        when(featureView.getVehicleVolume()).thenReturn(80);
        when(featureView.getGeometry()).thenReturn("{not-json}");
        when(trafficRecordService.findMapFeaturesByRecordIds(List.of(recordId))).thenReturn(List.of(featureView));

        mockMvc.perform(get("/api/traffic-map")
                        .param("recordIds", recordId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Geometria invalida")));
    }
}
