package com.selflearning.authservice.auth.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth")
public record AuthPermissionCacheProperties(
        /**
         * 用户权限上下文 Redis 缓存有效期
         */
        @NotNull Duration permissionCacheTtl
) {
}
