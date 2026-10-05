package com.smarttrafficflow.backend.domain.streets.service;

import com.smarttrafficflow.backend.api.dto.StreetSearchResponse;
import com.smarttrafficflow.backend.api.dto.StreetResponse;
import com.smarttrafficflow.backend.domain.streets.entity.Street;
import com.smarttrafficflow.backend.domain.streets.repository.StreetRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StreetService {

    private final StreetRepository streetRepository;
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    public StreetService(StreetRepository streetRepository) {
        this.streetRepository = streetRepository;
    }

    public Street getByOsmWayId(Long osmWayId) {
        return streetRepository.findByOsmWayId(osmWayId)
                .orElseThrow(() -> new IllegalArgumentException("streetOsmWayId invalido: " + osmWayId));
    }

    public StreetSearchResponse search(String query, Integer limit, Integer offset) {
        String normalizedQuery = query == null ? "" : query.trim();
        int normalizedLimit = normalizeLimit(limit);
        int normalizedOffset = normalizeOffset(offset);

        List<StreetResponse> items = streetRepository.searchByName(normalizedQuery, normalizedLimit, normalizedOffset)
                .stream()
                .map(street -> new StreetResponse(street.getId(), street.getOsmWayId(), street.getName()))
                .toList();
        long total = streetRepository.countByNameFilter(normalizedQuery);

        return new StreetSearchResponse(items, normalizedLimit, normalizedOffset, total);
    }

    public List<Long> getRandomStreetOsmWayIds(int count) {
        List<Long> osmWayIds = streetRepository.findRandomOsmWayIds(count);
        if (osmWayIds.isEmpty()) {
            throw new IllegalStateException("Nenhuma rua real importada. Importe GeoJSON antes de simular.");
        }
        return osmWayIds;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeOffset(Integer offset) {
        if (offset == null || offset < 0) {
            return 0;
        }
        return offset;
    }
}
