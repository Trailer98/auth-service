package com.selflearning.authservice.role.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RoleStatusRequest(
        @NotNull
        @Min(0)
        @Max(1)
        Integer status
) {
}
