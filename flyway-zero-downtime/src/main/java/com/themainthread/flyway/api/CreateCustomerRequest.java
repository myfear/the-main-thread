package com.themainthread.flyway.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank @Email String email,
        @NotBlank String displayName) {
}
