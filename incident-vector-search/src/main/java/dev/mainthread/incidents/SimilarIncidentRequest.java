package dev.mainthread.incidents;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SimilarIncidentRequest(
        @NotNull @Valid IncidentInput incident,
        @Min(1) @Max(20) Integer limit,
        @DecimalMin("0.0") @DecimalMax("1.0") Float minScore,
        String filterService,
        String filterEnvironment,
        Boolean onlyResolved) {
}
