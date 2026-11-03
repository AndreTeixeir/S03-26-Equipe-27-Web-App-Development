package com.smarttrafficflow.backend.api.controller;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        assertThat(csv).startsWith("﻿" + HEADER + "\n");
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
}
