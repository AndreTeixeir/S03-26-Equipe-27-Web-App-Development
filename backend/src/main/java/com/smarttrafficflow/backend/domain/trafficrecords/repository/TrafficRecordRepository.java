package com.smarttrafficflow.backend.domain.trafficrecords.repository;

import com.smarttrafficflow.backend.domain.trafficrecords.entity.TrafficRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TrafficRecordRepository extends JpaRepository<TrafficRecord, UUID> {

    /** The most recent records first ({@code timestamp} and {@code id} descending), as many as the page asks. */
    @Query("select tr from TrafficRecord tr join fetch tr.street order by tr.timestamp desc, tr.id desc")
    List<TrafficRecord> findLatestWithStreet(Pageable pageable);

    /**
     * The table filter ("Filtro"), written once for the page, the ids and their counts, so that all four find the same
     * records. A record matches when the text pattern is in its road type, street name, weather or event, or when its
     * weather or road type is one of the codes whose name on the screen contains the term (see {@code RecordFilter}).
     * The codes are compared in upper case, because free text keeps the case it was typed with and the screen
     * shows it with the same name. An empty list of codes matches nothing ({@code in ()} becomes {@code 1=0}), so a
     * term that is no name on the screen leaves the result of the text search as it is.
     */
    String MATCHES_FILTER = """
            (
                :queryPattern is null
                or lower(tr.roadType) like :queryPattern
                or lower(coalesce(st.name, '')) like :queryPattern
                or lower(coalesce(tr.weather, '')) like :queryPattern
                or lower(coalesce(tr.eventType, '')) like :queryPattern
                or upper(tr.weather) in :weatherCodes
                or upper(tr.roadType) in :roadTypeCodes
            )
            """;

    @Query(
            value = "select tr from TrafficRecord tr join fetch tr.street st where " + MATCHES_FILTER,
            countQuery = "select count(tr) from TrafficRecord tr join tr.street st where " + MATCHES_FILTER
    )
    Page<TrafficRecord> findPageWithStreet(
            @Param("queryPattern") String queryPattern,
            @Param("weatherCodes") Collection<String> weatherCodes,
            @Param("roadTypeCodes") Collection<String> roadTypeCodes,
            Pageable pageable);

    /**
     * Ids of the records that match the same filter as {@link #findPageWithStreet}, most recent first. It selects
     * only the ids, so "select all filtered" does not load thousands of entities.
     */
    @Query(
            value = "select tr.id from TrafficRecord tr join tr.street st where " + MATCHES_FILTER
                    + " order by tr.timestamp desc, tr.id desc",
            countQuery = "select count(tr) from TrafficRecord tr join tr.street st where " + MATCHES_FILTER
    )
    Page<UUID> findIdsByFilter(
            @Param("queryPattern") String queryPattern,
            @Param("weatherCodes") Collection<String> weatherCodes,
            @Param("roadTypeCodes") Collection<String> roadTypeCodes,
            Pageable pageable);

    @Query("""
            select tr from TrafficRecord tr join fetch tr.street
            where tr.id in :ids
            order by tr.timestamp desc, tr.id desc
            """)
    List<TrafficRecord> findAllByIdInWithStreet(@Param("ids") Collection<UUID> ids);

    @Query("""
            select
                count(tr) as recordCount,
                coalesce(sum(tr.vehicleVolume), 0) as totalVehicleVolume,
                count(distinct st.id) as uniqueStreetCount,
                max(tr.timestamp) as latestTimestamp
            from TrafficRecord tr
            join tr.street st
            """)
    TrafficRecordSummaryView summarizeAll();

    @Query("""
            select
                count(tr) as recordCount,
                coalesce(sum(tr.vehicleVolume), 0) as totalVehicleVolume,
                count(distinct st.id) as uniqueStreetCount,
                max(tr.timestamp) as latestTimestamp
            from TrafficRecord tr
            join tr.street st
            where tr.id in :ids
            """)
    TrafficRecordSummaryView summarizeByIds(@Param("ids") Collection<UUID> ids);

    @Query(
            value = """
                    SELECT
                        tr.id AS recordId,
                        s.id AS streetId,
                        s.osm_way_id AS streetOsmWayId,
                        s.name AS streetName,
                        tr.vehicle_volume AS vehicleVolume,
                        ST_AsGeoJSON(s.geom) AS geometry
                    FROM traffic_records tr
                    JOIN streets s ON s.id = tr.street_id
                    WHERE tr.id IN (:ids)
                    """,
            nativeQuery = true
    )
    List<TrafficMapFeatureView> findMapFeaturesByRecordIds(@Param("ids") Collection<UUID> ids);

    /** The map features of the {@code limit} most recent records ({@code timestamp} and {@code id} descending). */
    @Query(
            value = """
                    SELECT
                        tr.id AS recordId,
                        s.id AS streetId,
                        s.osm_way_id AS streetOsmWayId,
                        s.name AS streetName,
                        tr.vehicle_volume AS vehicleVolume,
                        ST_AsGeoJSON(s.geom) AS geometry
                    FROM traffic_records tr
                    JOIN streets s ON s.id = tr.street_id
                    ORDER BY tr.timestamp DESC, tr.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<TrafficMapFeatureView> findLatestMapFeatures(@Param("limit") int limit);

    interface TrafficMapFeatureView {
        UUID getRecordId();

        UUID getStreetId();

        Long getStreetOsmWayId();

        String getStreetName();

        Integer getVehicleVolume();

        String getGeometry();
    }

    interface TrafficRecordSummaryView {
        long getRecordCount();

        long getTotalVehicleVolume();

        long getUniqueStreetCount();

        OffsetDateTime getLatestTimestamp();
    }
}
