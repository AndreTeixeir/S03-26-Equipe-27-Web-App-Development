package com.smarttrafficflow.backend.domain.trafficrecords.repository;

import com.smarttrafficflow.backend.domain.analytics.service.StatsGroupBy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sums the vehicle volume per bucket straight in the database, so the application never loads every record.
 * <p>
 * The bucket expressions are constants chosen from {@link StatsGroupBy}; user input only travels as bound
 * parameters. The time zone is a parameter of the query ({@code timestamp AT TIME ZONE :zone}), because the hour
 * and the weekday of a record depend on where it is observed.
 */
@Repository
public class TrafficAggregationRepository {

    private static final String BLANK_ROAD_TYPE = "'^\\s*$'";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TrafficAggregationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Total vehicle volume per bucket, ordered by bucket.
     * <ul>
     *   <li>{@link StatsGroupBy#HOUR}: key {@code "0".."23"}</li>
     *   <li>{@link StatsGroupBy#WEEKDAY}: key {@code "1".."7"} (ISO: 1 = Monday)</li>
     *   <li>{@link StatsGroupBy#ROAD_TYPE}: the road type, {@code "UNKNOWN"} when blank</li>
     * </ul>
     * Only buckets that have records are returned. A {@code null} or empty {@code recordIds} means every record.
     */
    public Map<String, Long> sumVolumeBy(StatsGroupBy groupBy, String zone, List<UUID> recordIds) {
        boolean filtered = recordIds != null && !recordIds.isEmpty();
        String sql = "SELECT " + bucketExpression(groupBy) + " AS bucket, SUM(tr.vehicle_volume) AS total "
                + "FROM traffic_records tr "
                + (filtered ? "WHERE tr.id IN (:ids) " : "")
                + "GROUP BY 1 ORDER BY 1";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (groupBy != StatsGroupBy.ROAD_TYPE) {
            parameters.addValue("zone", zone);
        }
        if (filtered) {
            parameters.addValue("ids", recordIds);
        }

        Map<String, Long> totals = new LinkedHashMap<>();
        jdbcTemplate.query(sql, parameters, resultSet -> {
            totals.put(resultSet.getString("bucket"), resultSet.getLong("total"));
        });
        return totals;
    }

    private static String bucketExpression(StatsGroupBy groupBy) {
        return switch (groupBy) {
            case HOUR -> "CAST(EXTRACT(HOUR FROM tr.timestamp AT TIME ZONE :zone) AS integer)";
            case WEEKDAY -> "CAST(EXTRACT(ISODOW FROM tr.timestamp AT TIME ZONE :zone) AS integer)";
            case ROAD_TYPE -> "CASE WHEN tr.road_type ~ " + BLANK_ROAD_TYPE
                    + " THEN 'UNKNOWN' ELSE tr.road_type END";
        };
    }
}
