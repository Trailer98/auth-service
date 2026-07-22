package com.selflearning.authservice.auth.response;

import java.time.Instant;

public record TokenValidateResponse(
        boolean valid,
        Long userId,
        String username,
        String tokenId,
        Instant expiresAt
) {
}
