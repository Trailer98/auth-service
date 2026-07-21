package com.selflearning.authservice.permission.response;

import com.selflearning.authservice.permission.domain.AuthPermission;
import java.time.LocalDateTime;

public record PermissionResponse(
        Long id,
        String applicationCode,
        String permissionCode,
        String permissionName,
        String permissionType,
        Long parentId,
        String path,
        String component,
        Integer sortOrder,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PermissionResponse from(AuthPermission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getApplicationCode(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getPermissionType(),
                permission.getParentId(),
                permission.getPath(),
                permission.getComponent(),
                permission.getSortOrder(),
                permission.getStatus(),
                permission.getCreatedAt(),
                permission.getUpdatedAt());
    }
}
