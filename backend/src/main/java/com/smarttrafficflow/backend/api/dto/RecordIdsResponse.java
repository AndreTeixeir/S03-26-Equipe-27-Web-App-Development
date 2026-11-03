package com.smarttrafficflow.backend.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Ids of the records that match the table filter, most recent first.
 *
 * @param recordIds the ids, up to {@code limit}
 * @param total     how many records match the filter
 * @param limit     the ceiling applied to {@code recordIds}
 * @param truncated whether there are more matches than the ids returned
 */
public record RecordIdsResponse(
        List<UUID> recordIds,
        long total,
        int limit,
        boolean truncated
) {
}
