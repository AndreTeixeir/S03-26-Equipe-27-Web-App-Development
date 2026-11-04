package com.smarttrafficflow.backend.domain.trafficrecords.service;

import com.smarttrafficflow.backend.api.dto.CreateTrafficRecordRequest;
import com.smarttrafficflow.backend.api.dto.PagedTrafficRecordResponse;
import com.smarttrafficflow.backend.api.dto.RecordIdsResponse;
import com.smarttrafficflow.backend.config.RecordLimits;
import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.api.dto.TrafficRecordSummaryResponse;
import com.smarttrafficflow.backend.domain.trafficrecords.entity.TrafficRecord;
import com.smarttrafficflow.backend.domain.trafficrecords.repository.TrafficRecordRepository;
import com.smarttrafficflow.backend.domain.streets.entity.Street;
import com.smarttrafficflow.backend.domain.streets.service.StreetService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TrafficRecordService {

    private static final Logger log = LoggerFactory.getLogger(TrafficRecordService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final TrafficRecordRepository repository;
    private final StreetService streetService;
    private final RecordLimits limits;

    public TrafficRecordService(TrafficRecordRepository repository, StreetService streetService, RecordLimits limits) {
        this.repository = repository;
        this.streetService = streetService;
        this.limits = limits;
    }

    public TrafficRecordResponse create(CreateTrafficRecordRequest request) {
        Street street = streetService.getByOsmWayId(request.streetOsmWayId());

        TrafficRecord record = new TrafficRecord();
        record.setTimestamp(request.timestamp());
        record.setRoadType(request.roadType());
        record.setVehicleVolume(request.vehicleVolume());
        record.setEventType(request.eventType());
        record.setWeather(request.weather());
        record.setStreet(street);

        TrafficRecord saved = repository.save(record);
        log.info("TrafficRecord saved id={}, roadType={}, timestamp={}",
                saved.getId(), saved.getRoadType(), saved.getTimestamp());
        return toResponse(saved);
    }

    /** The {@code limit} most recent records ({@code timestamp} and {@code id} descending). */
    public List<TrafficRecordResponse> findLatest(int limit) {
        List<TrafficRecordResponse> records = repository.findLatestWithStreet(PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
        log.debug("TrafficRecord findLatest({}) returned {} records", limit, records.size());
        return records;
    }

    /** The requested records that exist, most recent first. */
    public List<TrafficRecordResponse> findByIds(List<UUID> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        List<TrafficRecordResponse> records = repository.findAllByIdInWithStreet(recordIds).stream()
                .map(this::toResponse)
                .toList();
        log.debug("TrafficRecord findByIds returned {} records from {} ids", records.size(), recordIds.size());
        return records;
    }

    public long countAll() {
        return repository.count();
    }

    public PagedTrafficRecordResponse findPage(int page, int size, String query) {
        RecordFilter filter = RecordFilter.of(query);
        PageRequest pageRequest = PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.by(
                        Sort.Order.desc("timestamp"),
                        Sort.Order.desc("id")
                )
        );
        Page<TrafficRecord> recordsPage = repository.findPageWithStreet(
                filter.textPattern(), filter.weatherCodes(), filter.roadTypeCodes(), pageRequest);
        List<TrafficRecordResponse> items = recordsPage.getContent().stream().map(this::toResponse).toList();
        log.debug("TrafficRecord findPage returned {} records from total={} for page={}",
                items.size(), recordsPage.getTotalElements(), page);
        return new PagedTrafficRecordResponse(
                items,
                recordsPage.getNumber(),
                recordsPage.getSize(),
                recordsPage.getTotalElements(),
                recordsPage.getTotalPages()
        );
    }

    /**
     * Ids of every record that matches the table filter ("select all filtered"), most recent first, up to the export
     * limit. {@code truncated} tells the caller there were more matches than ids.
     */
    public RecordIdsResponse findFilteredIds(String query) {
        int limit = limits.exportMaxRecords();
        RecordFilter filter = RecordFilter.of(query);
        Page<UUID> ids = repository.findIdsByFilter(
                filter.textPattern(), filter.weatherCodes(), filter.roadTypeCodes(), PageRequest.of(0, limit));
        boolean truncated = ids.getTotalElements() > ids.getNumberOfElements();
        log.debug("TrafficRecord findFilteredIds returned {} ids of {} matches (limit {})",
                ids.getNumberOfElements(), ids.getTotalElements(), limit);
        return new RecordIdsResponse(ids.getContent(), ids.getTotalElements(), limit, truncated);
    }

    public TrafficRecordSummaryResponse findSummary(List<UUID> recordIds) {
        TrafficRecordRepository.TrafficRecordSummaryView summaryView =
                recordIds == null || recordIds.isEmpty()
                        ? repository.summarizeAll()
                        : repository.summarizeByIds(recordIds);
        long recordCount = summaryView.getRecordCount();
        long totalVehicleVolume = summaryView.getTotalVehicleVolume();
        int averageVehicleVolume = recordCount == 0 ? 0 : (int) Math.round((double) totalVehicleVolume / recordCount);
        return new TrafficRecordSummaryResponse(
                recordCount,
                totalVehicleVolume,
                summaryView.getUniqueStreetCount(),
                averageVehicleVolume,
                summaryView.getLatestTimestamp()
        );
    }

    public List<TrafficRecordRepository.TrafficMapFeatureView> findMapFeaturesByRecordIds(List<UUID> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        return repository.findMapFeaturesByRecordIds(recordIds);
    }

    public List<TrafficRecordRepository.TrafficMapFeatureView> findLatestMapFeatures(int limit) {
        return repository.findLatestMapFeatures(limit);
    }

    private TrafficRecordResponse toResponse(TrafficRecord record) {
        return new TrafficRecordResponse(
                record.getId(),
                record.getTimestamp(),
                record.getRoadType(),
                record.getVehicleVolume(),
                record.getEventType(),
                record.getWeather(),
                record.getStreet().getId(),
                record.getStreet().getOsmWayId(),
                record.getStreet().getName()
        );
    }
}
