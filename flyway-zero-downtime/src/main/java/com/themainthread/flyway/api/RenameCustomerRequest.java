package com.themainthread.flyway.api;

import jakarta.validation.constraints.NotBlank;

public record RenameCustomerRequest(@NotBlank String displayName) {
}
