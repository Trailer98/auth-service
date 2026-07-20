package com.selflearning.authservice.auth.response;

import java.time.Instant;

public record LoginResponse(
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
        Instant refreshTokenExpiresAt,

        /**
         * 当前登录用户信息
         */
        UserProfile user
) {
}
