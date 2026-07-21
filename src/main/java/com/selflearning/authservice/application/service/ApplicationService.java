package com.selflearning.authservice.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.selflearning.authservice.application.request.ApplicationCreateRequest;
import com.selflearning.authservice.application.request.ApplicationStatusRequest;
import com.selflearning.authservice.application.request.ApplicationUpdateRequest;
import com.selflearning.authservice.application.response.ApplicationResponse;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.NotFoundException;
import com.selflearning.authservice.common.web.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final int DEFAULT_STATUS_ENABLED = 1;
    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final AuthApplicationMapper applicationMapper;

    public ApplicationService(AuthApplicationMapper applicationMapper) {
        this.applicationMapper = applicationMapper;
    }

    /**
     * 分页查询应用列表。
     *
     * @param keyword 应用编码、名称或描述关键字
     * @param status 应用状态
     * @param page 当前页码，从 1 开始
     * @param pageSize 每页条数
     * @return 应用分页结果
     */
    public PageResponse<ApplicationResponse> pageApplications(String keyword, Integer status, Long page, Long pageSize) {
        long normalizedPage = normalizePage(page);
        long normalizedPageSize = normalizePageSize(pageSize);
        validateStatus(status);

        Page<AuthApplication> resultPage = applicationMapper.selectPage(
                Page.of(normalizedPage, normalizedPageSize),
                baseQuery(keyword, status)
                        .orderByDesc(AuthApplication::getCreatedAt)
                        .orderByDesc(AuthApplication::getId));

        return new PageResponse<>(
                resultPage.getRecords().stream()
                        .map(ApplicationResponse::from)
                        .toList(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize());
    }

    /**
     * 根据 ID 查询应用详情。
     *
     * @param id 应用ID
     * @return 应用详情
     */
    public ApplicationResponse getApplication(Long id) {
        return ApplicationResponse.from(requireExistingApplication(id));
    }

    /**
     * 创建应用。
     *
     * @param request 创建请求
     * @return 新建应用详情
     */
    @Transactional
    public ApplicationResponse createApplication(ApplicationCreateRequest request) {
        AuthApplication application = new AuthApplication();
        application.setApplicationCode(request.applicationCode().trim());
        application.setApplicationName(request.applicationName().trim());
        application.setDescription(trimToNull(request.description()));
        application.setStatus(request.status() == null ? DEFAULT_STATUS_ENABLED : request.status());
        application.setDeleted(false);

        try {
            applicationMapper.insert(application);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("Application code already exists");
        }

        return ApplicationResponse.from(applicationMapper.selectById(application.getId()));
    }

    /**
     * 更新应用基础信息。应用编码作为权限域外键，创建后不允许修改。
     *
     * @param id 应用ID
     * @param request 更新请求
     * @return 更新后的应用详情
     */
    @Transactional
    public ApplicationResponse updateApplication(Long id, ApplicationUpdateRequest request) {
        AuthApplication existing = requireExistingApplication(id);
        existing.setApplicationName(request.applicationName().trim());
        existing.setDescription(trimToNull(request.description()));
        existing.setStatus(request.status() == null ? existing.getStatus() : request.status());
        applicationMapper.updateById(existing);
        return ApplicationResponse.from(requireExistingApplication(id));
    }

    /**
     * 更新应用启停状态。
     *
     * @param id 应用ID
     * @param request 状态请求
     * @return 更新后的应用详情
     */
    @Transactional
    public ApplicationResponse updateStatus(Long id, ApplicationStatusRequest request) {
        AuthApplication existing = requireExistingApplication(id);
        existing.setStatus(request.status());
        applicationMapper.updateById(existing);
        return ApplicationResponse.from(requireExistingApplication(id));
    }

    /**
     * 逻辑删除应用。
     *
     * @param id 应用ID
     */
    @Transactional
    public void deleteApplication(Long id) {
        AuthApplication existing = requireExistingApplication(id);
        existing.setDeleted(true);
        applicationMapper.updateById(existing);
    }

    private LambdaQueryWrapper<AuthApplication> baseQuery(String keyword, Integer status) {
        LambdaQueryWrapper<AuthApplication> wrapper = new LambdaQueryWrapper<AuthApplication>()
                .eq(AuthApplication::getDeleted, false);
        if (status != null) {
            wrapper.eq(AuthApplication::getStatus, status);
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query
                    .like(AuthApplication::getApplicationCode, normalizedKeyword)
                    .or()
                    .like(AuthApplication::getApplicationName, normalizedKeyword)
                    .or()
                    .like(AuthApplication::getDescription, normalizedKeyword));
        }
        return wrapper;
    }

    private AuthApplication requireExistingApplication(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("Invalid application id");
        }
        AuthApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<AuthApplication>()
                .eq(AuthApplication::getId, id)
                .eq(AuthApplication::getDeleted, false)
                .last("LIMIT 1"));
        if (application == null) {
            throw new NotFoundException("Application not found");
        }
        return application;
    }

    private long normalizePage(Long page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 1) {
            throw new BadRequestException("Page must be greater than 0");
        }
        return page;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
        return pageSize;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BadRequestException("Status must be 0 or 1");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
