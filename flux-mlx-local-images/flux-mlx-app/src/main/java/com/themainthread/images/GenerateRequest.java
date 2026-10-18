package com.themainthread.images;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateRequest(
        @NotBlank @Size(max = 4000) String prompt,
        @Min(256) @Max(1024) int width,
        @Min(256) @Max(1024) int height,
        @Min(0) long seed) {

    @AssertTrue(message = "width and height must be multiples of 64")
    public boolean isAligned() {
        return width % 64 == 0 && height % 64 == 0;
    }
}
