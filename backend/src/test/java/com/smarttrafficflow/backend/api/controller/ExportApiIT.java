package com.smarttrafficflow.backend.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarttrafficflow.backend.support.PostgisTestcontainersConfiguration;
import com.smarttrafficflow.backend.support.TrafficTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Data exports through the whole stack, with a real PostgreSQL/PostGIS database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@Transactional
@DisplayName("Export API with a real database")
class ExportApiIT {

    private static final String HEADER =
            "id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TrafficTestData data;

    @BeforeEach
    void setUp() {
        data = new TrafficTestData(jdbcTemplate);
        data.clear();
    }

    @Test
    @DisplayName("serves the csv as text/csv in UTF-8, starting with the byte order mark")
    void servesTheCsvAsUtf8WithByteOrderMark() throws Exception {
        UUID street = data.insertStreet(101L, "Avenida São João");
        data.insertRecord(street, "2024-06-17T12:00:00Z", "LOCAL", 10, "Obra", "Céu limpo");

        MvcResult result = mockMvc.perform(get("/api/exports").param("format", "csv"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/csv;charset=UTF-8");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=traffic-data.csv");
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith(String.valueOf((char) 0xFEFF) + HEADER + "\n");
        assertThat(csv).contains(",LOCAL,10,Obra,Céu limpo,").endsWith(",101,Avenida São João\n");
    }

    @Test
    @DisplayName("escapes commas, quotes and line breaks and neutralizes formulas, with the data coming from the database")
    void escapesAndProtectsTheStoredText() throws Exception {
        UUID street = data.insertStreet(202L, "=Rua \"Fórmula\", 1");
        UUID other = data.insertStreet(303L, "Rua da Quebra\nde Linha");
        data.insertRecord(street, "2024-06-17T12:00:00Z", "LOCAL", 10, "=1+1", "@SUM(A1)");
        data.insertRecord(other, "2024-06-17T11:00:00Z", "ARTERIAL", 20, "Show, \"ao vivo\"", "+5");

        String csv = mockMvc.perform(get("/api/exports").param("format", "csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains(",LOCAL,10,'=1+1,'@SUM(A1),");
        assertThat(csv).contains(",\"'=Rua \"\"Fórmula\"\", 1\"\n");
        assertThat(csv).contains(",ARTERIAL,20,\"Show, \"\"ao vivo\"\"\",'+5,");
        assertThat(csv).contains(",\"Rua da Quebra\nde Linha\"\n");
    }

    @Test
    @DisplayName("exports the real records as JSON, most recent first, with the same fields as the csv")
    void exportsRealRecordsAsJson() throws Exception {
        UUID street = data.insertStreet(101L, "Avenida Central");
        UUID older = data.insertRecord(street, "2024-06-17T08:00:00Z", "LOCAL", 10, "Obra", "Chuva");
        UUID newer = data.insertRecord(street, "2024-06-18T08:00:00Z", "HIGHWAY", 250, null, null);

        MvcResult result = mockMvc.perform(get("/api/exports").param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Records", "2"))
                .andExpect(header().string("X-Exported-Records", "2"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.toString()))
                .andExpect(jsonPath("$[1].id").value(older.toString()))
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode first = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get(0);
        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "timestamp", "roadType", "vehicleVolume", "eventType", "weather",
                "streetId", "streetOsmWayId", "streetName");
        assertThat(first.get("roadType").asText()).isEqualTo("HIGHWAY");
        assertThat(first.get("vehicleVolume").asInt()).isEqualTo(250);
        assertThat(first.get("streetOsmWayId").asLong()).isEqualTo(101L);
        assertThat(first.get("streetName").asText()).isEqualTo("Avenida Central");
    }

    @Test
    @DisplayName("informs the total and the exported count in headers, also for the csv")
    void informsTotalsInHeaders() throws Exception {
        UUID street = data.insertStreet(101L, "Avenida Central");
        data.insertRecord(street, "2024-06-17T08:00:00Z", "LOCAL", 10);

        mockMvc.perform(get("/api/exports").param("format", "csv"))
                .andExpect(header().string("X-Total-Records", "1"))
                .andExpect(header().string("X-Exported-Records", "1"));
    }

    @Test
    @DisplayName("exports only the selected records when ids are sent in the body of a POST")
    void exportsOnlyTheSelection() throws Exception {
        UUID street = data.insertStreet(101L, "Avenida Central");
        UUID first = data.insertRecord(street, "2024-06-17T08:00:00Z", "LOCAL", 10);
        data.insertRecord(street, "2024-06-17T09:00:00Z", "ARTERIAL", 20);
        UUID third = data.insertRecord(street, "2024-06-17T10:00:00Z", "HIGHWAY", 30);

        mockMvc.perform(post("/api/exports/filter").param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of(first, third, first))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Records", "2"))
                .andExpect(header().string("X-Exported-Records", "2"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(third.toString()))
                .andExpect(jsonPath("$[1].id").value(first.toString()));

        String csv = mockMvc.perform(post("/api/exports/filter").param("format", "csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(List.of(first, third))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv.lines().count()).isEqualTo(3);
        assertThat(csv).contains(",HIGHWAY,30,").contains(",LOCAL,10,").doesNotContain(",ARTERIAL,20,");
    }

    @Test
    @DisplayName("exports everything when the POST has no ids")
    void exportsEverythingWithoutIds() throws Exception {
        UUID street = data.insertStreet(101L, "Avenida Central");
        data.insertRecord(street, "2024-06-17T08:00:00Z", "LOCAL", 10);
        data.insertRecord(street, "2024-06-17T09:00:00Z", "LOCAL", 20);

        mockMvc.perform(post("/api/exports/filter").param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recordIds\":[]}"))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(post("/api/exports/filter").param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("exports an empty file for ids that do not exist")
    void exportsNothingForUnknownIds() throws Exception {
        mockMvc.perform(post("/api/exports/filter").param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON).content(body(List.of(UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Records", "0"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("rejects an invalid or missing format with 400, by GET and by POST")
    void rejectsInvalidAndMissingFormats() throws Exception {
        mockMvc.perform(get("/api/exports").param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Formato de exportacao invalido: xml")));
        mockMvc.perform(post("/api/exports/filter").param("format", "xml")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/exports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    private String body(List<UUID> ids) throws Exception {
        return objectMapper.writeValueAsString(Map.of("recordIds", ids));
    }
}
