package com.selflearning.authservice.application.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ApplicationStatusRequest(
        @NotNull
        @Min(0)
        @Max(1)
        Integer status
) {
}
