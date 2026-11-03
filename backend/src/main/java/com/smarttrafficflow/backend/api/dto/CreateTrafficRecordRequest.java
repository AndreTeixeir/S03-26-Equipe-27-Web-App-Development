package com.smarttrafficflow.backend.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * The text limits match the database columns (road_type 100, event_type 150, weather 100), so a text that does not
 * fit is a validation error (400) instead of a failed insert (500).
 */
public record CreateTrafficRecordRequest(
        @NotNull OffsetDateTime timestamp,
        @NotBlank @Size(max = 100, message = "deve ter no máximo 100 caracteres") String roadType,
        @NotNull @Min(0) Integer vehicleVolume,
        @Size(max = 150, message = "deve ter no máximo 150 caracteres") String eventType,
        @Size(max = 100, message = "deve ter no máximo 100 caracteres") String weather,
        @NotNull @Positive Long streetOsmWayId
) {
}
