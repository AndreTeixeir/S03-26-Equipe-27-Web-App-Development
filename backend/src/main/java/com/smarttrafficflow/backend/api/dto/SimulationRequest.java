package com.smarttrafficflow.backend.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The scenario name is stored as the event type of each record (column of 150 characters). */
public record SimulationRequest(
        @NotNull @Min(1) @Max(250) Integer recordsToGenerate,
        @Size(max = 150, message = "deve ter no máximo 150 caracteres") String scenarioName
) {
}
