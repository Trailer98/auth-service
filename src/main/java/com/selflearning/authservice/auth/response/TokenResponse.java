package com.selflearning.authservice.auth.response;

import java.time.Instant;

public record TokenResponse(
        /**
         * JWT 访问令牌
         */
        String accessToken,

        /**
         * 刷新令牌
         */
        String refreshToken,

        /**
         * 访问令牌过期时间
         */
        Instant accessTokenExpiresAt,

        /**
         * 刷新令牌过期时间
         */
        Instant refreshTokenExpiresAt
) {
}
