package com.selflearning.authservice.application.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApplicationCreateRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_\\-]*$")
        String applicationCode,

        @NotBlank
        @Size(max = 128)
        String applicationName,

        @Size(max = 512)
        String description,

        @Min(0)
        @Max(1)
        Integer status
) {
}
