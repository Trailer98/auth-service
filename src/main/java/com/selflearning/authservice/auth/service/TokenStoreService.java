package com.selflearning.authservice.auth.service;

import com.selflearning.authservice.auth.config.AuthJwtProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenStoreService {

    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final AuthJwtProperties properties;

    public TokenStoreService(StringRedisTemplate redisTemplate, AuthJwtProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public RefreshTokenIssue issueRefreshToken(Long userId) {
        String refreshToken = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(properties.refreshTokenTtl());
        redisTemplate.opsForValue().set(refreshKey(refreshToken), String.valueOf(userId), properties.refreshTokenTtl());
        return new RefreshTokenIssue(refreshToken, expiresAt);
    }

    public Long consumeRefreshToken(String refreshToken) {
        String key = refreshKey(refreshToken);
        String userId = redisTemplate.opsForValue().getAndDelete(key);
        if (userId == null) {
            return null;
        }
        return Long.valueOf(userId);
    }

    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisTemplate.delete(refreshKey(refreshToken));
        }
    }

    public void blacklistAccessToken(String tokenId, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.opsForValue().set(blacklistKey(tokenId), "1", ttl);
        }
    }

    public boolean isAccessTokenBlacklisted(String tokenId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(tokenId)));
    }

    private String refreshKey(String refreshToken) {
        return REFRESH_PREFIX + refreshToken;
    }

    private String blacklistKey(String tokenId) {
        return BLACKLIST_PREFIX + tokenId;
    }

    public record RefreshTokenIssue(String token, Instant expiresAt) {
    }
}
