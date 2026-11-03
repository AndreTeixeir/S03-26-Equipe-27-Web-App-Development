package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.CreateTrafficRecordRequest;
import com.smarttrafficflow.backend.api.dto.PagedTrafficRecordResponse;
import com.smarttrafficflow.backend.api.dto.RecordIdsFilterRequest;
import com.smarttrafficflow.backend.api.dto.RecordIdsResponse;
import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.api.dto.TrafficRecordSummaryResponse;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/traffic-records")
public class TrafficRecordController {

    private static final Logger log = LoggerFactory.getLogger(TrafficRecordController.class);

    private final TrafficRecordService trafficRecordService;

    public TrafficRecordController(TrafficRecordService trafficRecordService) {
        this.trafficRecordService = trafficRecordService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrafficRecordResponse create(@Valid @RequestBody CreateTrafficRecordRequest request) {
        log.info("POST /api/traffic-records - creating traffic record for roadType={}, vehicleVolume={}",
                request.roadType(), request.vehicleVolume());
        TrafficRecordResponse response = trafficRecordService.create(request);
        log.info("POST /api/traffic-records - traffic record created with id={}", response.id());
        return response;
    }

    @GetMapping
    public PagedTrafficRecordResponse findPage(
            // 10 million pages of at most 100 records stay far below the Integer.MAX_VALUE limit of the page offset.
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "deve ser maior ou igual a 0")
            @Max(value = 10_000_000, message = "deve ser menor ou igual a 10000000") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "deve ser maior ou igual a 1") int size,
            @RequestParam(required = false) String query
    ) {
        PagedTrafficRecordResponse recordsPage = trafficRecordService.findPage(page, size, query);
        log.info("GET /api/traffic-records - returning page={} size={} totalItems={}",
                recordsPage.page(), recordsPage.size(), recordsPage.totalItems());
        return recordsPage;
    }

    /** Ids of every record that matches the table filter, in one request ("select all filtered"). */
    @GetMapping("/ids")
    public RecordIdsResponse findFilteredIds(@RequestParam(required = false) String query) {
        RecordIdsResponse response = trafficRecordService.findFilteredIds(query);
        log.info("GET /api/traffic-records/ids - returning {} ids of {} matches (truncated={})",
                response.recordIds().size(), response.total(), response.truncated());
        return response;
    }

    @GetMapping("/summary")
    public TrafficRecordSummaryResponse findSummary(@RequestParam(required = false) List<UUID> recordIds) {
        int filterSize = recordIds == null ? 0 : recordIds.size();
        log.info("GET /api/traffic-records/summary - summarizing {} selected recordIds", filterSize);
        return trafficRecordService.findSummary(recordIds);
    }

    @PostMapping("/summary/filter")
    public TrafficRecordSummaryResponse findFilteredSummary(@RequestBody RecordIdsFilterRequest request) {
        int filterSize = request.recordIds() == null ? 0 : request.recordIds().size();
        log.info("POST /api/traffic-records/summary/filter - summarizing {} selected recordIds", filterSize);
        return trafficRecordService.findSummary(request.recordIds());
    }
}
