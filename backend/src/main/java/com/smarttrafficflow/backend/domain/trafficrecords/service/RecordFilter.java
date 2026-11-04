package com.smarttrafficflow.backend.domain.trafficrecords.service;

import com.smarttrafficflow.backend.domain.analytics.service.RoadTypes;
import com.smarttrafficflow.backend.domain.analytics.service.WeatherTypes;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * What the table filter ("Filtro") looks for. The text is compared with what is stored (road type, street name,
 * weather and event). But the screen shows the weather and the road type with other words than the codes stored by
 * the simulation ("Chuva" for {@code RAIN}, "Via expressa" for {@code HIGHWAY}), so the term is also turned into the
 * codes whose name on the screen contains it. The comparison with the names ignores case and accents, and a piece
 * of a word is enough.
 *
 * <p>The code lists are never null; they are empty when the term is no name on the screen.
 *
 * @param textPattern   a {@code LIKE} pattern (trimmed, lower case, between percent signs), or null without a term
 * @param weatherCodes  weather codes whose name contains the term
 * @param roadTypeCodes road type codes whose name contains the term
 */
public record RecordFilter(String textPattern, List<String> weatherCodes, List<String> roadTypeCodes) {

    private static final RecordFilter NONE = new RecordFilter(null, List.of(), List.of());

    public static RecordFilter of(String query) {
        if (query == null) {
            return NONE;
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty()) {
            return NONE;
        }
        String term = normalize(trimmed);
        return new RecordFilter(
                "%" + trimmed.toLowerCase() + "%",
                codesNamedWith(WeatherTypes.codes(), WeatherTypes::label, term),
                codesNamedWith(RoadTypes.codes(), RoadTypes::label, term));
    }

    private static List<String> codesNamedWith(List<String> codes, UnaryOperator<String> label, String term) {
        return codes.stream()
                .filter(code -> normalize(label.apply(code)).contains(term))
                .toList();
    }

    /** Lower case and no accents: "Não informado" and "NAO INFORMADO" become "nao informado". */
    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
