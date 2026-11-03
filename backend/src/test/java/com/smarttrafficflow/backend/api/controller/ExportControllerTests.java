package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.exception.GlobalExceptionHandler;
import com.smarttrafficflow.backend.domain.exports.service.ExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExportController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("ExportController MVC integration tests")
class ExportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @Test
    @DisplayName("returns csv exports with attachment headers")
    void returnsCsvExports() throws Exception {
        when(exportService.exportCsv()).thenReturn("""
                id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName
                00000000-0000-0000-0000-000000000001,2024-06-17T08:00Z,ARTERIAL,120,RUSH_HOUR,SUNNY,00000000-0000-0000-0000-000000000101,101,Avenida Central
                """);

        mockMvc.perform(get("/api/exports")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=traffic-data.csv"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("streetOsmWayId,streetName")));
    }

    @Test
    @DisplayName("returns json guidance when json format is requested")
    void returnsJsonGuidancePayload() throws Exception {
        mockMvc.perform(get("/api/exports")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ok"))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("/api/traffic-records")));
    }

    @Test
    @DisplayName("rejects unsupported export formats")
    void rejectsInvalidFormats() throws Exception {
        mockMvc.perform(get("/api/exports")
                        .param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("Formato de exportacao invalido: xml"));
    }
}
