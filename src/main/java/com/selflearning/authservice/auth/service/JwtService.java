package com.selflearning.authservice.auth.service;

import com.selflearning.authservice.auth.config.AuthJwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final AuthJwtProperties properties;
    private final SecretKey key;

    public JwtService(AuthJwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public AccessTokenIssue issueAccessToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(tokenId)
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new AccessTokenIssue(token, tokenId, expiresAt);
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"access".equals(claims.get("type", String.class))) {
            throw new JwtException("JWT token type is not access");
        }
        String subject = claims.getSubject();
        String tokenId = claims.getId();
        if (subject == null || subject.isBlank() || tokenId == null || tokenId.isBlank()) {
            throw new JwtException("JWT token is missing required claims");
        }
        return new AuthenticatedUser(
                Long.valueOf(subject),
                claims.get("username", String.class),
                tokenId,
                claims.getExpiration().toInstant().getEpochSecond());
    }

    public record AccessTokenIssue(String token, String tokenId, Instant expiresAt) {
    }
}
