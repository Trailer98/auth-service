package com.selflearning.authservice.application.controller;

import com.selflearning.authservice.application.request.ApplicationCreateRequest;
import com.selflearning.authservice.application.request.ApplicationStatusRequest;
import com.selflearning.authservice.application.request.ApplicationUpdateRequest;
import com.selflearning.authservice.application.response.ApplicationResponse;
import com.selflearning.authservice.application.service.ApplicationService;
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
@RequestMapping("/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询应用列表。
     *
     * <p>{@code applicationCode} 只用于 {@link RequiresPermission} 解析权限域（固定传
     * {@code "PLATFORM"}），与实际查询/操作的应用注册表记录无关——本接口管理的始终是全表，不是
     * 按某个 applicationCode 过滤的子集。
     *
     * @param keyword 应用编码、名称或描述关键字
     * @param status 应用状态，1 表示启用，0 表示停用
     * @param page 当前页码，从 1 开始
     * @param pageSize 每页条数，最大 100
     * @param applicationCode 调用方所属应用编码，用于权限校验（固定为 PLATFORM）
     * @return 应用分页结果
     */
    @GetMapping
    @RequiresPermission("application:read")
    public ApiResponse<PageResponse<ApplicationResponse>> pageApplications(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(applicationService.pageApplications(keyword, status, page, pageSize));
    }

    /**
     * 查询应用详情。
     *
     * @param id 应用ID
     * @param applicationCode 调用方所属应用编码，用于权限校验（固定为 PLATFORM）
     * @return 应用详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("application:read")
    public ApiResponse<ApplicationResponse> getApplication(
            @PathVariable Long id,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(applicationService.getApplication(id));
    }

    /**
     * 创建应用。
     *
     * @param request 创建请求
     * @param applicationCode 调用方所属应用编码，用于权限校验（固定为 PLATFORM）
     * @return 新建应用详情
     */
    @PostMapping
    @RequiresPermission("application:manage")
    public ApiResponse<ApplicationResponse> createApplication(
            @Valid @RequestBody ApplicationCreateRequest request,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(applicationService.createApplication(request));
    }

    /**
     * 更新应用基础信息。
     *
     * @param id 应用ID
     * @param request 更新请求
     * @param applicationCode 调用方所属应用编码，用于权限校验（固定为 PLATFORM）
     * @return 更新后的应用详情
     */
    @PutMapping("/{id}")
    @RequiresPermission("application:manage")
    public ApiResponse<ApplicationResponse> updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationUpdateRequest request,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(applicationService.updateApplication(id, request));
    }

    /**
     * 更新应用启停状态。PLATFORM 是保留系统应用，不能被禁用——见 {@link ApplicationService#updateStatus}。
     *
     * @param id 应用ID
     * @param request 状态请求
     * @param applicationCode 调用方所属应用编码，用于权限校验（固定为 PLATFORM）
     * @return 更新后的应用详情
     */
    @PatchMapping("/{id}/status")
    @RequiresPermission("application:manage")
    public ApiResponse<ApplicationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationStatusRequest request,
            @RequestParam String applicationCode) {
        return ApiResponse.ok(applicationService.updateStatus(id, request));
    }

    /**
     * 删除应用。PLATFORM 是保留系统应用，不能被删除——见 {@link ApplicationService#deleteApplication}。
     *
     * @param id 应用ID
     * @param applicationCode 调用方所属应用编码，用于权限校验（固定为 PLATFORM）
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("application:manage")
    public ApiResponse<Void> deleteApplication(
            @PathVariable Long id,
            @RequestParam String applicationCode) {
        applicationService.deleteApplication(id);
        return ApiResponse.ok();
    }
}
