package com.selflearning.authservice.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.selflearning.authservice.auth.config.AuthJwtProperties;
import com.selflearning.authservice.auth.domain.AuthUser;
import com.selflearning.authservice.auth.mapper.AuthLoginLogMapper;
import com.selflearning.authservice.auth.mapper.AuthTokenBlacklistMapper;
import com.selflearning.authservice.auth.mapper.AuthUserMapper;
import com.selflearning.authservice.auth.response.TokenValidateResponse;
import com.selflearning.authservice.common.web.UnauthorizedException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

    private final AuthUserMapper userMapper = org.mockito.Mockito.mock(AuthUserMapper.class);
    private final AuthLoginLogMapper loginLogMapper = org.mockito.Mockito.mock(AuthLoginLogMapper.class);
    private final AuthTokenBlacklistMapper tokenBlacklistMapper = org.mockito.Mockito.mock(AuthTokenBlacklistMapper.class);
    private final TokenStoreService tokenStoreService = org.mockito.Mockito.mock(TokenStoreService.class);
    private final JwtService jwtService = new JwtService(new AuthJwtProperties(
            "auth-service",
            "test-jwt-secret-at-least-32-bytes-long",
            Duration.ofMinutes(15),
            Duration.ofDays(7)));
    private final AuthService authService = new AuthService(
            userMapper,
            loginLogMapper,
            tokenBlacklistMapper,
            new BCryptPasswordEncoder(),
            jwtService,
            tokenStoreService);

    @Test
    void validateInternalAccessTokenReturnsIdentityWhenTokenSessionAndUserAreValid() {
        JwtService.AccessTokenIssue tokenIssue = jwtService.issueAccessToken(1L, "admin");
        when(tokenStoreService.isAccessTokenBlacklisted(tokenIssue.tokenId())).thenReturn(false);
        when(tokenStoreService.isAccessTokenSessionValid(tokenIssue.tokenId(), 1L)).thenReturn(true);
        when(userMapper.selectById(1L)).thenReturn(enabledUser());

        TokenValidateResponse response = authService.validateInternalAccessToken("Bearer " + tokenIssue.token());

        assertThat(response.valid()).isTrue();
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.tokenId()).isEqualTo(tokenIssue.tokenId());
        assertThat(response.expiresAt().getEpochSecond()).isEqualTo(tokenIssue.expiresAt().getEpochSecond());
    }

    @Test
    void validateInternalAccessTokenRejectsInvalidSession() {
        JwtService.AccessTokenIssue tokenIssue = jwtService.issueAccessToken(1L, "admin");
        when(tokenStoreService.isAccessTokenBlacklisted(tokenIssue.tokenId())).thenReturn(false);
        when(tokenStoreService.isAccessTokenSessionValid(tokenIssue.tokenId(), 1L)).thenReturn(false);

        assertThatThrownBy(() -> authService.validateInternalAccessToken(tokenIssue.token()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session is invalid");
    }

    @Test
    void validateInternalAccessTokenRejectsDisabledUser() {
        JwtService.AccessTokenIssue tokenIssue = jwtService.issueAccessToken(1L, "admin");
        AuthUser user = enabledUser();
        user.setStatus(0);
        when(tokenStoreService.isAccessTokenBlacklisted(tokenIssue.tokenId())).thenReturn(false);
        when(tokenStoreService.isAccessTokenSessionValid(tokenIssue.tokenId(), 1L)).thenReturn(true);
        when(userMapper.selectById(1L)).thenReturn(user);

        assertThatThrownBy(() -> authService.validateInternalAccessToken(tokenIssue.token()))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("User is disabled");
    }

    private AuthUser enabledUser() {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setStatus(1);
        user.setDeleted(false);
        return user;
    }
}
