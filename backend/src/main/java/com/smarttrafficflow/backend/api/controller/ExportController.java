package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.RecordIdsFilterRequest;
import com.smarttrafficflow.backend.domain.exports.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    public static final String TOTAL_RECORDS_HEADER = "X-Total-Records";
    public static final String EXPORTED_RECORDS_HEADER = "X-Exported-Records";

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);
    private static final MediaType CSV_UTF8 = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /** Exports the most recent records of the whole base, up to the export limit. */
    @GetMapping
    public ResponseEntity<?> export(@RequestParam String format) {
        log.info("GET /api/exports - requested format={}", format);
        return respond(format, null);
    }

    /** Exports the selected records. The ids travel in the body because a large selection does not fit in a URL. */
    @PostMapping("/filter")
    public ResponseEntity<?> exportSelection(@RequestParam String format, @RequestBody RecordIdsFilterRequest request) {
        int selected = request.recordIds() == null ? 0 : request.recordIds().size();
        log.info("POST /api/exports/filter - requested format={} with {} selected recordIds", format, selected);
        return respond(format, request.recordIds());
    }

    private ResponseEntity<?> respond(String format, List<UUID> recordIds) {
        boolean csv = "csv".equalsIgnoreCase(format);
        boolean json = "json".equalsIgnoreCase(format);
        if (!csv && !json) {
            log.warn("Export requested with invalid format={}", format);
            throw new IllegalArgumentException("Formato de exportacao invalido: " + format);
        }

        ExportService.ExportResult result = exportService.export(recordIds);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(TOTAL_RECORDS_HEADER, String.valueOf(result.total()))
                .header(EXPORTED_RECORDS_HEADER, String.valueOf(result.records().size()));

        if (csv) {
            String body = exportService.toCsv(result.records());
            log.info("Export - csv generated with {} characters", body.length());
            return response
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=traffic-data.csv")
                    .contentType(CSV_UTF8)
                    .body(body);
        }

        log.info("Export - json generated with {} records", result.records().size());
        return response
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=traffic-data.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.records());
    }
}
