package com.selflearning.authservice.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(max = 128)
        String nickname,

        @Email
        @Size(max = 128)
        String email,

        @Size(max = 32)
        String phone,

        @Size(min = 6, max = 128)
        String password,

        @Min(0)
        @Max(1)
        Integer status
) {
}
