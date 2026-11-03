package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExportController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
@DisplayName("ExportController MVC integration tests")
class ExportControllerTests {

    private static final TrafficRecordResponse RECORD = new TrafficRecordResponse(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            OffsetDateTime.parse("2024-06-17T08:00:00Z"),
            "ARTERIAL", 120, "RUSH_HOUR", "SUNNY",
            UUID.fromString("00000000-0000-0000-0000-000000000101"), 101L, "Avenida Central");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExportService exportService;

    @Test
    @DisplayName("returns csv exports with attachment headers")
    void returnsCsvExports() throws Exception {
        List<TrafficRecordResponse> records = List.of(RECORD);
        when(exportService.export(null)).thenReturn(new ExportService.ExportResult(records, 12L));
        when(exportService.toCsv(records)).thenReturn("""
                id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName
                00000000-0000-0000-0000-000000000001,2024-06-17T08:00Z,ARTERIAL,120,RUSH_HOUR,SUNNY,00000000-0000-0000-0000-000000000101,101,Avenida Central
                """);

        mockMvc.perform(get("/api/exports")
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=traffic-data.csv"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("X-Total-Records", "12"))
                .andExpect(header().string("X-Exported-Records", "1"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("streetOsmWayId,streetName")));
    }

    @Test
    @DisplayName("returns the records as json when json format is requested")
    void returnsJsonRecords() throws Exception {
        when(exportService.export(null)).thenReturn(new ExportService.ExportResult(List.of(RECORD), 1L));

        mockMvc.perform(get("/api/exports")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Total-Records", "1"))
                .andExpect(header().string("X-Exported-Records", "1"))
                .andExpect(jsonPath("$[0].id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$[0].roadType").value("ARTERIAL"))
                .andExpect(jsonPath("$[0].vehicleVolume").value(120))
                .andExpect(jsonPath("$[0].streetName").value("Avenida Central"));
    }

    @Test
    @DisplayName("exports the selection sent in the body of a POST")
    void exportsTheSelectionFromAPost() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(exportService.export(List.of(first, second)))
                .thenReturn(new ExportService.ExportResult(List.of(RECORD), 1L));

        mockMvc.perform(post("/api/exports/filter")
                        .param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordIds\":[\"" + first + "\",\"" + second + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Exported-Records", "1"))
                .andExpect(jsonPath("$[0].id").value("00000000-0000-0000-0000-000000000001"));

        verify(exportService).export(List.of(first, second));
    }

    @Test
    @DisplayName("rejects unsupported export formats before reading any record")
    void rejectsInvalidFormats() throws Exception {
        mockMvc.perform(get("/api/exports")
                        .param("format", "xml"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("Formato de exportacao invalido: xml"));
        mockMvc.perform(post("/api/exports/filter")
                        .param("format", "xml")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(exportService);
    }

    @Test
    @DisplayName("rejects a request without the format parameter")
    void rejectsAMissingFormat() throws Exception {
        mockMvc.perform(get("/api/exports"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    @Test
    @DisplayName("surfaces a selection larger than the limit as a bad request")
    void surfacesATooLargeSelection() throws Exception {
        when(exportService.export(any()))
                .thenThrow(new IllegalArgumentException("A seleção tem 4 registros e o limite por exportação é 3."));

        mockMvc.perform(post("/api/exports/filter")
                        .param("format", "csv")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recordIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("limite")));

        verify(exportService, never()).toCsv(any());
    }

    @Test
    @DisplayName("treats a body without ids as no selection")
    void treatsABodyWithoutIdsAsNoSelection() throws Exception {
        when(exportService.export(eq(null))).thenReturn(new ExportService.ExportResult(List.of(), 0L));

        mockMvc.perform(post("/api/exports/filter")
                        .param("format", "json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Records", "0"))
                .andExpect(jsonPath("$").isEmpty());
    }
}
