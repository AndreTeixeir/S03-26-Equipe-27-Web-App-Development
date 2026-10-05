package com.smarttrafficflow.backend.domain.streets.repository;

import com.smarttrafficflow.backend.domain.streets.entity.Street;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StreetRepository extends JpaRepository<Street, UUID> {
    Optional<Street> findByOsmWayId(Long osmWayId);

    @Query(value = """
            SELECT *
            FROM streets s
            WHERE (:query = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY s.name ASC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<Street> searchByName(@Param("query") String query, @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(*)
            FROM streets s
            WHERE (:query = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')))
            """, nativeQuery = true)
    long countByNameFilter(@Param("query") String query);

    @Query(value = "SELECT osm_way_id FROM streets ORDER BY random() LIMIT :limit", nativeQuery = true)
    List<Long> findRandomOsmWayIds(@Param("limit") int limit);
}
