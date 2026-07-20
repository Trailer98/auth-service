package com.selflearning.authservice.auth.controller;

import com.selflearning.authservice.auth.request.LoginRequest;
import com.selflearning.authservice.auth.request.LogoutRequest;
import com.selflearning.authservice.auth.request.RefreshRequest;
import com.selflearning.authservice.auth.response.LoginResponse;
import com.selflearning.authservice.auth.response.TokenResponse;
import com.selflearning.authservice.auth.response.UserProfile;
import com.selflearning.authservice.auth.service.AuthService;
import com.selflearning.authservice.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录接口。
     *
     * <p>接收用户名和密码，校验通过后返回 access token、refresh token 和当前用户信息。</p>
     *
     * @param request 登录请求参数
     * @param servletRequest HTTP 请求对象，用于记录登录日志中的客户端信息
     * @return 登录结果
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request, servletRequest));
    }

    /**
     * 查询当前登录用户信息接口。
     *
     * <p>通过 Authorization 请求头中的 Bearer access token 识别当前用户。</p>
     *
     * @param authorizationHeader Authorization 请求头
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserProfile> me(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return ApiResponse.ok(authService.me(authorizationHeader));
    }

    /**
     * 用户登出接口。
     *
     * <p>通过 Authorization 请求头注销当前 access token；请求体可选传入 refresh token 并同步注销。</p>
     *
     * @param authorizationHeader Authorization 请求头
     * @param request 登出请求参数，可选携带 refresh token
     * @return 空响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) LogoutRequest request) {
        authService.logout(authorizationHeader, request);
        return ApiResponse.ok();
    }

    /**
     * 刷新令牌接口。
     *
     * <p>使用 refresh token 换发新的 access token 和 refresh token。</p>
     *
     * @param request 刷新令牌请求参数
     * @return 新令牌信息
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }
}
