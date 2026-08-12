package com.selflearning.authservice.permission.controller;

import com.selflearning.authservice.permission.request.PermissionCreateRequest;
import com.selflearning.authservice.permission.request.PermissionStatusRequest;
import com.selflearning.authservice.permission.request.PermissionUpdateRequest;
import com.selflearning.authservice.permission.response.PermissionResponse;
import com.selflearning.authservice.permission.service.PermissionService;
import com.selflearning.authservice.common.security.RequiresPermission;
import com.selflearning.authservice.common.web.ApiResponse;
import com.selflearning.authservice.common.web.PageResponse;
import jakarta.validation.Valid;
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
@RequestMapping("/applications/{applicationCode}/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * 分页查询指定应用下的权限。
     *
     * @param applicationCode 应用编码
     * @param keyword 权限编码、名称或路径关键字
     * @param permissionType 权限类型
     * @param status 权限状态，1 表示启用，0 表示停用
     * @param parentId 父权限ID
     * @param page 当前页码，从 1 开始
     * @param pageSize 每页条数，最大 100
     * @return 权限分页结果
     */
    @GetMapping
    @RequiresPermission("permission:manage")
    public ApiResponse<PageResponse<PermissionResponse>> pagePermissions(
            @PathVariable String applicationCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String permissionType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(permissionService.pagePermissions(
                applicationCode,
                keyword,
                permissionType,
                status,
                parentId,
                page,
                pageSize));
    }

    /**
     * 查询指定应用下的权限详情。
     *
     * @param applicationCode 应用编码
     * @param permissionId 权限ID
     * @return 权限详情
     */
    @GetMapping("/{permissionId}")
    @RequiresPermission("permission:manage")
    public ApiResponse<PermissionResponse> getPermission(
            @PathVariable String applicationCode,
            @PathVariable Long permissionId) {
        return ApiResponse.ok(permissionService.getPermission(applicationCode, permissionId));
    }

    /**
     * 在指定应用下创建权限。
     *
     * @param applicationCode 应用编码
     * @param request 创建请求
     * @return 新建权限详情
     */
    @PostMapping
    @RequiresPermission("permission:manage")
    public ApiResponse<PermissionResponse> createPermission(
            @PathVariable String applicationCode,
            @Valid @RequestBody PermissionCreateRequest request) {
        return ApiResponse.ok(permissionService.createPermission(applicationCode, request));
    }

    /**
     * 更新指定应用下的权限基础信息。
     *
     * @param applicationCode 应用编码
     * @param permissionId 权限ID
     * @param request 更新请求
     * @return 更新后的权限详情
     */
    @PutMapping("/{permissionId}")
    @RequiresPermission("permission:manage")
    public ApiResponse<PermissionResponse> updatePermission(
            @PathVariable String applicationCode,
            @PathVariable Long permissionId,
            @Valid @RequestBody PermissionUpdateRequest request) {
        return ApiResponse.ok(permissionService.updatePermission(applicationCode, permissionId, request));
    }

    /**
     * 更新指定应用下的权限启停状态。
     *
     * @param applicationCode 应用编码
     * @param permissionId 权限ID
     * @param request 状态请求
     * @return 更新后的权限详情
     */
    @PatchMapping("/{permissionId}/status")
    @RequiresPermission("permission:manage")
    public ApiResponse<PermissionResponse> updateStatus(
            @PathVariable String applicationCode,
            @PathVariable Long permissionId,
            @Valid @RequestBody PermissionStatusRequest request) {
        return ApiResponse.ok(permissionService.updateStatus(applicationCode, permissionId, request));
    }

    /**
     * 删除指定应用下的权限。
     *
     * @param applicationCode 应用编码
     * @param permissionId 权限ID
     * @return 空响应
     */
    @DeleteMapping("/{permissionId}")
    @RequiresPermission("permission:manage")
    public ApiResponse<Void> deletePermission(
            @PathVariable String applicationCode,
            @PathVariable Long permissionId) {
        permissionService.deletePermission(applicationCode, permissionId);
        return ApiResponse.ok();
    }
}
