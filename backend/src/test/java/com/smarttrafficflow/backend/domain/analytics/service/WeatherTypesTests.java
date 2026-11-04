package com.smarttrafficflow.backend.domain.analytics.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The labels must be the ones the screen shows (frontend/latest-app/src/lib/labels.ts, {@code weatherLabels}), because
 * the table search finds records by what the screen shows. The frontend test fixes the same words.
 */
@DisplayName("WeatherTypes tests")
class WeatherTypesTests {

    @Test
    @DisplayName("names the weather codes of the simulation as the screen does")
    void namesTheWeatherCodesAsTheScreenDoes() {
        assertThat(WeatherTypes.label("SUNNY")).isEqualTo("Ensolarado");
        assertThat(WeatherTypes.label("RAIN")).isEqualTo("Chuva");
        assertThat(WeatherTypes.label("CLOUDY")).isEqualTo("Nublado");
    }

    @Test
    @DisplayName("names a code in lower case too, as the screen does")
    void namesACodeInLowerCaseToo() {
        assertThat(WeatherTypes.label("sunny")).isEqualTo("Ensolarado");
        assertThat(WeatherTypes.label("rain")).isEqualTo("Chuva");
    }

    @Test
    @DisplayName("leaves free text exactly as it was registered")
    void leavesFreeTextAsItWasRegistered() {
        assertThat(WeatherTypes.label("Céu limpo")).isEqualTo("Céu limpo");
        assertThat(WeatherTypes.label("Chuva forte")).isEqualTo("Chuva forte");
    }

    @Test
    @DisplayName("lists the codes that have a label, in the order of the simulation")
    void listsTheCodesThatHaveALabel() {
        assertThat(WeatherTypes.codes()).containsExactly("SUNNY", "RAIN", "CLOUDY");
    }
}
