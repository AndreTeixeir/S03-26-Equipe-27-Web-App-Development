package com.smarttrafficflow.backend.domain.analytics.service;

import com.smarttrafficflow.backend.api.dto.TrafficStatsResponse;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficAggregationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    /** Time zone in which hours and weekdays are observed. */
    private static final String ZONE = "UTC";

    private final TrafficAggregationRepository aggregationRepository;

    public AnalyticsService(TrafficAggregationRepository aggregationRepository) {
        this.aggregationRepository = aggregationRepository;
    }

    public TrafficStatsResponse getStats(String groupBy, List<UUID> recordIds) {
        StatsGroupBy dimension = StatsGroupBy.parse(groupBy);
        Map<String, Long> totals = aggregationRepository.sumVolumeBy(dimension, ZONE, recordIds);
        log.info("Analytics requested with groupBy={} produced {} buckets", groupBy, totals.size());

        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        totals.forEach((bucket, total) -> {
            labels.add(toLabel(dimension, bucket));
            values.add(total);
        });
        return new TrafficStatsResponse(labels, values);
    }

    private String toLabel(StatsGroupBy dimension, String bucket) {
        return switch (dimension) {
            case HOUR -> String.format("%02d", Integer.parseInt(bucket));
            case WEEKDAY -> DayOfWeek.of(Integer.parseInt(bucket)).name();
            case ROAD_TYPE -> bucket;
        };
    }
}
