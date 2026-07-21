package com.selflearning.authservice.role.response;

import com.selflearning.authservice.role.domain.AuthRole;
import java.time.LocalDateTime;

public record RoleResponse(
        Long id,
        String applicationCode,
        String roleCode,
        String roleName,
        String description,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static RoleResponse from(AuthRole role) {
        return new RoleResponse(
                role.getId(),
                role.getApplicationCode(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                role.getStatus(),
                role.getCreatedAt(),
                role.getUpdatedAt());
    }
}
