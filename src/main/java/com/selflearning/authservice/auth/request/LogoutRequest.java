package com.selflearning.authservice.auth.request;

public record LogoutRequest(
        /**
         * 刷新令牌，用于登出时同步注销 refresh token
         */
        String refreshToken
) {
}
