package com.smarttrafficflow.backend.domain.exports.service;

import com.smarttrafficflow.backend.api.dto.TrafficRecordResponse;
import com.smarttrafficflow.backend.config.RecordLimits;
import com.smarttrafficflow.backend.domain.trafficrecords.service.TrafficRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService tests")
class ExportServiceTests {

    // U+FEFF, written as a number because the character itself is invisible in the source.
    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final String HEADER =
            "id,timestamp,roadType,vehicleVolume,eventType,weather,streetId,streetOsmWayId,streetName";

    @Mock
    private TrafficRecordService trafficRecordService;

    private ExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new ExportService(trafficRecordService, new RecordLimits("3", "250"));
    }

    // ---- CSV format ----

    @Test
    @DisplayName("returns only the current csv header when there are no records")
    void returnsOnlyHeaderWhenThereAreNoRecords() {
        String csv = exportService.toCsv(List.of());

        assertThat(csv).isEqualTo(BOM + HEADER + "\n");
    }

    @Test
    @DisplayName("exports each current record field in csv order")
    void exportsCurrentStreetBasedColumns() {
        String csv = exportService.toCsv(List.of(
                recordResponse("ARTERIAL", 120, "RUSH_HOUR", "SUNNY", 101L, "Avenida Central"),
                recordResponse("HIGHWAY", 250, null, "RAIN", 202L, "Rodovia Norte")
        ));

        assertThat(csv).contains("streetId,streetOsmWayId,streetName");
        assertThat(csv).contains("ARTERIAL,120,RUSH_HOUR,SUNNY");
        assertThat(csv).contains("HIGHWAY,250,,RAIN");
        assertThat(csv).contains(",101,Avenida Central");
        assertThat(csv).contains(",202,Rodovia Norte");
    }

    @Test
    @DisplayName("renders nullable string fields as empty values")
    void rendersNullableFieldsAsEmptyValues() {
        String dataLine = dataLine(recordResponse("LOCAL", 80, null, null, 303L, null));

        assertThat(dataLine).contains(",LOCAL,80,,,");
        assertThat(dataLine).endsWith(",303,");
    }

    @Test
    @DisplayName("returns one data line per exported record")
    void returnsOneDataLinePerExportedRecord() {
        List<String> lines = exportService.toCsv(List.of(
                recordResponse("ARTERIAL", 120, "RUSH_HOUR", "SUNNY", 101L, "Avenida Central"),
                recordResponse("HIGHWAY", 250, null, "RAIN", 202L, "Rodovia Norte"),
                recordResponse("LOCAL", 80, "SCHOOL", "CLOUDY", 303L, "Rua Um")
        )).lines().toList();

        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).isEqualTo(BOM + HEADER);
        assertThat(lines.get(3)).contains(",LOCAL,80,SCHOOL,CLOUDY,");
    }

    @Test
    @DisplayName("starts the file with a UTF-8 byte order mark so spreadsheets show accents")
    void startsWithByteOrderMark() {
        byte[] bytes = exportService.toCsv(List.of()).getBytes(StandardCharsets.UTF_8);

        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).startsWith(BOM + "id,timestamp,");
    }

    @Test
    @DisplayName("quotes fields with a comma, doubling the inner quotes (RFC 4180)")
    void quotesFieldsWithCommasAndQuotes() {
        String dataLine = dataLine(recordResponse("LOCAL", 10, "Show, \"ao vivo\"", "Chuva, forte", 1L, "Rua \"Boa\", 10"));

        assertThat(dataLine).contains(",\"Show, \"\"ao vivo\"\"\",\"Chuva, forte\",");
        assertThat(dataLine).endsWith(",1,\"Rua \"\"Boa\"\", 10\"");
    }

    @Test
    @DisplayName("quotes fields with a line break, so one record stays in one csv row")
    void quotesFieldsWithLineBreaks() {
        String csv = exportService.toCsv(List.of(recordResponse("LOCAL", 10, "linha 1\nlinha 2", "a\r\nb", 1L, "Rua Um")));

        assertThat(csv).contains(",\"linha 1\nlinha 2\",\"a\r\nb\",");
    }

    @Test
    @DisplayName("prefixes text that starts with =, +, - or @ with an apostrophe, against formula injection")
    void protectsAgainstFormulaInjection() {
        List<String> rows = exportService.toCsv(List.of(
                recordResponse("=1+1", 10, "@SUM(A1)", "+5", 1L, "-5"),
                recordResponse("LOCAL", 10, "-cmd", "=A1,B1", 2L, "Rua Dois")
        )).lines().toList();

        assertThat(rows.get(1)).contains(",'=1+1,10,'@SUM(A1),'+5,").endsWith(",1,'-5");
        assertThat(rows.get(2)).contains(",'-cmd,\"'=A1,B1\",");
    }

    @Test
    @DisplayName("prefixes text that starts with a tab or a carriage return")
    void protectsAgainstFormulaStartingWithControlCharacters() {
        String csv = exportService.toCsv(List.of(recordResponse("LOCAL", 10, "\t=1+1", "\rx", 1L, "Rua Um")));

        assertThat(csv).contains(",'\t=1+1,\"'\rx\",");
    }

    @Test
    @DisplayName("keeps accents and does not change harmless text")
    void keepsAccentsAndHarmlessText() {
        String dataLine = dataLine(recordResponse("ARTERIAL", 10, "Obra", "Céu limpo", 1L, "Avenida São João"));

        assertThat(dataLine).contains(",ARTERIAL,10,Obra,Céu limpo,").endsWith(",1,Avenida São João");
    }

    @Test
    @DisplayName("does not alter numbers, ids or timestamps")
    void doesNotAlterNumbersIdsAndTimestamps() {
        UUID id = UUID.randomUUID();
        UUID streetId = UUID.randomUUID();

        String dataLine = dataLine(new TrafficRecordResponse(
                id, OffsetDateTime.of(2024, 6, 17, 8, 30, 0, 0, ZoneOffset.UTC),
                "LOCAL", 120, null, null, streetId, 55L, "Rua Um"));

        assertThat(dataLine).isEqualTo(id + ",2024-06-17T08:30Z,LOCAL,120,,," + streetId + ",55,Rua Um");
    }

    // ---- which records are exported ----

    @Test
    @DisplayName("without ids, exports the most recent records up to the limit and reports the total")
    void exportsTheMostRecentUpToTheLimit() {
        List<TrafficRecordResponse> latest = records(3);
        when(trafficRecordService.countAll()).thenReturn(12_345L);
        when(trafficRecordService.findLatest(3)).thenReturn(latest);

        ExportService.ExportResult result = exportService.export(null);

        assertThat(result.records()).isEqualTo(latest);
        assertThat(result.total()).isEqualTo(12_345L);
        verify(trafficRecordService, never()).findByIds(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("treats an empty id list as no selection")
    void treatsEmptyIdsAsNoSelection() {
        when(trafficRecordService.countAll()).thenReturn(2L);
        when(trafficRecordService.findLatest(3)).thenReturn(records(2));

        ExportService.ExportResult result = exportService.export(List.of());

        assertThat(result.records()).hasSize(2);
        assertThat(result.total()).isEqualTo(2L);
    }

    @Test
    @DisplayName("with ids, exports only the selected records")
    void exportsTheSelectedRecords() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<TrafficRecordResponse> selected = records(2);
        when(trafficRecordService.findByIds(List.of(first, second))).thenReturn(selected);

        ExportService.ExportResult result = exportService.export(List.of(first, second, first));

        assertThat(result.records()).isEqualTo(selected);
        assertThat(result.total()).isEqualTo(2L);
        verify(trafficRecordService, never()).countAll();
        verify(trafficRecordService, never()).findLatest(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("accepts a selection with exactly the limit and rejects a larger one before reading the database")
    void rejectsASelectionLargerThanTheLimit() {
        List<UUID> atLimit = IntStream.range(0, 3).mapToObj(i -> UUID.randomUUID()).toList();
        when(trafficRecordService.findByIds(atLimit)).thenReturn(records(3));
        assertThat(exportService.export(atLimit).records()).hasSize(3);

        List<UUID> overLimit = IntStream.range(0, 4).mapToObj(i -> UUID.randomUUID()).toList();
        assertThatThrownBy(() -> exportService.export(overLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4 registros")
                .hasMessageContaining("limite")
                .hasMessageContaining("3");
        verify(trafficRecordService, never()).findByIds(overLimit);
    }

    @Test
    @DisplayName("counts the distinct ids against the limit")
    void countsDistinctIdsAgainstTheLimit() {
        UUID id = UUID.randomUUID();
        List<UUID> repeated = List.of(id, id, id, id, id);
        when(trafficRecordService.findByIds(List.of(id))).thenReturn(records(1));

        assertThat(exportService.export(repeated).records()).hasSize(1);
    }

    @Test
    @DisplayName("does not touch the database to build a csv")
    void buildingTheCsvDoesNotReadTheDatabase() {
        exportService.toCsv(records(2));

        verifyNoInteractions(trafficRecordService);
    }

    private String dataLine(TrafficRecordResponse record) {
        return exportService.toCsv(List.of(record)).lines().skip(1).findFirst().orElseThrow();
    }

    private List<TrafficRecordResponse> records(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> recordResponse("LOCAL", 10 + i, null, null, 100L + i, "Rua " + i))
                .toList();
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
