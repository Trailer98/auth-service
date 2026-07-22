package com.selflearning.authservice.auth.controller;

import com.selflearning.authservice.auth.request.TokenValidateRequest;
import com.selflearning.authservice.auth.response.TokenValidateResponse;
import com.selflearning.authservice.auth.service.AuthService;
import com.selflearning.authservice.common.web.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
public class InternalTokenController {

    private final AuthService authService;

    public InternalTokenController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Gateway 内部 JWT 鉴权校验接口。
     *
     * <p>优先使用请求体 token；未传请求体时兼容 Authorization: Bearer token。</p>
     *
     * @param authorizationHeader Authorization 请求头
     * @param request token 校验请求体
     * @return token 校验通过后的用户身份信息
     */
    @PostMapping("/token/validate")
    public ApiResponse<TokenValidateResponse> validate(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) TokenValidateRequest request) {
        String token = request == null || request.token() == null || request.token().isBlank()
                ? authorizationHeader
                : request.token();
        return ApiResponse.ok(authService.validateInternalAccessToken(token));
    }
}
