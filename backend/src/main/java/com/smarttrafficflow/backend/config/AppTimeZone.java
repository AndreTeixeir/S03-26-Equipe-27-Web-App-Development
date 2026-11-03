package com.smarttrafficflow.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/**
 * Time zone in which hours and weekdays of the traffic records are observed (property {@code app.timezone},
 * environment variable {@code APP_TIMEZONE}).
 * <p>
 * The value is validated when the application starts: an unknown zone stops the startup with a clear message, instead
 * of failing only when the first statistics request reaches the database. Only identifiers of the time zone database
 * (such as {@code America/Sao_Paulo} or {@code UTC}) are accepted. Offsets like {@code UTC+3} are rejected because Java
 * and PostgreSQL would read their sign in opposite ways.
 */
@Component
public class AppTimeZone {

    private final String id;

    public AppTimeZone(@Value("${app.timezone}") String id) {
        if (id == null || !ZoneId.getAvailableZoneIds().contains(id)) {
            throw new IllegalStateException(
                    "Fuso horário inválido em APP_TIMEZONE: '" + id + "'. "
                            + "Use um identificador da base de fusos, por exemplo America/Sao_Paulo ou UTC.");
        }
        this.id = id;
    }

    public String id() {
        return id;
    }
}
