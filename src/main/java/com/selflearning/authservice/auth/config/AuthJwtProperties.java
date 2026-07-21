package com.selflearning.authservice.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public record AuthJwtProperties(
        /**
         * JWT 签发方，用于生成和校验令牌的 issuer 声明
         */
        @NotBlank String issuer,

        /**
         * JWT 签名密钥，HS256 至少需要 32 字节
         */
        @NotBlank String secret,

        /**
         * 访问令牌有效期
         */
        @NotNull Duration accessTokenTtl,

        /**
         * 刷新令牌有效期
         */
        @NotNull Duration refreshTokenTtl
) {
}
