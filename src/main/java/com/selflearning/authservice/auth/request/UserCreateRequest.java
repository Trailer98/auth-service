package com.selflearning.authservice.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_\\-]*$")
        String username,

        @NotBlank
        @Size(min = 6, max = 128)
        String password,

        @Size(max = 128)
        String nickname,

        @Email
        @Size(max = 128)
        String email,

        @Size(max = 32)
        String phone,

        @Min(0)
        @Max(1)
        Integer status
) {
}
