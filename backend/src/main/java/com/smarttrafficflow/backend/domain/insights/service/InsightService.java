package com.smarttrafficflow.backend.domain.insights.service;

import com.smarttrafficflow.backend.api.dto.TrafficInsightResponse;
import com.smarttrafficflow.backend.config.AppTimeZone;
import com.smarttrafficflow.backend.domain.analytics.service.RoadTypes;
import com.smarttrafficflow.backend.domain.analytics.service.StatsGroupBy;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficAggregationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);

    private final TrafficAggregationRepository aggregationRepository;
    private final AppTimeZone timeZone;

    public InsightService(TrafficAggregationRepository aggregationRepository, AppTimeZone timeZone) {
        this.aggregationRepository = aggregationRepository;
        this.timeZone = timeZone;
    }

    public TrafficInsightResponse generateInsights(List<UUID> recordIds) {
        Map<String, Long> byHour = aggregationRepository.sumVolumeBy(StatsGroupBy.HOUR, timeZone.id(), recordIds);
        List<String> insights = new ArrayList<>();

        Map<String, Long> byRoadType = byHour.isEmpty()
                ? Map.of()
                : aggregationRepository.sumVolumeBy(StatsGroupBy.ROAD_TYPE, timeZone.id(), recordIds);

        // Both queries read the same records, so they are empty together. If they ever disagree, say there is no data
        // instead of failing on a missing road type.
        if (byHour.isEmpty() || byRoadType.isEmpty()) {
            insights.add("Sem dados suficientes para gerar insights.");
            log.warn("Insight generation requested without data");
            return new TrafficInsightResponse(insights);
        }

        // Ties keep the first candidate in the presentation order, so the same data gives the same text.
        int peakHour = -1;
        for (int hour = 0; hour < 24; hour++) {
            Long total = byHour.get(String.valueOf(hour));
            if (total != null && (peakHour < 0 || total > byHour.get(String.valueOf(peakHour)))) {
                peakHour = hour;
            }
        }

        String peakRoadType = null;
        for (String roadType : RoadTypes.inPresentationOrder(byRoadType.keySet())) {
            if (peakRoadType == null || byRoadType.get(roadType) > byRoadType.get(peakRoadType)) {
                peakRoadType = roadType;
            }
        }

        insights.add("Horário de pico: " + peakHour + "h.");
        insights.add("Tipo de via com maior volume: " + RoadTypes.label(peakRoadType) + ".");

        log.info("Generated {} insights (peakHour={}h, peakRoadType={})", insights.size(), peakHour, peakRoadType);
        return new TrafficInsightResponse(insights);
    }
}
