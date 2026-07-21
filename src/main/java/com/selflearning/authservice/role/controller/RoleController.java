package com.selflearning.authservice.role.controller;

import com.selflearning.authservice.role.request.RolePermissionAssignRequest;
import com.selflearning.authservice.role.service.AuthorizationService;
import com.selflearning.authservice.permission.response.PermissionResponse;
import com.selflearning.authservice.role.request.RoleCreateRequest;
import com.selflearning.authservice.role.request.RoleStatusRequest;
import com.selflearning.authservice.role.request.RoleUpdateRequest;
import com.selflearning.authservice.role.response.RoleResponse;
import com.selflearning.authservice.role.service.RoleService;
import com.selflearning.authservice.common.web.ApiResponse;
import com.selflearning.authservice.common.web.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/applications/{applicationCode}/roles")
public class RoleController {

    private final RoleService roleService;
    private final AuthorizationService authorizationService;

    public RoleController(RoleService roleService, AuthorizationService authorizationService) {
        this.roleService = roleService;
        this.authorizationService = authorizationService;
    }

    /**
     * 分页查询指定应用下的角色。
     *
     * @param applicationCode 应用编码
     * @param keyword 角色编码、名称或描述关键字
     * @param status 角色状态，1 表示启用，0 表示停用
     * @param page 当前页码，从 1 开始
     * @param pageSize 每页条数，最大 100
     * @return 角色分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<RoleResponse>> pageRoles(
            @PathVariable String applicationCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(roleService.pageRoles(applicationCode, keyword, status, page, pageSize));
    }

    /**
     * 查询指定应用下的角色详情。
     *
     * @param applicationCode 应用编码
     * @param roleId 角色ID
     * @return 角色详情
     */
    @GetMapping("/{roleId}")
    public ApiResponse<RoleResponse> getRole(
            @PathVariable String applicationCode,
            @PathVariable Long roleId) {
        return ApiResponse.ok(roleService.getRole(applicationCode, roleId));
    }

    /**
     * 在指定应用下创建角色。
     *
     * @param applicationCode 应用编码
     * @param request 创建请求
     * @return 新建角色详情
     */
    @PostMapping
    public ApiResponse<RoleResponse> createRole(
            @PathVariable String applicationCode,
            @Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(roleService.createRole(applicationCode, request));
    }

    /**
     * 更新指定应用下的角色基础信息。
     *
     * @param applicationCode 应用编码
     * @param roleId 角色ID
     * @param request 更新请求
     * @return 更新后的角色详情
     */
    @PutMapping("/{roleId}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable String applicationCode,
            @PathVariable Long roleId,
            @Valid @RequestBody RoleUpdateRequest request) {
        return ApiResponse.ok(roleService.updateRole(applicationCode, roleId, request));
    }

    /**
     * 更新指定应用下的角色启停状态。
     *
     * @param applicationCode 应用编码
     * @param roleId 角色ID
     * @param request 状态请求
     * @return 更新后的角色详情
     */
    @PatchMapping("/{roleId}/status")
    public ApiResponse<RoleResponse> updateStatus(
            @PathVariable String applicationCode,
            @PathVariable Long roleId,
            @Valid @RequestBody RoleStatusRequest request) {
        return ApiResponse.ok(roleService.updateStatus(applicationCode, roleId, request));
    }

    /**
     * 删除指定应用下的角色。
     *
     * @param applicationCode 应用编码
     * @param roleId 角色ID
     * @return 空响应
     */
    @DeleteMapping("/{roleId}")
    public ApiResponse<Void> deleteRole(
            @PathVariable String applicationCode,
            @PathVariable Long roleId) {
        roleService.deleteRole(applicationCode, roleId);
        return ApiResponse.ok();
    }

    /**
     * 查询指定应用下某个角色绑定的权限。
     *
     * @param applicationCode 应用编码
     * @param roleId 角色ID
     * @return 权限列表
     */
    @GetMapping("/{roleId}/permissions")
    public ApiResponse<List<PermissionResponse>> listRolePermissions(
            @PathVariable String applicationCode,
            @PathVariable Long roleId) {
        return ApiResponse.ok(authorizationService.listRolePermissions(applicationCode, roleId));
    }

    /**
     * 替换指定应用下某个角色绑定的权限。
     *
     * <p>所有 permissionIds 必须属于当前 applicationCode。</p>
     *
     * @param applicationCode 应用编码
     * @param roleId 角色ID
     * @param request 权限ID集合
     * @return 替换后的权限列表
     */
    @PutMapping("/{roleId}/permissions")
    public ApiResponse<List<PermissionResponse>> replaceRolePermissions(
            @PathVariable String applicationCode,
            @PathVariable Long roleId,
            @Valid @RequestBody RolePermissionAssignRequest request) {
        return ApiResponse.ok(authorizationService.replaceRolePermissions(applicationCode, roleId, request));
    }
}
