package com.selflearning.authservice.auth.controller;

import com.selflearning.authservice.auth.request.UserCreateRequest;
import com.selflearning.authservice.auth.request.UserStatusRequest;
import com.selflearning.authservice.auth.request.UserUpdateRequest;
import com.selflearning.authservice.auth.response.UserResponse;
import com.selflearning.authservice.auth.service.UserService;
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

/**
 * Users are a global resource — not owned by any single application (an {@code auth_user} row can hold
 * roles under several {@code applicationCode}s at once) — so this controller is not path-scoped by
 * {@code applicationCode} the way {@code RoleController}/{@code PermissionController} are. Every method
 * instead takes {@code applicationCode} as an explicit request parameter: it says "I'm managing users as
 * application X", and {@link RequiresPermission} (via {@code PermissionAspect}) checks the caller holds
 * {@code <applicationCode-lowercased>:user:manage} in that application's scope. WMS is the only caller
 * today; nothing here assumes it's the only one going forward.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询用户列表。
     *
     * @param keyword 用户名、昵称、邮箱或手机号关键字
     * @param status 用户状态，1 表示启用，0 表示停用
     * @param page 当前页码，从 1 开始
     * @param pageSize 每页条数，最大 100
     * @param applicationCode 调用方所属应用编码，用于权限校验（如 WMS）
     * @return 用户分页结果
     */
    @GetMapping
    @RequiresPermission("user:manage")
    public ApiResponse<PageResponse<UserResponse>> pageUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(userService.pageUsers(keyword, status, page, pageSize));
    }

    /**
     * 查询用户详情。
     *
     * @param id 用户ID
     * @param applicationCode 调用方所属应用编码，用于权限校验
     * @return 用户详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("user:manage")
    public ApiResponse<UserResponse> getUser(
            @PathVariable Long id,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(userService.getUser(id));
    }

    /**
     * 创建用户。
     *
     * @param request 创建请求
     * @param applicationCode 调用方所属应用编码，用于权限校验
     * @return 新建用户详情
     */
    @PostMapping
    @RequiresPermission("user:manage")
    public ApiResponse<UserResponse> createUser(
            @Valid @RequestBody UserCreateRequest request,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(userService.createUser(request));
    }

    /**
     * 更新用户基础信息。
     *
     * @param id 用户ID
     * @param request 更新请求
     * @param applicationCode 调用方所属应用编码，用于权限校验
     * @return 更新后的用户详情
     */
    @PutMapping("/{id}")
    @RequiresPermission("user:manage")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(userService.updateUser(id, request));
    }

    /**
     * 更新用户启停状态。
     *
     * @param id 用户ID
     * @param request 状态请求
     * @param applicationCode 调用方所属应用编码，用于权限校验
     * @return 更新后的用户详情
     */
    @PatchMapping("/{id}/status")
    @RequiresPermission("user:manage")
    public ApiResponse<UserResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusRequest request,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(userService.updateStatus(id, request));
    }

    /**
     * 删除用户。
     *
     * @param id 用户ID
     * @param applicationCode 调用方所属应用编码，用于权限校验
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("user:manage")
    public ApiResponse<Void> deleteUser(
            @PathVariable Long id,
            @RequestParam String applicationCode) {
        userService.deleteUser(id);
        return ApiResponse.ok();
    }
}
