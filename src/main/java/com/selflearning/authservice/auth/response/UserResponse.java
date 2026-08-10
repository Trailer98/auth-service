package com.selflearning.authservice.auth.response;

import com.selflearning.authservice.auth.domain.AuthUser;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String nickname,
        String email,
        String phone,
        Integer status,
        LocalDateTime lastLoginTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse from(AuthUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.getLastLoginTime(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
