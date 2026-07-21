package com.selflearning.authservice.permission.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.auth.service.PermissionContextCacheService;
import com.selflearning.authservice.permission.domain.AuthPermission;
import com.selflearning.authservice.permission.mapper.AuthPermissionMapper;
import com.selflearning.authservice.permission.request.PermissionCreateRequest;
import com.selflearning.authservice.permission.request.PermissionStatusRequest;
import com.selflearning.authservice.permission.request.PermissionUpdateRequest;
import com.selflearning.authservice.permission.response.PermissionResponse;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.NotFoundException;
import com.selflearning.authservice.common.web.PageResponse;
import com.selflearning.authservice.role.domain.AuthRolePermission;
import com.selflearning.authservice.role.domain.AuthUserRole;
import com.selflearning.authservice.role.mapper.AuthRolePermissionMapper;
import com.selflearning.authservice.role.mapper.AuthUserRoleMapper;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionService {

    private static final int DEFAULT_STATUS_ENABLED = 1;
    private static final int DEFAULT_SORT_ORDER = 0;
    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final AuthApplicationMapper applicationMapper;
    private final AuthPermissionMapper permissionMapper;
    private final AuthRolePermissionMapper rolePermissionMapper;
    private final AuthUserRoleMapper userRoleMapper;
    private final PermissionContextCacheService permissionContextCacheService;

    public PermissionService(
            AuthApplicationMapper applicationMapper,
            AuthPermissionMapper permissionMapper,
            AuthRolePermissionMapper rolePermissionMapper,
            AuthUserRoleMapper userRoleMapper,
            PermissionContextCacheService permissionContextCacheService) {
        this.applicationMapper = applicationMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionContextCacheService = permissionContextCacheService;
    }

    public PageResponse<PermissionResponse> pagePermissions(
            String applicationCode,
            String keyword,
            String permissionType,
            Integer status,
            Long parentId,
            Long page,
            Long pageSize) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        long normalizedPage = normalizePage(page);
        long normalizedPageSize = normalizePageSize(pageSize);
        validateStatus(status);

        Page<AuthPermission> resultPage = permissionMapper.selectPage(
                Page.of(normalizedPage, normalizedPageSize),
                baseQuery(normalizedApplicationCode, keyword, permissionType, status, parentId)
                        .orderByAsc(AuthPermission::getSortOrder)
                        .orderByAsc(AuthPermission::getId));

        return new PageResponse<>(
                resultPage.getRecords().stream()
                        .map(PermissionResponse::from)
                        .toList(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize());
    }

    public PermissionResponse getPermission(String applicationCode, Long permissionId) {
        return PermissionResponse.from(requirePermission(applicationCode, permissionId));
    }

    @Transactional
    public PermissionResponse createPermission(String applicationCode, PermissionCreateRequest request) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        validateParent(normalizedApplicationCode, request.parentId(), null);

        AuthPermission permission = new AuthPermission();
        permission.setApplicationCode(normalizedApplicationCode);
        permission.setPermissionCode(request.permissionCode().trim());
        permission.setPermissionName(request.permissionName().trim());
        permission.setPermissionType(request.permissionType().trim());
        permission.setParentId(request.parentId());
        permission.setPath(trimToNull(request.path()));
        permission.setComponent(trimToNull(request.component()));
        permission.setSortOrder(request.sortOrder() == null ? DEFAULT_SORT_ORDER : request.sortOrder());
        permission.setStatus(request.status() == null ? DEFAULT_STATUS_ENABLED : request.status());
        permission.setDeleted(false);

        try {
            permissionMapper.insert(permission);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("Permission code already exists in application");
        }

        return PermissionResponse.from(permissionMapper.selectById(permission.getId()));
    }

    @Transactional
    public PermissionResponse updatePermission(String applicationCode, Long permissionId, PermissionUpdateRequest request) {
        AuthPermission existing = requirePermission(applicationCode, permissionId);
        validateParent(existing.getApplicationCode(), request.parentId(), permissionId);
        Integer previousStatus = existing.getStatus();

        existing.setPermissionName(request.permissionName().trim());
        existing.setPermissionType(request.permissionType().trim());
        existing.setParentId(request.parentId());
        existing.setPath(trimToNull(request.path()));
        existing.setComponent(trimToNull(request.component()));
        existing.setSortOrder(request.sortOrder() == null ? existing.getSortOrder() : request.sortOrder());
        existing.setStatus(request.status() == null ? existing.getStatus() : request.status());
        permissionMapper.updateById(existing);
        if (request.status() != null && !request.status().equals(previousStatus)) {
            evictPermissionContext(existing.getApplicationCode(), existing.getId());
        }
        return PermissionResponse.from(requirePermission(applicationCode, permissionId));
    }

    @Transactional
    public PermissionResponse updateStatus(String applicationCode, Long permissionId, PermissionStatusRequest request) {
        AuthPermission existing = requirePermission(applicationCode, permissionId);
        existing.setStatus(request.status());
        permissionMapper.updateById(existing);
        evictPermissionContext(existing.getApplicationCode(), existing.getId());
        return PermissionResponse.from(requirePermission(applicationCode, permissionId));
    }

    @Transactional
    public void deletePermission(String applicationCode, Long permissionId) {
        AuthPermission existing = requirePermission(applicationCode, permissionId);
        existing.setDeleted(true);
        permissionMapper.updateById(existing);
        evictPermissionContext(existing.getApplicationCode(), existing.getId());
    }

    private void evictPermissionContext(String applicationCode, Long permissionId) {
        Set<Long> roleIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<AuthRolePermission>()
                        .eq(AuthRolePermission::getApplicationCode, applicationCode)
                        .eq(AuthRolePermission::getPermissionId, permissionId))
                .stream()
                .map(AuthRolePermission::getRoleId)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            permissionContextCacheService.evictUsersApplication(Set.of(), applicationCode);
            return;
        }
        Set<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<AuthUserRole>()
                        .eq(AuthUserRole::getApplicationCode, applicationCode)
                        .in(AuthUserRole::getRoleId, roleIds))
                .stream()
                .map(AuthUserRole::getUserId)
                .collect(Collectors.toSet());
        permissionContextCacheService.evictUsersApplication(userIds, applicationCode);
    }

    private LambdaQueryWrapper<AuthPermission> baseQuery(
            String applicationCode,
            String keyword,
            String permissionType,
            Integer status,
            Long parentId) {
        LambdaQueryWrapper<AuthPermission> wrapper = new LambdaQueryWrapper<AuthPermission>()
                .eq(AuthPermission::getApplicationCode, applicationCode)
                .eq(AuthPermission::getDeleted, false);
        if (status != null) {
            wrapper.eq(AuthPermission::getStatus, status);
        }
        String normalizedPermissionType = trimToNull(permissionType);
        if (normalizedPermissionType != null) {
            wrapper.eq(AuthPermission::getPermissionType, normalizedPermissionType);
        }
        if (parentId != null) {
            wrapper.eq(AuthPermission::getParentId, parentId);
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query
                    .like(AuthPermission::getPermissionCode, normalizedKeyword)
                    .or()
                    .like(AuthPermission::getPermissionName, normalizedKeyword)
                    .or()
                    .like(AuthPermission::getPath, normalizedKeyword));
        }
        return wrapper;
    }

    private AuthPermission requirePermission(String applicationCode, Long permissionId) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        if (permissionId == null || permissionId <= 0) {
            throw new BadRequestException("Invalid permission id");
        }
        AuthPermission permission = permissionMapper.selectOne(new LambdaQueryWrapper<AuthPermission>()
                .eq(AuthPermission::getApplicationCode, normalizedApplicationCode)
                .eq(AuthPermission::getId, permissionId)
                .eq(AuthPermission::getDeleted, false)
                .last("LIMIT 1"));
        if (permission == null) {
            throw new NotFoundException("Permission not found in application");
        }
        return permission;
    }

    private void validateParent(String applicationCode, Long parentId, Long currentPermissionId) {
        if (parentId == null) {
            return;
        }
        if (parentId <= 0) {
            throw new BadRequestException("Invalid parent permission id");
        }
        if (parentId.equals(currentPermissionId)) {
            throw new BadRequestException("Permission cannot use itself as parent");
        }
        AuthPermission parent = permissionMapper.selectOne(new LambdaQueryWrapper<AuthPermission>()
                .eq(AuthPermission::getApplicationCode, applicationCode)
                .eq(AuthPermission::getId, parentId)
                .eq(AuthPermission::getDeleted, false)
                .last("LIMIT 1"));
        if (parent == null) {
            throw new BadRequestException("Parent permission must belong to the same application");
        }
    }

    private String requireApplication(String applicationCode) {
        String normalizedApplicationCode = normalizeApplicationCode(applicationCode);
        AuthApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<AuthApplication>()
                .eq(AuthApplication::getApplicationCode, normalizedApplicationCode)
                .eq(AuthApplication::getDeleted, false)
                .last("LIMIT 1"));
        if (application == null) {
            throw new NotFoundException("Application not found");
        }
        return normalizedApplicationCode;
    }

    private String normalizeApplicationCode(String applicationCode) {
        if (applicationCode == null || applicationCode.isBlank()) {
            throw new BadRequestException("Application code is required");
        }
        return applicationCode.trim();
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
