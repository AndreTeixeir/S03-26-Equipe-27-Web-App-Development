package com.smarttrafficflow.backend.domain.map.service;

import com.smarttrafficflow.backend.config.RecordLimits;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficRecordRepository;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TrafficMapService {

    public static final String SCOPE_LATEST = "latest";
    public static final String SCOPE_SELECTION = "selection";

    private static final Logger log = LoggerFactory.getLogger(TrafficMapService.class);

    private final TrafficRecordService trafficRecordService;
    private final RecordLimits limits;

    public TrafficMapService(TrafficRecordService trafficRecordService, RecordLimits limits) {
        this.trafficRecordService = trafficRecordService;
        this.limits = limits;
    }

    /**
     * What the map shows. With ids: the selected records, with no ceiling. Without ids: the most recent records, up
     * to the map ceiling, so the map is never empty on a base that has data.
     */
    public MapContent findContent(List<UUID> recordIds) {
        if (recordIds != null && !recordIds.isEmpty()) {
            List<TrafficRecordRepository.TrafficMapFeatureView> selected =
                    trafficRecordService.findMapFeaturesByRecordIds(recordIds);
            log.info("Map shows {} features from {} selected records", selected.size(), recordIds.size());
            return new MapContent(SCOPE_SELECTION, null, selected);
        }

        int limit = limits.mapMaxRecords();
        List<TrafficRecordRepository.TrafficMapFeatureView> latest = trafficRecordService.findLatestMapFeatures(limit);
        log.info("Map shows the {} most recent features (ceiling {})", latest.size(), limit);
        return new MapContent(SCOPE_LATEST, limit, latest);
    }

    /**
     * @param scope    {@code latest} or {@code selection}
     * @param limit    the ceiling applied, or {@code null} for a selection (which has none)
     * @param features the street features, one per record
     */
    public record MapContent(String scope, Integer limit, List<TrafficRecordRepository.TrafficMapFeatureView> features) {
    }
}
