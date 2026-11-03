package com.smarttrafficflow.backend.api.controller;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * The export limit, with a small ceiling (3 records) to prove the cut with a real database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgisTestcontainersConfiguration.class)
@TestPropertySource(properties = "app.export.max-records=3")
@Transactional
@DisplayName("Export limit with a real database")
class ExportLimitApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> recordsOldestFirst = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TrafficTestData data = new TrafficTestData(jdbcTemplate);
        data.clear();
        recordsOldestFirst.clear();
        UUID street = data.insertStreet(101L, "Avenida Central");
        for (int day = 10; day <= 14; day++) {
            recordsOldestFirst.add(data.insertRecord(street, "2024-06-" + day + "T08:00:00Z", "LOCAL", day));
        }
    }

    @Test
    @DisplayName("without selection, exports only the most recent records up to the limit and informs the cut")
    void cutsTheJsonAtTheLimit() throws Exception {
        mockMvc.perform(get("/api/exports").param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Records", "5"))
                .andExpect(header().string("X-Exported-Records", "3"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(recordsOldestFirst.get(4).toString()))
                .andExpect(jsonPath("$[1].id").value(recordsOldestFirst.get(3).toString()))
                .andExpect(jsonPath("$[2].id").value(recordsOldestFirst.get(2).toString()));
    }

    @Test
    @DisplayName("cuts the csv at the limit with the header plus the most recent rows")
    void cutsTheCsvAtTheLimit() throws Exception {
        MockHttpResult result = new MockHttpResult(mockMvc.perform(get("/api/exports").param("format", "csv"))
                .andExpect(header().string("X-Total-Records", "5"))
                .andExpect(header().string("X-Exported-Records", "3"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(result.csv().lines().count()).isEqualTo(4);
        assertThat(result.csv()).contains(recordsOldestFirst.get(4).toString())
                .doesNotContain(recordsOldestFirst.get(0).toString())
                .doesNotContain(recordsOldestFirst.get(1).toString());
    }

    @Test
    @DisplayName("cuts a POST without ids the same way")
    void cutsAPostWithoutIds() throws Exception {
        mockMvc.perform(post("/api/exports/filter").param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(header().string("X-Total-Records", "5"))
                .andExpect(header().string("X-Exported-Records", "3"));
    }

    @Test
    @DisplayName("exports the whole selection when it is within the limit")
    void exportsASelectionWithinTheLimit() throws Exception {
        mockMvc.perform(post("/api/exports/filter").param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("recordIds", recordsOldestFirst.subList(0, 3)))))
                .andExpect(header().string("X-Total-Records", "3"))
                .andExpect(header().string("X-Exported-Records", "3"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("rejects a selection larger than the limit with 400 and a clear message")
    void rejectsASelectionLargerThanTheLimit() throws Exception {
        mockMvc.perform(post("/api/exports/filter").param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("recordIds", recordsOldestFirst.subList(0, 4)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value(containsString("limite")))
                .andExpect(jsonPath("$.message").value(containsString("3")));
    }

    private record MockHttpResult(String csv) {
    }
}
