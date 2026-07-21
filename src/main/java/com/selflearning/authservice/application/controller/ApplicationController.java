package com.selflearning.authservice.application.controller;

import com.selflearning.authservice.application.request.ApplicationCreateRequest;
import com.selflearning.authservice.application.request.ApplicationStatusRequest;
import com.selflearning.authservice.application.request.ApplicationUpdateRequest;
import com.selflearning.authservice.application.response.ApplicationResponse;
import com.selflearning.authservice.application.service.ApplicationService;
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
     * @param keyword 应用编码、名称或描述关键字
     * @param status 应用状态，1 表示启用，0 表示停用
     * @param page 当前页码，从 1 开始
     * @param pageSize 每页条数，最大 100
     * @return 应用分页结果
     */
    @GetMapping
    public ApiResponse<PageResponse<ApplicationResponse>> pageApplications(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long pageSize) {
        return ApiResponse.ok(applicationService.pageApplications(keyword, status, page, pageSize));
    }

    /**
     * 查询应用详情。
     *
     * @param id 应用ID
     * @return 应用详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ApplicationResponse> getApplication(@PathVariable Long id) {
        return ApiResponse.ok(applicationService.getApplication(id));
    }

    /**
     * 创建应用。
     *
     * @param request 创建请求
     * @return 新建应用详情
     */
    @PostMapping
    public ApiResponse<ApplicationResponse> createApplication(@Valid @RequestBody ApplicationCreateRequest request) {
        return ApiResponse.ok(applicationService.createApplication(request));
    }

    /**
     * 更新应用基础信息。
     *
     * @param id 应用ID
     * @param request 更新请求
     * @return 更新后的应用详情
     */
    @PutMapping("/{id}")
    public ApiResponse<ApplicationResponse> updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationUpdateRequest request) {
        return ApiResponse.ok(applicationService.updateApplication(id, request));
    }

    /**
     * 更新应用启停状态。
     *
     * @param id 应用ID
     * @param request 状态请求
     * @return 更新后的应用详情
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<ApplicationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ApplicationStatusRequest request) {
        return ApiResponse.ok(applicationService.updateStatus(id, request));
    }

    /**
     * 删除应用。
     *
     * @param id 应用ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteApplication(@PathVariable Long id) {
        applicationService.deleteApplication(id);
        return ApiResponse.ok();
    }
}
