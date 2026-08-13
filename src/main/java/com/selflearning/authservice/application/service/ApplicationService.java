package com.selflearning.authservice.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.selflearning.authservice.application.request.ApplicationCreateRequest;
import com.selflearning.authservice.application.request.ApplicationStatusRequest;
import com.selflearning.authservice.application.request.ApplicationUpdateRequest;
import com.selflearning.authservice.application.response.ApplicationResponse;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.auth.service.PermissionContextCacheService;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.NotFoundException;
import com.selflearning.authservice.common.web.PageResponse;
import com.selflearning.authservice.role.domain.AuthUserRole;
import com.selflearning.authservice.role.mapper.AuthUserRoleMapper;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final int DEFAULT_STATUS_ENABLED = 1;
    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;
    /**
     * PLATFORM 是身份平台自身的管理面（应用注册表等跨应用资源），不是一个可以被禁用/删除的普通业务
     * 应用——一旦它被禁用或删除，{@code AuthContextService.requireEnabledApplication} 会让所有
     * {@code applicationCode=PLATFORM} 的请求（包括 PLATFORM_ADMIN 自己）全部失败，相当于把平台管理面
     * 自己锁死在外面。这条业务不变量放在 Service 层而不是 Controller 层：即便是合法持有
     * {@code application:manage} 权限的调用方，也不应该能通过这个接口把自己锁死；未来任何新增的调用
     * 入口（批处理脚本、内部管理 CLI）都会自动继承这条保护，不依赖每个调用方各自记得检查。
     */
    private static final String RESERVED_PLATFORM_APPLICATION_CODE = "PLATFORM";

    private final AuthApplicationMapper applicationMapper;
    private final AuthUserRoleMapper userRoleMapper;
    private final PermissionContextCacheService permissionContextCacheService;

    public ApplicationService(
            AuthApplicationMapper applicationMapper,
            AuthUserRoleMapper userRoleMapper,
            PermissionContextCacheService permissionContextCacheService) {
        this.applicationMapper = applicationMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionContextCacheService = permissionContextCacheService;
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
        guardPlatformNotDisabled(existing, request.status());
        Integer previousStatus = existing.getStatus();
        existing.setApplicationName(request.applicationName().trim());
        existing.setDescription(trimToNull(request.description()));
        existing.setStatus(request.status() == null ? existing.getStatus() : request.status());
        applicationMapper.updateById(existing);
        if (request.status() != null && !request.status().equals(previousStatus)) {
            evictApplicationContext(existing.getApplicationCode());
        }
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
        guardPlatformNotDisabled(existing, request.status());
        existing.setStatus(request.status());
        applicationMapper.updateById(existing);
        evictApplicationContext(existing.getApplicationCode());
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
        guardPlatformNotDeleted(existing);
        existing.setDeleted(true);
        applicationMapper.updateById(existing);
        evictApplicationContext(existing.getApplicationCode());
    }

    /**
     * 禁止把 PLATFORM 这一行的 status 改成非启用值——{@code updateApplication} 和 {@code updateStatus}
     * 都能触碰 status 字段，两处都要挡。{@code requestedStatus} 为 {@code null} 表示这次请求不改
     * status（{@code updateApplication} 允许只改名称/描述），此时不视为禁用尝试。
     */
    private void guardPlatformNotDisabled(AuthApplication existing, Integer requestedStatus) {
        if (RESERVED_PLATFORM_APPLICATION_CODE.equals(existing.getApplicationCode())
                && requestedStatus != null
                && requestedStatus != DEFAULT_STATUS_ENABLED) {
            throw new BadRequestException("PLATFORM is a reserved system application and cannot be disabled");
        }
    }

    private void guardPlatformNotDeleted(AuthApplication existing) {
        if (RESERVED_PLATFORM_APPLICATION_CODE.equals(existing.getApplicationCode())) {
            throw new BadRequestException("PLATFORM is a reserved system application and cannot be deleted");
        }
    }

    private void evictApplicationContext(String applicationCode) {
        Set<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<AuthUserRole>()
                        .eq(AuthUserRole::getApplicationCode, applicationCode))
                .stream()
                .map(AuthUserRole::getUserId)
                .collect(Collectors.toSet());
        permissionContextCacheService.evictUsersApplication(userIds, applicationCode);
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
