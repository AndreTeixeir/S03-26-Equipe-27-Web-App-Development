package com.smarttrafficflow.backend.domain.trafficrecords.service;

import com.smarttrafficflow.backend.api.dto.CreateTrafficRecordRequest;
import com.smarttrafficflow.backend.api.dto.PagedTrafficRecordResponse;
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

    public TrafficRecordService(TrafficRecordRepository repository, StreetService streetService) {
        this.repository = repository;
        this.streetService = streetService;
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

    public List<TrafficRecordResponse> findAll() {
        List<TrafficRecordResponse> records = repository.findAllWithStreet().stream().map(this::toResponse).toList();
        log.debug("TrafficRecord findAll returned {} records", records.size());
        return records;
    }

    public PagedTrafficRecordResponse findPage(int page, int size, String query) {
        String queryPattern = buildQueryPattern(query);
        PageRequest pageRequest = PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.by(
                        Sort.Order.desc("timestamp"),
                        Sort.Order.desc("id")
                )
        );
        Page<TrafficRecord> recordsPage = repository.findPageWithStreet(queryPattern, pageRequest);
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

    public List<TrafficRecord> findAllEntities() {
        List<TrafficRecord> records = repository.findAllWithStreet();
        log.debug("TrafficRecord findAllEntities returned {} records", records.size());
        return records;
    }

    public List<TrafficRecord> findEntitiesByIds(List<UUID> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }

        List<TrafficRecord> records = repository.findAllByIdInWithStreet(recordIds);
        log.debug("TrafficRecord findEntitiesByIds returned {} records from {} ids", records.size(), recordIds.size());
        return records;
    }

    public List<TrafficRecordRepository.TrafficMapFeatureView> findMapFeaturesByRecordIds(List<UUID> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        return repository.findMapFeaturesByRecordIds(recordIds);
    }

    private String buildQueryPattern(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : "%" + trimmed.toLowerCase() + "%";
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
