package io.mainthread.catalogboard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank @Size(max = 32) String sku,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) String category,
        @Min(0) int stock,
        @Min(0) int reorderPoint) {
}
