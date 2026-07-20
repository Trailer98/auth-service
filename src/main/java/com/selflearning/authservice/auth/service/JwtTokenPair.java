package com.selflearning.authservice.auth.service;

import java.time.Instant;

public record JwtTokenPair(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt
) {
}
