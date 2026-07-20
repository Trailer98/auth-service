package com.selflearning.authservice.auth.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        /**
         * 用户名
         */
        @NotBlank String username,

        /**
         * 登录密码明文，仅用于本次登录校验
         */
        @NotBlank String password
) {
}
