package com.smarttrafficflow.backend.domain.insights.service;

import com.smarttrafficflow.backend.api.dto.TrafficInsightResponse;
import com.smarttrafficflow.backend.domain.trafficrecords.entity.TrafficRecord;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);

    private final TrafficRecordService trafficRecordService;

    public InsightService(TrafficRecordService trafficRecordService) {
        this.trafficRecordService = trafficRecordService;
    }

    public TrafficInsightResponse generateInsights(List<UUID> recordIds) {
        List<TrafficRecord> records = (recordIds == null || recordIds.isEmpty())
                ? trafficRecordService.findAllEntities()
                : trafficRecordService.findEntitiesByIds(recordIds);
        List<String> insights = new ArrayList<>();
        log.info("Generating insights from {} records", records.size());

        if (records.isEmpty()) {
            insights.add("Sem dados suficientes para gerar insights.");
            log.warn("Insight generation requested without data");
            return new TrafficInsightResponse(insights);
        }

        Map<Integer, Long> byHour = new HashMap<>();
        Map<String, Long> byRoadType = new HashMap<>();

        for (TrafficRecord record : records) {
            long volume = record.getVehicleVolume().longValue();
            byHour.merge(record.getTimestamp().getHour(), volume, Long::sum);
            byRoadType.merge(safeValue(record.getRoadType(), "UNKNOWN"), volume, Long::sum);
        }

        Map.Entry<Integer, Long> peakHour = byHour.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        Map.Entry<String, Long> peakRoadType = byRoadType.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue))
                .orElseThrow();

        insights.add("Horario com maior volume agregado: " + peakHour.getKey() + "h.");
        insights.add("Tipo de via com maior volume agregado: " + peakRoadType.getKey() + ".");

        log.info("Generated {} insights (peakHour={}h, peakRoadType={})",
                insights.size(), peakHour.getKey(), peakRoadType.getKey());
        return new TrafficInsightResponse(insights);
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
