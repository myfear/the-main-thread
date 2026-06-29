package dev.mainthread.incidents;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record IncidentInput(
        String id,
        @NotBlank String service,
        @NotBlank String environment,
        @NotBlank String exceptionType,
        @NotBlank String message,
        @NotEmpty @Size(max = 20) List<@NotBlank String> stackTrace,
        String resolvedBy,
        String incidentUrl) {
}
