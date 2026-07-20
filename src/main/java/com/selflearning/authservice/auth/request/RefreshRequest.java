package com.selflearning.authservice.auth.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        /**
         * 刷新令牌，用于换发新的 access token 和 refresh token
         */
        @NotBlank String refreshToken
) {
}
