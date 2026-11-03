package com.smarttrafficflow.backend.domain.analytics.service;

import com.smarttrafficflow.backend.api.dto.TrafficStatsResponse;
import com.smarttrafficflow.backend.config.AppTimeZone;
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

    private final TrafficAggregationRepository aggregationRepository;
    private final AppTimeZone timeZone;

    public AnalyticsService(TrafficAggregationRepository aggregationRepository, AppTimeZone timeZone) {
        this.aggregationRepository = aggregationRepository;
        this.timeZone = timeZone;
    }

    public TrafficStatsResponse getStats(String groupBy, List<UUID> recordIds) {
        StatsGroupBy dimension = StatsGroupBy.parse(groupBy);
        Map<String, Long> totals = aggregationRepository.sumVolumeBy(dimension, timeZone.id(), recordIds);

        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        switch (dimension) {
            case HOUR -> {
                for (int hour = 0; hour < 24; hour++) {
                    labels.add(String.format("%02d", hour));
                    values.add(totals.getOrDefault(String.valueOf(hour), 0L));
                }
            }
            case WEEKDAY -> {
                for (DayOfWeek day : DayOfWeek.values()) {
                    labels.add(day.name());
                    values.add(totals.getOrDefault(String.valueOf(day.getValue()), 0L));
                }
            }
            case ROAD_TYPE -> {
                for (String roadType : RoadTypes.inPresentationOrder(totals.keySet())) {
                    labels.add(roadType);
                    values.add(totals.get(roadType));
                }
            }
        }

        log.info("Analytics requested with groupBy={} returned {} buckets", groupBy, labels.size());
        return new TrafficStatsResponse(labels, values);
    }
}
