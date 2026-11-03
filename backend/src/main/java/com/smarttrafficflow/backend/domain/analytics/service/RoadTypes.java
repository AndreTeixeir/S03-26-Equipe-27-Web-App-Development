package com.smarttrafficflow.backend.domain.analytics.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Road type codes and the fixed order in which they are presented.
 */
public final class RoadTypes {

    public static final String UNKNOWN = "UNKNOWN";

    private static final List<String> KNOWN_IN_ORDER = List.of("LOCAL", "ARTERIAL", "HIGHWAY");

    private RoadTypes() {
    }

    /**
     * Orders the road types that are present: {@code LOCAL}, {@code ARTERIAL}, {@code HIGHWAY}, then any other
     * value in alphabetical order (the API accepts free text, and its volume must not be dropped), and
     * {@code UNKNOWN} last. Road types without records are not invented.
     */
    public static List<String> inPresentationOrder(Collection<String> present) {
        List<String> ordered = new ArrayList<>();
        for (String known : KNOWN_IN_ORDER) {
            if (present.contains(known)) {
                ordered.add(known);
            }
        }
        present.stream()
                .filter(type -> !KNOWN_IN_ORDER.contains(type) && !UNKNOWN.equals(type))
                .sorted()
                .forEach(ordered::add);
        if (present.contains(UNKNOWN)) {
            ordered.add(UNKNOWN);
        }
        return ordered;
    }
}
