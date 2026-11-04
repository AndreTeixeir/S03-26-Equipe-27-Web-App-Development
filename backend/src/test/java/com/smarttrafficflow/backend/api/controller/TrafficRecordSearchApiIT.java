package com.smarttrafficflow.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import com.smarttrafficflow.backend.support.TrafficTestData;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The table filter ("Filtro") and "select all filtered" must understand the words the screen shows ("Chuva",
 * "Via expressa"...), not only the codes stored in the database ("RAIN", "HIGHWAY"), with a real database.
 * The expected totals were counted by hand from the eight records below.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@Transactional
@DisplayName("Records search by the words the screen shows, with a real database")
class TrafficRecordSearchApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID floresLocalRain;
    private UUID floresArterialSunny;
    private UUID joaoHighwayCloudy;
    private UUID joaoLocalFreeTextRain;
    private UUID floresHighwayRainWithEvent;
    private UUID joaoArterialRainInTheEvent;
    private UUID floresUnknownCloudy;
    private UUID joaoArterialLowerCaseRain;

    @BeforeEach
    void setUp() {
        TrafficTestData data = new TrafficTestData(jdbcTemplate);
        data.clear();
        UUID flores = data.insertStreet(202L, "Rua das Flores");
        UUID joao = data.insertStreet(303L, "Avenida São João");
        // The weather and the road type are stored as codes (simulation) or as free text (typed by a person).
        floresLocalRain = data.insertRecord(flores, "2024-06-10T08:00:00Z", "LOCAL", 10, null, "RAIN");
        floresArterialSunny = data.insertRecord(flores, "2024-06-11T08:00:00Z", "ARTERIAL", 20, null, "SUNNY");
        joaoHighwayCloudy = data.insertRecord(joao, "2024-06-12T08:00:00Z", "HIGHWAY", 30, null, "CLOUDY");
        joaoLocalFreeTextRain = data.insertRecord(joao, "2024-06-13T08:00:00Z", "LOCAL", 40, null, "Chuva forte");
        floresHighwayRainWithEvent = data.insertRecord(flores, "2024-06-14T08:00:00Z", "HIGHWAY", 50, "Obra", "RAIN");
        joaoArterialRainInTheEvent = data.insertRecord(joao, "2024-06-15T08:00:00Z", "ARTERIAL", 60, "chuva na pista", null);
        floresUnknownCloudy = data.insertRecord(flores, "2024-06-16T08:00:00Z", "UNKNOWN", 70, null, "CLOUDY");
        joaoArterialLowerCaseRain = data.insertRecord(joao, "2024-06-17T08:00:00Z", "ARTERIAL", 80, null, "rain");
    }

    @Test
    @DisplayName("finds the weather by the word the screen shows, in any case")
    void findsTheWeatherByTheWordTheScreenShows() throws Exception {
        // "Chuva" is RAIN (r1, r5, r8) plus the free text that already contained it (r4, r6).
        assertTotals(Map.of(
                "chuva", 5L,
                "CHUVA", 5L,
                "  Chuva  ", 5L,
                "ensolarado", 1L,
                "Ensolarado", 1L,
                "nublado", 2L,
                "NUBLADO", 2L
        ));
    }

    @Test
    @DisplayName("finds part of a word, as before: 'chu' finds the rain, 'via' finds the three road types")
    void findsPartOfAWord() throws Exception {
        assertTotals(Map.of(
                "chu", 5L,
                "via", 7L,
                "local", 2L
        ));
    }

    @Test
    @DisplayName("finds the road type by the words the screen shows")
    void findsTheRoadTypeByTheWordsTheScreenShows() throws Exception {
        assertTotals(Map.of(
                "via expressa", 2L,
                "Via Local", 2L,
                "VIA ARTERIAL", 3L,
                "via arterial", 3L
        ));
    }

    @Test
    @DisplayName("ignores the accents of the term: 'não informado' and 'nao informado' find the unknown road type")
    void ignoresTheAccentsOfTheTerm() throws Exception {
        assertTotals(Map.of(
                "não informado", 1L,
                "nao informado", 1L,
                "NÃO INFORMADO", 1L
        ));
    }

    @Test
    @DisplayName("keeps finding what it already found: codes, free text and the street name")
    void keepsFindingWhatItAlreadyFound() throws Exception {
        assertTotals(Map.of(
                "rain", 3L,
                "highway", 2L,
                "arterial", 3L,
                "unknown", 1L,
                "chuva forte", 1L,
                "chuva na", 1L,
                "obra", 1L,
                "flores", 4L,
                "avenida", 4L
        ));
    }

    @Test
    @DisplayName("answers with no results, and no error, when the term matches nothing")
    void answersWithNothingWhenTheTermMatchesNothing() throws Exception {
        JsonNode page = getJson(get("/api/traffic-records").param("query", "xyzabc"));
        JsonNode ids = getJson(get("/api/traffic-records/ids").param("query", "xyzabc"));

        assertThat(page.get("totalItems").asLong()).isZero();
        assertThat(page.get("items")).isEmpty();
        assertThat(ids.get("total").asLong()).isZero();
        assertThat(ids.get("recordIds")).isEmpty();
    }

    @Test
    @DisplayName("returns every record when there is no term")
    void returnsEverythingWithoutATerm() throws Exception {
        assertThat(getJson(get("/api/traffic-records")).get("totalItems").asLong()).isEqualTo(8);
        assertThat(getJson(get("/api/traffic-records").param("query", "   ")).get("totalItems").asLong()).isEqualTo(8);
        assertThat(getJson(get("/api/traffic-records/ids")).get("total").asLong()).isEqualTo(8);
    }

    @Test
    @DisplayName("'select all filtered' returns exactly the records of the table for 'chuva'")
    void selectsExactlyTheRecordsOfTheTable() throws Exception {
        Set<UUID> expected = Set.of(
                floresLocalRain, joaoLocalFreeTextRain, floresHighwayRainWithEvent,
                joaoArterialRainInTheEvent, joaoArterialLowerCaseRain);

        Set<UUID> inTheTable = idsOf(getJson(get("/api/traffic-records").param("query", "chuva").param("size", "100")).get("items"), "id");
        Set<UUID> selected = idsOf(getJson(get("/api/traffic-records/ids").param("query", "chuva")).get("recordIds"), null);

        assertThat(inTheTable).isEqualTo(expected);
        assertThat(selected).isEqualTo(expected);
    }

    private void assertTotals(Map<String, Long> expectedByTerm) throws Exception {
        SoftAssertions softly = new SoftAssertions();
        for (Map.Entry<String, Long> entry : expectedByTerm.entrySet()) {
            String term = entry.getKey();
            long inTheTable = getJson(get("/api/traffic-records").param("query", term)).get("totalItems").asLong();
            long inTheIds = getJson(get("/api/traffic-records/ids").param("query", term)).get("total").asLong();
            softly.assertThat(inTheTable).as("table, term '%s'", term).isEqualTo(entry.getValue());
            softly.assertThat(inTheIds).as("ids, term '%s'", term).isEqualTo(entry.getValue());
        }
        softly.assertAll();
    }

    private JsonNode getJson(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** The ids in a JSON array: the array holds the ids themselves, or objects that carry them in {@code field}. */
    private Set<UUID> idsOf(JsonNode nodes, String field) {
        return StreamSupport.stream(nodes.spliterator(), false)
                .map(node -> field == null ? node.asText() : node.get(field).asText())
                .map(UUID::fromString)
                .collect(Collectors.toSet());
    }
}
