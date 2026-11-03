package com.smarttrafficflow.backend.domain.exports.service;

import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final TrafficRecordService trafficRecordService;

    public ExportService(TrafficRecordService trafficRecordService) {
        this.trafficRecordService = trafficRecordService;
    }

    public String exportCsv() {
        List<TrafficRecordResponse> records = trafficRecordService.findAll();
        log.info("Exporting {} records to CSV", records.size());
        StringBuilder sb = new StringBuilder(CsvFormat.BYTE_ORDER_MARK);
        sb.append("id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName\n");

        for (TrafficRecordResponse record : records) {
            sb.append(record.id()).append(',')
                    .append(record.timestamp()).append(',')
                    .append(CsvFormat.text(record.roadType())).append(',')
                    .append(record.vehicleVolume()).append(',')
                    .append(CsvFormat.text(record.eventType())).append(',')
                    .append(CsvFormat.text(record.weather())).append(',')
                    .append(record.streetId()).append(',')
                    .append(record.streetOsmWayId()).append(',')
                    .append(CsvFormat.text(record.streetName()))
                    .append('\n');
        }

        String csv = sb.toString();
        log.debug("CSV export built with {} characters", csv.length());
        return csv;
    }
}
