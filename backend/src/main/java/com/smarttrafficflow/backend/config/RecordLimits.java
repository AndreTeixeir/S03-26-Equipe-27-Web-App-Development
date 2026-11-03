package com.smarttrafficflow.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ceilings on how many records one request may carry.
 * <ul>
 *   <li>{@code app.export.max-records} ({@code APP_EXPORT_MAX_RECORDS}, default 10000): records in an export, ids
 *       collected for "select all filtered", and ids accepted in the body of an export.</li>
 *   <li>{@code app.map.max-records} ({@code APP_MAP_MAX_RECORDS}, default 250): most recent records shown on the
 *       map when nothing is selected.</li>
 * </ul>
 * Values are checked when the application starts. The upper bound keeps {@code IN (...)} lists under the 65535 bind
 * parameters the PostgreSQL driver accepts.
 */
@Component
public class RecordLimits {

    static final int MIN = 1;
    static final int MAX = 50_000;

    private final int exportMaxRecords;
    private final int mapMaxRecords;

    public RecordLimits(
            @Value("${app.export.max-records}") String exportMaxRecords,
            @Value("${app.map.max-records}") String mapMaxRecords
    ) {
        this.exportMaxRecords = parse("APP_EXPORT_MAX_RECORDS", exportMaxRecords);
        this.mapMaxRecords = parse("APP_MAP_MAX_RECORDS", mapMaxRecords);
    }

    public int exportMaxRecords() {
        return exportMaxRecords;
    }

    public int mapMaxRecords() {
        return mapMaxRecords;
    }

    static int parse(String variable, String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            if (parsed >= MIN && parsed <= MAX) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the message below
        }
        throw new IllegalStateException(
                variable + " inválida: '" + value + "'. Use um número inteiro entre " + MIN + " e " + MAX + ".");
    }
}
