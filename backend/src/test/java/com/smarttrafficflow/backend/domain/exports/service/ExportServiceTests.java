package com.smarttrafficflow.backend.domain.exports.service;

import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService tests")
class ExportServiceTests {

    @Mock
    private TrafficRecordService trafficRecordService;

    @InjectMocks
    private ExportService exportService;

    @Test
    @DisplayName("returns only the current csv header when there are no records")
    void returnsOnlyHeaderWhenThereAreNoRecords() {
        when(trafficRecordService.findAll()).thenReturn(List.of());

        String csv = exportService.exportCsv();

        assertThat(csv).isEqualTo("﻿id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName\n");
    }

    @Test
    @DisplayName("exports each current record field in csv order")
    void exportsCurrentStreetBasedColumns() {
        when(trafficRecordService.findAll()).thenReturn(List.of(
                recordResponse("ARTERIAL", 120, "RUSH_HOUR", "SUNNY", 101L, "Avenida Central"),
                recordResponse("HIGHWAY", 250, null, "RAIN", 202L, "Rodovia Norte")
        ));

        String csv = exportService.exportCsv();

        assertThat(csv).contains("streetId,streetOsmWayId,streetName");
        assertThat(csv).contains("ARTERIAL,120,RUSH_HOUR,SUNNY");
        assertThat(csv).contains("HIGHWAY,250,,RAIN");
        assertThat(csv).contains(",101,Avenida Central");
        assertThat(csv).contains(",202,Rodovia Norte");
    }

    @Test
    @DisplayName("renders nullable string fields as empty values")
    void rendersNullableFieldsAsEmptyValues() {
        when(trafficRecordService.findAll()).thenReturn(List.of(
                recordResponse("LOCAL", 80, null, null, 303L, null)
        ));

        String dataLine = exportService.exportCsv().lines().skip(1).findFirst().orElseThrow();

        assertThat(dataLine).contains(",LOCAL,80,,,");
        assertThat(dataLine).endsWith(",303,");
    }

    @Test
    @DisplayName("returns one data line per exported record")
    void returnsOneDataLinePerExportedRecord() {
        when(trafficRecordService.findAll()).thenReturn(List.of(
                recordResponse("ARTERIAL", 120, "RUSH_HOUR", "SUNNY", 101L, "Avenida Central"),
                recordResponse("HIGHWAY", 250, null, "RAIN", 202L, "Rodovia Norte"),
                recordResponse("LOCAL", 80, "SCHOOL", "CLOUDY", 303L, "Rua Um")
        ));

        List<String> lines = exportService.exportCsv().lines().toList();

        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).isEqualTo("﻿id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName");
        assertThat(lines.get(3)).contains(",LOCAL,80,SCHOOL,CLOUDY,");
    }

    @Test
    @DisplayName("starts the file with a UTF-8 byte order mark so spreadsheets show accents")
    void startsWithByteOrderMark() {
        when(trafficRecordService.findAll()).thenReturn(List.of());

        assertThat(exportService.exportCsv()).startsWith("﻿id,timestamp,");
    }

    @Test
    @DisplayName("quotes fields with a comma, doubling the inner quotes (RFC 4180)")
    void quotesFieldsWithCommasAndQuotes() {
        when(trafficRecordService.findAll()).thenReturn(List.of(
                recordResponse("LOCAL", 10, "Show, \"ao vivo\"", "Chuva, forte", 1L, "Rua \"Boa\", 10")
        ));

        String dataLine = dataLine();

        assertThat(dataLine).contains(",\"Show, \"\"ao vivo\"\"\",\"Chuva, forte\",");
        assertThat(dataLine).endsWith(",1,\"Rua \"\"Boa\"\", 10\"");
    }

    @Test
    @DisplayName("quotes fields with a line break, so one record stays in one csv row")
    void quotesFieldsWithLineBreaks() {
        when(trafficRecordService.findAll()).thenReturn(List.of(
                recordResponse("LOCAL", 10, "linha 1\nlinha 2", "a\r\nb", 1L, "Rua Um")
        ));

        String csv = exportService.exportCsv();

        assertThat(csv).contains(",\"linha 1\nlinha 2\",\"a\r\nb\",");
    }

    @Test
    @DisplayName("prefixes text that starts with =, +, - or @ with an apostrophe, against formula injection")
    void protectsAgainstFormulaInjection() {
        when(trafficRecordService.findAll()).thenReturn(List.of(
                recordResponse("=1+1", 10, "@SUM(A1)", "+5", 1L, "-5"),
                recordResponse("LOCAL", 10, "-cmd", "=A1,B1", 2L, "Rua Dois")
        ));

        List<String> rows = exportService.exportCsv().lines().toList();

        assertThat(rows.get(1)).contains(",'=1+1,10,'@SUM(A1),'+5,").endsWith(",1,'-5");
        assertThat(rows.get(2)).contains(",'-cmd,\"'=A1,B1\",");
    }

    @Test
    @DisplayName("prefixes text that starts with a tab or a carriage return")
    void protectsAgainstFormulaStartingWithControlCharacters() {
        when(trafficRecordService.findAll()).thenReturn(List.of(
                recordResponse("LOCAL", 10, "\t=1+1", "\rx", 1L, "Rua Um")
        ));

        assertThat(exportService.exportCsv()).contains(",'\t=1+1,\"'\rx\",");
    }

    @Test
    @DisplayName("keeps accents and does not change harmless text")
    void keepsAccentsAndHarmlessText() {
        when(trafficRecordService.findAll()).thenReturn(List.of(
                recordResponse("ARTERIAL", 10, "Obra", "Céu limpo", 1L, "Avenida São João")
        ));

        assertThat(dataLine()).contains(",ARTERIAL,10,Obra,Céu limpo,").endsWith(",1,Avenida São João");
    }

    @Test
    @DisplayName("does not alter numbers, ids or timestamps")
    void doesNotAlterNumbersIdsAndTimestamps() {
        UUID id = UUID.randomUUID();
        UUID streetId = UUID.randomUUID();
        when(trafficRecordService.findAll()).thenReturn(List.of(new TrafficRecordResponse(
                id, OffsetDateTime.of(2024, 6, 17, 8, 30, 0, 0, ZoneOffset.UTC),
                "LOCAL", 120, null, null, streetId, 55L, "Rua Um")));

        assertThat(dataLine()).isEqualTo(id + ",2024-06-17T08:30Z,LOCAL,120,,," + streetId + ",55,Rua Um");
    }

    private String dataLine() {
        return exportService.exportCsv().lines().skip(1).findFirst().orElseThrow();
    }

    private TrafficRecordResponse recordResponse(
            String roadType,
            int volume,
            String eventType,
            String weather,
            long streetOsmWayId,
            String streetName
    ) {
        return new TrafficRecordResponse(
                UUID.randomUUID(),
                OffsetDateTime.of(2024, 6, 17, 8, 0, 0, 0, ZoneOffset.UTC),
                roadType,
                volume,
                eventType,
                weather,
                UUID.randomUUID(),
                streetOsmWayId,
                streetName
        );
    }
}
