package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.RecordIdsFilterRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.domain.map.service.TrafficMapService;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/traffic-map")
public class MapController {

    private static final Logger log = LoggerFactory.getLogger(MapController.class);
    private static final TypeReference<Map<String, Object>> GEOMETRY_TYPE = new TypeReference<>() {
    };
    private static final String LOW_COLOR = "#2BA36A";
    private static final String MEDIUM_COLOR = "#FFB24A";
    private static final String HIGH_COLOR = "#E04545";

    private final TrafficMapService trafficMapService;
    private final ObjectMapper objectMapper;

    public MapController(TrafficMapService trafficMapService, ObjectMapper objectMapper) {
        this.trafficMapService = trafficMapService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Map<String, Object> getMapData(@RequestParam(required = false) List<UUID> recordIds) {
        return buildMapResponse(recordIds, "GET /api/traffic-map");
    }

    @PostMapping("/filter")
    public Map<String, Object> getMapDataFiltered(@RequestBody RecordIdsFilterRequest request) {
        return buildMapResponse(request.recordIds(), "POST /api/traffic-map/filter");
    }

    /**
     * A GeoJSON FeatureCollection. The extra member {@code meta} (allowed by GeoJSON) tells what the map shows:
     * the {@code latest} records up to a ceiling, or the {@code selection}.
     */
    private Map<String, Object> buildMapResponse(List<UUID> recordIds, String source) {
        TrafficMapService.MapContent content = trafficMapService.findContent(recordIds);
        List<Map<String, Object>> features = content.features().stream().map(this::toFeature).toList();
        log.info("{} - returning {} features (scope={})", source, features.size(), content.scope());

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("scope", content.scope());
        meta.put("limit", content.limit());
        meta.put("count", features.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("type", "FeatureCollection");
        response.put("features", features);
        response.put("meta", meta);
        return response;
    }

    private Map<String, Object> toFeature(TrafficRecordRepository.TrafficMapFeatureView featureView) {
        Map<String, Object> feature = new HashMap<>();
        feature.put("type", "Feature");
        feature.put("properties", Map.of(
                "recordId", featureView.getRecordId(),
                "streetId", featureView.getStreetId(),
                "streetOsmWayId", featureView.getStreetOsmWayId(),
                "streetName", featureView.getStreetName(),
                "vehicleVolume", featureView.getVehicleVolume(),
                "trafficLevel", toTrafficLevel(featureView.getVehicleVolume()),
                "color", toColor(featureView.getVehicleVolume())
        ));

        try {
            feature.put("geometry", objectMapper.readValue(featureView.getGeometry(), GEOMETRY_TYPE));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Geometria invalida para streetId=" + featureView.getStreetId(), ex);
        }

        return feature;
    }

    private String toTrafficLevel(int volume) {
        if (volume <= 100) {
            return "LOW";
        }
        if (volume <= 200) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private String toColor(int volume) {
        if (volume <= 100) {
            return LOW_COLOR;
        }
        if (volume <= 200) {
            return MEDIUM_COLOR;
        }
        return HIGH_COLOR;
    }
}
