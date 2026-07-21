package com.selflearning.authservice.role.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoleUpdateRequest(
        @NotBlank
        @Size(max = 128)
        String roleName,

        @Size(max = 512)
        String description,

        @Min(0)
        @Max(1)
        Integer status
) {
}
