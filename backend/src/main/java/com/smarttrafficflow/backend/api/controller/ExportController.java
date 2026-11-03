package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.domain.exports.service.ExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exports")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    public ResponseEntity<?> export(@RequestParam String format) {
        log.info("GET /api/exports - requested format={}", format);
        if ("csv".equalsIgnoreCase(format)) {
            String csv = exportService.exportCsv();
            log.info("GET /api/exports - csv generated with {} characters", csv.length());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=traffic-data.csv")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(csv);
        }

        if ("json".equalsIgnoreCase(format)) {
            List<Map<String, Object>> payload = List.of(
                    Map.of("status", "ok", "message", "Use /api/traffic-records para obter JSON completo")
            );
            log.info("GET /api/exports - returning json export guidance payload");
            return ResponseEntity.ok(payload);
        }

        log.warn("GET /api/exports - invalid format={}", format);
        throw new IllegalArgumentException("Formato de exportacao invalido: " + format);
    }
}
