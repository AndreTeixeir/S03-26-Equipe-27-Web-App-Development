package com.smarttrafficflow.backend.domain.streets.repository;

import com.smarttrafficflow.backend.domain.streets.entity.Street;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@DisplayName("StreetRepository integration tests")
class StreetRepositoryTests {

    @Autowired
    private StreetRepository streetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedStreets() {
        jdbcTemplate.update("DELETE FROM traffic_records");
        jdbcTemplate.update("DELETE FROM streets");
        insertStreet(101L, "Avenida Central");
        insertStreet(202L, "Rua das Flores");
        insertStreet(303L, "Avenida Paulista");
        insertStreet(404L, "Travessa Azul");
    }

    @Test
    @DisplayName("finds a street by its osm way id")
    void findsStreetByOsmWayId() {
        assertThat(streetRepository.findByOsmWayId(202L))
                .get()
                .extracting(Street::getName)
                .isEqualTo("Rua das Flores");
        assertThat(streetRepository.findByOsmWayId(999L)).isEmpty();
    }

    @Test
    @DisplayName("searches names case-insensitively and orders them by name")
    void searchesNamesCaseInsensitively() {
        List<Street> result = streetRepository.searchByName("AVENIDA", 10, 0);

        assertThat(result).extracting(Street::getName).containsExactly("Avenida Central", "Avenida Paulista");
    }

    @Test
    @DisplayName("applies limit and offset to the ordered search")
    void appliesLimitAndOffset() {
        List<Street> result = streetRepository.searchByName("", 2, 1);

        assertThat(result).extracting(Street::getName).containsExactly("Avenida Paulista", "Rua das Flores");
    }

    @Test
    @DisplayName("counts streets matching the name filter")
    void countsStreetsMatchingFilter() {
        assertThat(streetRepository.countByNameFilter("avenida")).isEqualTo(2);
        assertThat(streetRepository.countByNameFilter("")).isEqualTo(4);
        assertThat(streetRepository.countByNameFilter("inexistente")).isZero();
    }

    @Test
    @DisplayName("returns the osm way id of an existing street at random")
    void returnsRandomExistingOsmWayId() {
        assertThat(streetRepository.findRandomOsmWayId()).isIn(101L, 202L, 303L, 404L);
    }

    @Test
    @DisplayName("returns null as random osm way id when there are no streets")
    void returnsNullRandomOsmWayIdWithoutStreets() {
        jdbcTemplate.update("DELETE FROM streets");

        assertThat(streetRepository.findRandomOsmWayId()).isNull();
    }

    private void insertStreet(long osmWayId, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO streets (id, osm_way_id, name, geom)
                VALUES (?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                """,
                UUID.randomUUID(),
                osmWayId,
                name,
                "{\"type\":\"LineString\",\"coordinates\":[[-46.6333,-23.5505],[-46.6340,-23.5510]]}"
        );
    }
}
