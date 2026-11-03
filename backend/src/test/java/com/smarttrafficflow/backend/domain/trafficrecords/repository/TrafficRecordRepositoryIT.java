package com.smarttrafficflow.backend.domain.trafficrecords.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.domain.streets.entity.Street;
import com.smarttrafficflow.backend.domain.streets.repository.StreetRepository;
import com.smarttrafficflow.backend.domain.trafficrecords.entity.TrafficRecord;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@DisplayName("TrafficRecordRepository integration tests")
class TrafficRecordRepositoryIT {

    private static final String CENTRAL_GEOMETRY =
            "{\"type\":\"LineString\",\"coordinates\":[[-46.6333,-23.5505],[-46.634,-23.551]]}";
    private static final String FLORES_GEOMETRY =
            "{\"type\":\"LineString\",\"coordinates\":[[-46.64,-23.56],[-46.641,-23.561]]}";

    @Autowired
    private TrafficRecordRepository trafficRecordRepository;

    @Autowired
    private StreetRepository streetRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    private UUID centralId;
    private UUID floresId;
    private UUID morningRecordId;
    private UUID highwayRecordId;
    private UUID floresRecordId;

    @BeforeEach
    void seedData() {
        jdbcTemplate.update("DELETE FROM traffic_records");
        jdbcTemplate.update("DELETE FROM streets");
        centralId = insertStreet(101L, "Avenida Central", CENTRAL_GEOMETRY);
        floresId = insertStreet(202L, "Rua das Flores", FLORES_GEOMETRY);

        morningRecordId = saveRecord(centralId, "2024-06-17T08:00:00Z", "ARTERIAL", 100, null, "Chuva");
        highwayRecordId = saveRecord(centralId, "2024-06-17T09:00:00Z", "HIGHWAY", 250, "Acidente", "Sol");
        floresRecordId = saveRecord(floresId, "2024-06-18T10:00:00Z", "LOCAL", 40, null, null);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("loads the most recent records with their streets, newest first, up to the page size")
    void loadsTheMostRecentRecordsWithStreet() {
        List<TrafficRecord> all = trafficRecordRepository.findLatestWithStreet(PageRequest.of(0, 10));
        List<TrafficRecord> latestTwo = trafficRecordRepository.findLatestWithStreet(PageRequest.of(0, 2));

        assertThat(all).extracting(TrafficRecord::getId)
                .containsExactly(floresRecordId, highwayRecordId, morningRecordId);
        assertThat(all).extracting(record -> record.getStreet().getName())
                .containsExactly("Rua das Flores", "Avenida Central", "Avenida Central");
        assertThat(latestTwo).extracting(TrafficRecord::getId).containsExactly(floresRecordId, highwayRecordId);
    }

    @Test
    @DisplayName("breaks timestamp ties by id, descending, so the cut is stable")
    void breaksTimestampTiesById() {
        UUID first = saveRecord(centralId, "2025-01-01T00:00:00Z", "LOCAL", 1, null, null);
        UUID second = saveRecord(centralId, "2025-01-01T00:00:00Z", "LOCAL", 1, null, null);
        entityManager.flush();
        entityManager.clear();

        List<TrafficRecord> latest = trafficRecordRepository.findLatestWithStreet(PageRequest.of(0, 2));

        UUID expectedFirst = first.compareTo(second) > 0 ? first : second;
        UUID expectedSecond = first.compareTo(second) > 0 ? second : first;
        assertThat(latest).extracting(TrafficRecord::getId).containsExactly(expectedFirst, expectedSecond);
    }

    @Test
    @DisplayName("pages records with the sort used by the service")
    void pagesRecordsWithServiceSort() {
        PageRequest firstPage = PageRequest.of(0, 2, Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id")));

        Page<TrafficRecord> page = trafficRecordRepository.findPageWithStreet(null, firstPage);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(TrafficRecord::getId)
                .containsExactly(floresRecordId, highwayRecordId);
    }

    @Test
    @DisplayName("filters pages by road type, street name, weather and event type")
    void filtersPagesByQueryPattern() {
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Order.desc("timestamp")));

        assertThat(trafficRecordRepository.findPageWithStreet("%highway%", pageRequest).getContent())
                .extracting(TrafficRecord::getId).containsExactly(highwayRecordId);
        assertThat(trafficRecordRepository.findPageWithStreet("%flores%", pageRequest).getContent())
                .extracting(TrafficRecord::getId).containsExactly(floresRecordId);
        assertThat(trafficRecordRepository.findPageWithStreet("%chuva%", pageRequest).getContent())
                .extracting(TrafficRecord::getId).containsExactly(morningRecordId);
        assertThat(trafficRecordRepository.findPageWithStreet("%acidente%", pageRequest).getContent())
                .extracting(TrafficRecord::getId).containsExactly(highwayRecordId);
        assertThat(trafficRecordRepository.findPageWithStreet("%avenida%", pageRequest).getTotalElements())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("lists only the ids of the filtered records, newest first, with the total of matches")
    void listsTheFilteredIds() {
        Page<UUID> all = trafficRecordRepository.findIdsByFilter(null, PageRequest.of(0, 2));
        Page<UUID> highways = trafficRecordRepository.findIdsByFilter("%highway%", PageRequest.of(0, 10));
        Page<UUID> avenue = trafficRecordRepository.findIdsByFilter("%avenida%", PageRequest.of(0, 10));
        Page<UUID> nothing = trafficRecordRepository.findIdsByFilter("%inexistente%", PageRequest.of(0, 10));

        assertThat(all.getContent()).containsExactly(floresRecordId, highwayRecordId);
        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(highways.getContent()).containsExactly(highwayRecordId);
        assertThat(avenue.getContent()).containsExactly(highwayRecordId, morningRecordId);
        assertThat(avenue.getTotalElements()).isEqualTo(2);
        assertThat(nothing.getContent()).isEmpty();
        assertThat(nothing.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("loads only the requested records with their streets")
    void loadsRequestedRecordsWithStreet() {
        List<TrafficRecord> records = trafficRecordRepository.findAllByIdInWithStreet(
                List.of(morningRecordId, floresRecordId));

        assertThat(records).extracting(TrafficRecord::getId)
                .containsExactly(floresRecordId, morningRecordId);
        assertThat(records).extracting(record -> record.getStreet().getName())
                .containsExactly("Rua das Flores", "Avenida Central");
    }

    @Test
    @DisplayName("summarizes all records")
    void summarizesAllRecords() {
        TrafficRecordRepository.TrafficRecordSummaryView summary = trafficRecordRepository.summarizeAll();

        assertThat(summary.getRecordCount()).isEqualTo(3);
        assertThat(summary.getTotalVehicleVolume()).isEqualTo(390);
        assertThat(summary.getUniqueStreetCount()).isEqualTo(2);
        assertThat(summary.getLatestTimestamp()).isAtSameInstantAs(OffsetDateTime.parse("2024-06-18T10:00:00Z"));
    }

    @Test
    @DisplayName("summarizes an empty table with zeros and no latest timestamp")
    void summarizesEmptyTable() {
        jdbcTemplate.update("DELETE FROM traffic_records");

        TrafficRecordRepository.TrafficRecordSummaryView summary = trafficRecordRepository.summarizeAll();

        assertThat(summary.getRecordCount()).isZero();
        assertThat(summary.getTotalVehicleVolume()).isZero();
        assertThat(summary.getUniqueStreetCount()).isZero();
        assertThat(summary.getLatestTimestamp()).isNull();
    }

    @Test
    @DisplayName("summarizes only the requested records")
    void summarizesRequestedRecords() {
        TrafficRecordRepository.TrafficRecordSummaryView summary = trafficRecordRepository.summarizeByIds(
                List.of(highwayRecordId, floresRecordId));

        assertThat(summary.getRecordCount()).isEqualTo(2);
        assertThat(summary.getTotalVehicleVolume()).isEqualTo(290);
        assertThat(summary.getUniqueStreetCount()).isEqualTo(2);
        assertThat(summary.getLatestTimestamp()).isAtSameInstantAs(OffsetDateTime.parse("2024-06-18T10:00:00Z"));
    }

    @Test
    @DisplayName("projects map features with the street geometry as GeoJSON")
    void projectsMapFeaturesWithGeoJson() throws Exception {
        List<TrafficRecordRepository.TrafficMapFeatureView> features =
                trafficRecordRepository.findMapFeaturesByRecordIds(List.of(morningRecordId));

        assertThat(features).hasSize(1);
        TrafficRecordRepository.TrafficMapFeatureView feature = features.get(0);
        assertThat(feature.getRecordId()).isEqualTo(morningRecordId);
        assertThat(feature.getStreetId()).isEqualTo(centralId);
        assertThat(feature.getStreetOsmWayId()).isEqualTo(101L);
        assertThat(feature.getStreetName()).isEqualTo("Avenida Central");
        assertThat(feature.getVehicleVolume()).isEqualTo(100);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode geometry = objectMapper.readTree(feature.getGeometry());
        assertThat(geometry).isEqualTo(objectMapper.readTree(CENTRAL_GEOMETRY));
    }

    @Test
    @DisplayName("projects the map features of the most recent records, newest first, up to the limit")
    void projectsTheLatestMapFeatures() {
        List<TrafficRecordRepository.TrafficMapFeatureView> all = trafficRecordRepository.findLatestMapFeatures(10);
        List<TrafficRecordRepository.TrafficMapFeatureView> latestTwo = trafficRecordRepository.findLatestMapFeatures(2);

        assertThat(all).extracting(TrafficRecordRepository.TrafficMapFeatureView::getRecordId)
                .containsExactly(floresRecordId, highwayRecordId, morningRecordId);
        assertThat(latestTwo).extracting(TrafficRecordRepository.TrafficMapFeatureView::getRecordId)
                .containsExactly(floresRecordId, highwayRecordId);
        assertThat(all.get(0).getStreetName()).isEqualTo("Rua das Flores");
        assertThat(all.get(0).getGeometry()).contains("LineString");
    }

    private UUID insertStreet(long osmWayId, String name, String geometryJson) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO streets (id, osm_way_id, name, geom)
                VALUES (?, ?, ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4326))
                """,
                id,
                osmWayId,
                name,
                geometryJson
        );
        return id;
    }

    private UUID saveRecord(UUID streetId, String timestamp, String roadType, int vehicleVolume,
                            String eventType, String weather) {
        Street street = streetRepository.findById(streetId).orElseThrow();
        TrafficRecord record = new TrafficRecord();
        record.setStreet(street);
        record.setTimestamp(OffsetDateTime.parse(timestamp));
        record.setRoadType(roadType);
        record.setVehicleVolume(vehicleVolume);
        record.setEventType(eventType);
        record.setWeather(weather);
        return trafficRecordRepository.save(record).getId();
    }
}
