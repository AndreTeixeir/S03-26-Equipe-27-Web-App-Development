package com.smarttrafficflow.backend.domain.analytics.service;

/**
 * Dimensions accepted by the {@code groupBy} parameter of the traffic statistics.
 */
public enum StatsGroupBy {

    HOUR("hour"),
    WEEKDAY("weekday"),
    ROAD_TYPE("roadType");

    private final String parameter;

    StatsGroupBy(String parameter) {
        this.parameter = parameter;
    }

    /**
     * Resolves the value received from the client. It must run before any access to the database, so an invalid
     * value is rejected even when there are no records.
     */
    public static StatsGroupBy parse(String value) {
        for (StatsGroupBy groupBy : values()) {
            if (groupBy.parameter.equals(value)) {
                return groupBy;
            }
        }
        throw new IllegalArgumentException("groupBy invalido: " + value + ". Valores aceitos: hour, weekday, roadType");
    }
}
