package com.smarttrafficflow.backend.domain.simulations.service;

import com.smarttrafficflow.backend.api.dto.CreateTrafficRecordRequest;
import com.smarttrafficflow.backend.api.dto.SimulationRequest;
import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.domain.streets.service.StreetService;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private static final String[] ROAD_TYPES = {"LOCAL", "ARTERIAL", "HIGHWAY"};
    private static final String[] WEATHER_TYPES = {"SUNNY", "RAIN", "CLOUDY"};

    private final TrafficRecordService trafficRecordService;
    private final StreetService streetService;
    private final Random random = new Random();

    public SimulationService(TrafficRecordService trafficRecordService, StreetService streetService) {
        this.trafficRecordService = trafficRecordService;
        this.streetService = streetService;
    }

    public List<TrafficRecordResponse> generate(SimulationRequest request) {
        List<TrafficRecordResponse> generated = new ArrayList<>();
        log.info("Simulation started for scenario={} with {} records",
                request.scenarioName(), request.recordsToGenerate());

        List<Long> streetOsmWayIds = streetService.getRandomStreetOsmWayIds(request.recordsToGenerate());

        for (int i = 0; i < request.recordsToGenerate(); i++) {
            String roadType = ROAD_TYPES[random.nextInt(ROAD_TYPES.length)];
            String weather = WEATHER_TYPES[random.nextInt(WEATHER_TYPES.length)];
            int volume = 20 + random.nextInt(281);
            OffsetDateTime timestamp = OffsetDateTime.now().minusHours(random.nextInt(168));

            CreateTrafficRecordRequest createRequest = new CreateTrafficRecordRequest(
                    timestamp,
                    roadType,
                    volume,
                    request.scenarioName(),
                    weather,
                    streetOsmWayIds.get(random.nextInt(streetOsmWayIds.size()))
            );

            generated.add(trafficRecordService.create(createRequest));
        }

        log.info("Simulation finished for scenario={} with {} persisted records",
                request.scenarioName(), generated.size());
        return generated;
    }
}
