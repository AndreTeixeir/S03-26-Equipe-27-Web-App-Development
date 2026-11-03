package com.smarttrafficflow.backend.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Inserts streets and traffic records straight into the test database, with the SQL the application schema expects.
 */
public class TrafficTestData {

    private static final String GEOMETRY =
            "{\"type\":\"LineString\",\"coordinates\":[[-46.6333,-23.5505],[-46.6340,-23.5510]]}";

    private final JdbcTemplate jdbcTemplate;

    public TrafficTestData(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clear() {
        jdbcTemplate.update("DELETE FROM traffic_records");
        jdbcTemplate.update("DELETE FROM streets");
    }

    public UUID insertStreet(long osmWayId, String name) {
        return insertStreet(osmWayId, name, GEOMETRY);
    }

    public UUID insertStreet(long osmWayId, String name, String geometryJson) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO streets (id, osm_way_id, name, geom)
                VALUES (?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                """,
                id, osmWayId, name, geometryJson);
        return id;
    }

    public UUID insertRecord(UUID streetId, String timestamp, String roadType, int vehicleVolume) {
        return insertRecord(streetId, timestamp, roadType, vehicleVolume, null, null);
    }

    public UUID insertRecord(UUID streetId, String timestamp, String roadType, int vehicleVolume,
                             String eventType, String weather) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO traffic_records (id, timestamp, road_type, vehicle_volume, event_type, weather, street_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, OffsetDateTime.parse(timestamp), roadType, vehicleVolume, eventType, weather, streetId);
        return id;
    }
}
