package com.selflearning.authservice.permission.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PermissionCreateRequest(
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9:_\\-.]*$")
        String permissionCode,

        @NotBlank
        @Size(max = 128)
        String permissionName,

        @NotBlank
        @Size(max = 32)
        String permissionType,

        Long parentId,

        @Size(max = 255)
        String path,

        @Size(max = 255)
        String component,

        Integer sortOrder,

        @Min(0)
        @Max(1)
        Integer status
) {
}
