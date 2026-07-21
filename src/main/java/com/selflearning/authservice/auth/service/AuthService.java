package com.selflearning.authservice.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.selflearning.authservice.auth.request.LoginRequest;
import com.selflearning.authservice.auth.request.LogoutRequest;
import com.selflearning.authservice.auth.request.RefreshRequest;
import com.selflearning.authservice.auth.response.LoginResponse;
import com.selflearning.authservice.auth.response.TokenResponse;
import com.selflearning.authservice.auth.response.UserProfile;
import com.selflearning.authservice.auth.domain.AuthLoginLog;
import com.selflearning.authservice.auth.domain.AuthTokenBlacklist;
import com.selflearning.authservice.auth.domain.AuthUser;
import com.selflearning.authservice.auth.mapper.AuthLoginLogMapper;
import com.selflearning.authservice.auth.mapper.AuthTokenBlacklistMapper;
import com.selflearning.authservice.auth.mapper.AuthUserMapper;
import com.selflearning.authservice.common.web.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int USER_STATUS_ENABLED = 1;
    private static final int LOGIN_SUCCESS = 1;
    private static final int LOGIN_FAILED = 0;

    private final AuthUserMapper userMapper;
    private final AuthLoginLogMapper loginLogMapper;
    private final AuthTokenBlacklistMapper tokenBlacklistMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStoreService tokenStoreService;

    public AuthService(
            AuthUserMapper userMapper,
            AuthLoginLogMapper loginLogMapper,
            AuthTokenBlacklistMapper tokenBlacklistMapper,
            BCryptPasswordEncoder passwordEncoder,
            JwtService jwtService,
            TokenStoreService tokenStoreService) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.tokenBlacklistMapper = tokenBlacklistMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenStoreService = tokenStoreService;
    }

    /**
     * 处理用户名密码登录。
     *
     * <p>登录时会按用户名查询未删除用户，使用 BCrypt 校验密码，校验用户状态，
     * 记录登录成功或失败日志，并在成功后签发 access token 和 refresh token。</p>
     *
     * @param request 登录请求参数
     * @param servletRequest HTTP 请求对象，用于记录登录 IP 和 User-Agent
     * @return 登录结果，包含令牌、过期时间和当前用户信息
     */
    public LoginResponse login(LoginRequest request, HttpServletRequest servletRequest) {
        AuthUser user = findActiveUserByUsername(request.username());
        // 验证账号密码
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            writeLoginLog(null, request.username(), LOGIN_FAILED, "Invalid username or password", servletRequest);
            throw new UnauthorizedException("Invalid username or password");
        }
        // 验证用户是否启用
        if (!Integer.valueOf(USER_STATUS_ENABLED).equals(user.getStatus())) {
            writeLoginLog(user.getId(), request.username(), LOGIN_FAILED, "User is disabled", servletRequest);
            throw new UnauthorizedException("User is disabled");
        }

        // 更新用户登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);
        writeLoginLog(user.getId(), user.getUsername(), LOGIN_SUCCESS, null, servletRequest);

        // 签发令牌
        JwtTokenPair tokenPair = issueTokenPair(user);
        return new LoginResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.accessTokenExpiresAt(),
                tokenPair.refreshTokenExpiresAt(),
                UserProfile.from(user));
    }

    /**
     * 查询当前登录用户信息。
     *
     * <p>该方法会从 Authorization 请求头解析 Bearer token，校验 JWT 签名、过期时间和 Redis 黑名单，
     * 再根据 token 中的用户 ID 查询用户基础资料。</p>
     *
     * @param authorizationHeader Authorization 请求头
     * @return 当前用户信息
     */
    public UserProfile me(String authorizationHeader) {
        AuthenticatedUser authenticatedUser = authenticate(authorizationHeader);
        AuthUser user = userMapper.selectById(authenticatedUser.userId());
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new UnauthorizedException("User not found");
        }
        if (!Integer.valueOf(USER_STATUS_ENABLED).equals(user.getStatus())) {
            throw new UnauthorizedException("User is disabled");
        }
        return UserProfile.from(user);
    }

    /**
     * 处理登出。
     *
     * <p>登出时会校验 access token，将 access token 的唯一标识写入 Redis 黑名单，
     * 同时持久化到数据库黑名单表；如果请求中传入 refresh token，会同步从 Redis 中删除。</p>
     *
     * @param authorizationHeader Authorization 请求头
     * @param request 登出请求参数，可选携带 refresh token
     */
    @Transactional
    public void logout(String authorizationHeader, LogoutRequest request) {
        AuthenticatedUser authenticatedUser = authenticate(authorizationHeader);
        Instant expiresAt = Instant.ofEpochSecond(authenticatedUser.expiresAtEpochSecond());
        tokenStoreService.blacklistAccessToken(authenticatedUser.tokenId(), expiresAt);
        persistTokenBlacklist(authenticatedUser.tokenId(), authenticatedUser.userId(), expiresAt);
        if (request != null) {
            tokenStoreService.revokeRefreshToken(request.refreshToken());
        }
    }

    /**
     * 刷新登录令牌。
     *
     * <p>refresh token 保存在 Redis 中，并在刷新时被原子消费，避免同一个 refresh token 重复换发。
     * 换发前会校验用户是否仍然存在且处于启用状态。</p>
     *
     * @param request 刷新令牌请求参数
     * @return 新的 access token、refresh token 及其过期时间
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Long userId = tokenStoreService.consumeRefreshToken(request.refreshToken());
        if (userId == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        AuthUser user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !Integer.valueOf(USER_STATUS_ENABLED).equals(user.getStatus())) {
            throw new UnauthorizedException("User is unavailable");
        }
        JwtTokenPair tokenPair = issueTokenPair(user);
        return new TokenResponse(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.accessTokenExpiresAt(),
                tokenPair.refreshTokenExpiresAt());
    }

    /**
     * 校验 Authorization 请求头中的 access token。
     *
     * @param authorizationHeader Authorization 请求头
     * @return access token 中解析出的认证用户信息
     */
    private AuthenticatedUser authenticate(String authorizationHeader) {
        String accessToken = resolveBearerToken(authorizationHeader);
        AuthenticatedUser authenticatedUser = jwtService.parseAccessToken(accessToken);
        if (tokenStoreService.isAccessTokenBlacklisted(authenticatedUser.tokenId())) {
            throw new UnauthorizedException("Access token has been revoked");
        }
        return authenticatedUser;
    }

    /**
     * 为指定用户签发 access token 和 refresh token。
     *
     * @param user 登录用户
     * @return access token 与 refresh token 组合
     */
    private JwtTokenPair issueTokenPair(AuthUser user) {
        JwtService.AccessTokenIssue accessToken = jwtService.issueAccessToken(user.getId(), user.getUsername());
        TokenStoreService.RefreshTokenIssue refreshToken = tokenStoreService.issueRefreshToken(user.getId());
        return new JwtTokenPair(accessToken.token(), refreshToken.token(), accessToken.expiresAt(), refreshToken.expiresAt());
    }

    /**
     * 根据用户名查询未逻辑删除的用户。
     *
     * @param username 用户名
     * @return 用户实体，不存在时返回 null
     */
    private AuthUser findActiveUserByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getUsername, username)
                .eq(AuthUser::getDeleted, false)
                .last("LIMIT 1"));
    }

    /**
     * 从 Authorization 请求头中提取 Bearer token。
     *
     * @param authorizationHeader Authorization 请求头
     * @return access token 字符串
     */
    private String resolveBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new UnauthorizedException("Missing bearer token");
        }
        return token;
    }

    /**
     * 写入登录日志。
     *
     * @param userId 用户ID，登录失败且用户不存在时为空
     * @param username 用户名
     * @param loginStatus 登录状态，1 表示成功，0 表示失败
     * @param failReason 失败原因，登录成功时为空
     * @param request HTTP 请求对象，用于提取客户端信息
     */
    private void writeLoginLog(
            Long userId,
            String username,
            int loginStatus,
            String failReason,
            HttpServletRequest request) {
        AuthLoginLog loginLog = new AuthLoginLog();
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setLoginIp(resolveClientIp(request));
        loginLog.setUserAgent(truncate(request.getHeader("User-Agent"), 512));
        loginLog.setLoginStatus(loginStatus);
        loginLog.setFailReason(truncate(failReason, 255));
        loginLogMapper.insert(loginLog);
    }

    /**
     * 持久化 access token 黑名单记录。
     *
     * @param tokenId JWT 唯一标识
     * @param userId 用户ID
     * @param expiresAt token 过期时间
     */
    private void persistTokenBlacklist(String tokenId, Long userId, Instant expiresAt) {
        AuthTokenBlacklist tokenBlacklist = new AuthTokenBlacklist();
        tokenBlacklist.setTokenId(tokenId);
        tokenBlacklist.setUserId(userId);
        tokenBlacklist.setExpireTime(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()));
        try {
            tokenBlacklistMapper.insert(tokenBlacklist);
        } catch (DuplicateKeyException ignored) {
            // Logout should remain idempotent if the same access token is submitted twice.
        }
    }

    /**
     * 解析客户端 IP。
     *
     * <p>优先读取反向代理传入的 X-Forwarded-For，其次读取 X-Real-IP，
     * 最后回退到 servlet 容器提供的远端地址。</p>
     *
     * @param request HTTP 请求对象
     * @return 客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return truncate(forwardedFor.split(",")[0].trim(), 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp, 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    /**
     * 按最大长度截断字符串。
     *
     * @param value 原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
