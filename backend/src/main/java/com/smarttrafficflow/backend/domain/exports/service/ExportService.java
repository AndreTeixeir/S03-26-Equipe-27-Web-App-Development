package com.smarttrafficflow.backend.domain.exports.service;

import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.config.RecordLimits;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final TrafficRecordService trafficRecordService;
    private final RecordLimits limits;

    public ExportService(TrafficRecordService trafficRecordService, RecordLimits limits) {
        this.trafficRecordService = trafficRecordService;
        this.limits = limits;
    }

    /**
     * The records to export, most recent first.
     * <ul>
     *   <li>With ids: the selected records. A selection larger than the export limit is rejected.</li>
     *   <li>Without ids: the most recent records, up to the export limit. {@code total} tells how many exist,
     *       so the caller can warn when the file was cut.</li>
     * </ul>
     */
    public ExportResult export(List<UUID> recordIds) {
        int max = limits.exportMaxRecords();

        if (recordIds != null && !recordIds.isEmpty()) {
            List<UUID> distinctIds = recordIds.stream().distinct().toList();
            if (distinctIds.size() > max) {
                throw new IllegalArgumentException("A seleção tem " + distinctIds.size()
                        + " registros e o limite por exportação é " + max + ". Reduza a seleção.");
            }
            List<TrafficRecordResponse> selected = trafficRecordService.findByIds(distinctIds);
            log.info("Exporting {} selected records", selected.size());
            return new ExportResult(selected, selected.size());
        }

        long total = trafficRecordService.countAll();
        List<TrafficRecordResponse> latest = trafficRecordService.findLatest(max);
        log.info("Exporting the {} most recent of {} records", latest.size(), total);
        return new ExportResult(latest, total);
    }

    public String toCsv(List<TrafficRecordResponse> records) {
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

    /** Records to export and how many records exist in the same context (the file is cut when it is larger). */
    public record ExportResult(List<TrafficRecordResponse> records, long total) {
    }
}
