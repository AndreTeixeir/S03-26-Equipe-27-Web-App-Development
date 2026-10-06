package com.smarttrafficflow.backend.domain.streets.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The runner commits its own work, so these tests run outside the test-managed transaction.
 * They only run against an empty streets table and remove only the streets they create,
 * identified by a reserved range of OSM way ids.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("StreetGeoJsonImportRunner integration tests")
class StreetGeoJsonImportRunnerTests {

    private static final long TEST_OSM_ID_START = 990_000_000L;
    private static final long TEST_OSM_ID_END = 990_999_999L;
    private static final String VALID_LINE = "[[-46.6333,-23.5505],[-46.6340,-23.5510]]";
    private static final String LINE_WITH_Z = "[[-46.6333,-23.5505,10],[-46.6340,-23.5510,10]]";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    private Path tempDir;

    @BeforeEach
    void requireEmptyStreetsTable() {
        Integer streets = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM streets", Integer.class);
        assumeTrue(streets != null && streets == 0,
                "These tests need an empty streets table; they never delete streets they did not create");
    }

    @AfterEach
    void removeTestStreets() {
        jdbcTemplate.update("DELETE FROM streets WHERE osm_way_id BETWEEN ? AND ?", TEST_OSM_ID_START, TEST_OSM_ID_END);
    }

    @Test
    @DisplayName("leaves no streets behind when the import fails midway")
    void leavesNoStreetsWhenImportFails() throws Exception {
        Path file = geoJson(
                feature(990_000_001L, "Rua Valida Um", VALID_LINE),
                feature(990_000_002L, "Rua Valida Dois", VALID_LINE),
                feature(990_000_003L, "Rua Com Altitude", LINE_WITH_Z)
        );

        assertThatThrownBy(() -> runner(file).run()).isInstanceOf(Exception.class);

        assertThat(countStreets()).isZero();
    }

    @Test
    @DisplayName("imports normally on the next start after a failed import")
    void importsOnTheNextStartAfterFailure() throws Exception {
        Path broken = geoJson(
                feature(990_000_001L, "Rua Valida Um", VALID_LINE),
                feature(990_000_003L, "Rua Com Altitude", LINE_WITH_Z)
        );
        assertThatThrownBy(() -> runner(broken).run()).isInstanceOf(Exception.class);

        Path fixed = geoJson(
                feature(990_000_001L, "Rua Valida Um", VALID_LINE),
                feature(990_000_002L, "Rua Valida Dois", VALID_LINE)
        );
        runner(fixed).run();

        assertThat(countStreets()).isEqualTo(2);
    }

    @Test
    @DisplayName("skips streets with invalid geometry or an overlong name and imports the rest")
    void skipsStructurallyInvalidStreets() throws Exception {
        Path file = geoJson(
                feature(990_000_001L, "Rua Valida Um", VALID_LINE),
                feature(990_000_002L, "Rua De Um Ponto", "[[-46.6333,-23.5505]]"),
                feature(990_000_003L, "Rua Sem Numero", "[[-46.6333,\"abc\"],[-46.6340,-23.5510]]"),
                feature(990_000_004L, "R".repeat(181), VALID_LINE),
                feature(990_000_005L, "Rua Valida Dois", VALID_LINE)
        );

        runner(file).run();

        assertThat(jdbcTemplate.queryForList("SELECT name FROM streets ORDER BY osm_way_id", String.class))
                .containsExactly("Rua Valida Um", "Rua Valida Dois");
    }

    @Test
    @DisplayName("accepts a name with exactly 180 characters, counting accented letters once")
    void acceptsNameAtTheColumnLimit() throws Exception {
        String name = "Á".repeat(180);

        runner(geoJson(feature(990_000_001L, name, VALID_LINE))).run();

        assertThat(jdbcTemplate.queryForList("SELECT name FROM streets", String.class)).containsExactly(name);
    }

    @Test
    @DisplayName("skips the import when streets already exist")
    void skipsImportWhenStreetsExist() throws Exception {
        runner(geoJson(feature(990_000_001L, "Rua Valida Um", VALID_LINE))).run();

        runner(geoJson(feature(990_000_002L, "Rua Valida Dois", VALID_LINE))).run();

        assertThat(jdbcTemplate.queryForList("SELECT name FROM streets", String.class))
                .containsExactly("Rua Valida Um");
    }

    private StreetGeoJsonImportRunner runner(Path file) {
        return new StreetGeoJsonImportRunner(jdbcTemplate, transactionManager, objectMapper, file.toString());
    }

    private int countStreets() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM streets", Integer.class);
        return count == null ? 0 : count;
    }

    private Path geoJson(String... features) throws Exception {
        Path file = Files.createTempFile(tempDir, "streets", ".geojson");
        String body = List.of(features).stream().collect(Collectors.joining(","));
        Files.writeString(file, "{\"type\":\"FeatureCollection\",\"features\":[" + body + "]}");
        return file;
    }

    private static String feature(long osmWayId, String name, String coordinates) {
        return """
                {"type":"Feature","id":"way/%d","properties":{"name":"%s"},\
                "geometry":{"type":"LineString","coordinates":%s}}"""
                .formatted(osmWayId, name, coordinates);
    }
}
