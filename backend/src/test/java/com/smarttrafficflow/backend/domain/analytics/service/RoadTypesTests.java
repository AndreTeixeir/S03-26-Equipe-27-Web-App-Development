package com.smarttrafficflow.backend.domain.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The labels must be the ones the screen shows (frontend/latest-app/src/lib/labels.ts, {@code roadTypeLabels}), because
 * the table search finds records by what the screen shows. The frontend test fixes the same words.
 */
@DisplayName("RoadTypes tests")
class RoadTypesTests {

    @Test
    @DisplayName("names the road types as the screen does")
    void namesTheRoadTypesAsTheScreenDoes() {
        assertThat(RoadTypes.label("LOCAL")).isEqualTo("Via local");
        assertThat(RoadTypes.label("ARTERIAL")).isEqualTo("Via arterial");
        assertThat(RoadTypes.label("HIGHWAY")).isEqualTo("Via expressa");
        assertThat(RoadTypes.label("UNKNOWN")).isEqualTo("Não informado");
    }

    @Test
    @DisplayName("shows a free-text road type as it was registered")
    void showsAFreeTextRoadTypeAsItWasRegistered() {
        assertThat(RoadTypes.label("RURAL")).isEqualTo("RURAL");
    }

    @Test
    @DisplayName("lists the codes that have a label, in the presentation order")
    void listsTheCodesThatHaveALabel() {
        assertThat(RoadTypes.codes()).containsExactly("LOCAL", "ARTERIAL", "HIGHWAY", "UNKNOWN");
    }
}
