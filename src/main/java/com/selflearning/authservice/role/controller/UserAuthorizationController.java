package com.selflearning.authservice.role.controller;

import com.selflearning.authservice.role.request.UserRoleAssignRequest;
import com.selflearning.authservice.role.service.AuthorizationService;
import com.selflearning.authservice.permission.response.PermissionResponse;
import com.selflearning.authservice.role.response.RoleResponse;
import com.selflearning.authservice.common.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/applications/{applicationCode}/users/{userId}")
public class UserAuthorizationController {

    private final AuthorizationService authorizationService;

    public UserAuthorizationController(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * 查询用户在指定应用下已分配的角色。
     *
     * @param applicationCode 应用编码
     * @param userId 用户ID
     * @return 角色列表
     */
    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> listUserRoles(
            @PathVariable String applicationCode,
            @PathVariable Long userId) {
        return ApiResponse.ok(authorizationService.listUserRoles(applicationCode, userId));
    }

    /**
     * 替换用户在指定应用下的角色。
     *
     * <p>该操作只影响当前 applicationCode 下的用户角色，不会删除用户在其他应用下的角色。</p>
     *
     * @param applicationCode 应用编码
     * @param userId 用户ID
     * @param request 角色ID集合
     * @return 替换后的角色列表
     */
    @PutMapping("/roles")
    public ApiResponse<List<RoleResponse>> replaceUserRoles(
            @PathVariable String applicationCode,
            @PathVariable Long userId,
            @Valid @RequestBody UserRoleAssignRequest request) {
        return ApiResponse.ok(authorizationService.replaceUserRoles(applicationCode, userId, request));
    }

    /**
     * 查询用户在指定应用下通过角色获得的有效权限。
     *
     * @param applicationCode 应用编码
     * @param userId 用户ID
     * @return 权限列表
     */
    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> listUserPermissions(
            @PathVariable String applicationCode,
            @PathVariable Long userId) {
        return ApiResponse.ok(authorizationService.listUserPermissions(applicationCode, userId));
    }
}
