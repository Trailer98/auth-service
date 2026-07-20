package com.selflearning.authservice.auth.service;

public record AuthenticatedUser(
        Long userId,
        String username,
        String tokenId,
        long expiresAtEpochSecond
) {
}
