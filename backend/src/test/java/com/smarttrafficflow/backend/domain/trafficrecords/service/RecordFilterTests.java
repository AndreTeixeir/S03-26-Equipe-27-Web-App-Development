package com.smarttrafficflow.backend.domain.trafficrecords.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecordFilter tests")
class RecordFilterTests {

    @Test
    @DisplayName("has no pattern and no codes when there is no term")
    void hasNothingWhenThereIsNoTerm() {
        for (String term : new String[]{null, "", "   "}) {
            RecordFilter filter = RecordFilter.of(term);

            assertThat(filter.textPattern()).as("term '%s'", term).isNull();
            assertThat(filter.weatherCodes()).as("term '%s'", term).isEmpty();
            assertThat(filter.roadTypeCodes()).as("term '%s'", term).isEmpty();
        }
    }

    @Test
    @DisplayName("keeps the text pattern it always had: trimmed, lower case, between percent signs")
    void keepsTheTextPatternItAlwaysHad() {
        assertThat(RecordFilter.of("  Norte ").textPattern()).isEqualTo("%norte%");
        assertThat(RecordFilter.of("Não Informado").textPattern()).isEqualTo("%não informado%");
    }

    @Test
    @DisplayName("turns the weather words of the screen into codes, in any case")
    void turnsTheWeatherWordsIntoCodes() {
        assertThat(RecordFilter.of("chuva").weatherCodes()).containsExactly("RAIN");
        assertThat(RecordFilter.of("CHUVA").weatherCodes()).containsExactly("RAIN");
        assertThat(RecordFilter.of("  Chuva ").weatherCodes()).containsExactly("RAIN");
        assertThat(RecordFilter.of("Ensolarado").weatherCodes()).containsExactly("SUNNY");
        assertThat(RecordFilter.of("nublado").weatherCodes()).containsExactly("CLOUDY");
        assertThat(RecordFilter.of("chuva").roadTypeCodes()).isEmpty();
    }

    @Test
    @DisplayName("turns the road type words of the screen into codes")
    void turnsTheRoadTypeWordsIntoCodes() {
        assertThat(RecordFilter.of("via expressa").roadTypeCodes()).containsExactly("HIGHWAY");
        assertThat(RecordFilter.of("Via Arterial").roadTypeCodes()).containsExactly("ARTERIAL");
        assertThat(RecordFilter.of("VIA LOCAL").roadTypeCodes()).containsExactly("LOCAL");
        assertThat(RecordFilter.of("via expressa").weatherCodes()).isEmpty();
    }

    @Test
    @DisplayName("matches part of a word: 'chu' is the rain, 'via' is the three road types, 'local' is LOCAL")
    void matchesPartOfAWord() {
        assertThat(RecordFilter.of("chu").weatherCodes()).containsExactly("RAIN");
        assertThat(RecordFilter.of("via").roadTypeCodes()).containsExactly("LOCAL", "ARTERIAL", "HIGHWAY");
        assertThat(RecordFilter.of("local").roadTypeCodes()).containsExactly("LOCAL");
    }

    @Test
    @DisplayName("ignores the accents of the term and of the label")
    void ignoresTheAccents() {
        assertThat(RecordFilter.of("não informado").roadTypeCodes()).containsExactly("UNKNOWN");
        assertThat(RecordFilter.of("nao informado").roadTypeCodes()).containsExactly("UNKNOWN");
        assertThat(RecordFilter.of("NÃO INFORMADO").roadTypeCodes()).containsExactly("UNKNOWN");
    }

    @Test
    @DisplayName("lists every code whose label contains the term, in the order of the codes")
    void listsEveryCodeWhoseLabelContainsTheTerm() {
        // "o": Ensolarado, Nublado | Via local, Não informado
        RecordFilter filter = RecordFilter.of("o");

        assertThat(filter.weatherCodes()).containsExactly("SUNNY", "CLOUDY");
        assertThat(filter.roadTypeCodes()).containsExactly("LOCAL", "UNKNOWN");
    }

    @Test
    @DisplayName("has no codes, but is not empty, when the term is no label: codes, free text and street names")
    void hasNoCodesWhenTheTermIsNoLabel() {
        for (String term : new String[]{"rain", "highway", "xyzabc", "flores", "Avenida São João"}) {
            RecordFilter filter = RecordFilter.of(term);

            assertThat(filter.textPattern()).as("term '%s'", term).isEqualTo("%" + term.toLowerCase() + "%");
            assertThat(filter.weatherCodes()).as("term '%s'", term).isEmpty();
            assertThat(filter.roadTypeCodes()).as("term '%s'", term).isEmpty();
        }
    }
}
