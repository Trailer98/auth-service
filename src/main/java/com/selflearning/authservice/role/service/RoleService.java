package com.selflearning.authservice.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.role.domain.AuthRole;
import com.selflearning.authservice.role.mapper.AuthRoleMapper;
import com.selflearning.authservice.role.request.RoleCreateRequest;
import com.selflearning.authservice.role.request.RoleStatusRequest;
import com.selflearning.authservice.role.request.RoleUpdateRequest;
import com.selflearning.authservice.role.response.RoleResponse;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.NotFoundException;
import com.selflearning.authservice.common.web.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private static final int DEFAULT_STATUS_ENABLED = 1;
    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final AuthApplicationMapper applicationMapper;
    private final AuthRoleMapper roleMapper;

    public RoleService(AuthApplicationMapper applicationMapper, AuthRoleMapper roleMapper) {
        this.applicationMapper = applicationMapper;
        this.roleMapper = roleMapper;
    }

    public PageResponse<RoleResponse> pageRoles(
            String applicationCode,
            String keyword,
            Integer status,
            Long page,
            Long pageSize) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        long normalizedPage = normalizePage(page);
        long normalizedPageSize = normalizePageSize(pageSize);
        validateStatus(status);

        Page<AuthRole> resultPage = roleMapper.selectPage(
                Page.of(normalizedPage, normalizedPageSize),
                baseQuery(normalizedApplicationCode, keyword, status)
                        .orderByDesc(AuthRole::getCreatedAt)
                        .orderByDesc(AuthRole::getId));

        return new PageResponse<>(
                resultPage.getRecords().stream()
                        .map(RoleResponse::from)
                        .toList(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize());
    }

    public RoleResponse getRole(String applicationCode, Long roleId) {
        return RoleResponse.from(requireRole(applicationCode, roleId));
    }

    @Transactional
    public RoleResponse createRole(String applicationCode, RoleCreateRequest request) {
        String normalizedApplicationCode = requireApplication(applicationCode);

        AuthRole role = new AuthRole();
        role.setApplicationCode(normalizedApplicationCode);
        role.setRoleCode(request.roleCode().trim());
        role.setRoleName(request.roleName().trim());
        role.setDescription(trimToNull(request.description()));
        role.setStatus(request.status() == null ? DEFAULT_STATUS_ENABLED : request.status());
        role.setDeleted(false);

        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            throw new BadRequestException("Role code already exists in application");
        }

        return RoleResponse.from(roleMapper.selectById(role.getId()));
    }

    @Transactional
    public RoleResponse updateRole(String applicationCode, Long roleId, RoleUpdateRequest request) {
        AuthRole existing = requireRole(applicationCode, roleId);
        existing.setRoleName(request.roleName().trim());
        existing.setDescription(trimToNull(request.description()));
        existing.setStatus(request.status() == null ? existing.getStatus() : request.status());
        roleMapper.updateById(existing);
        return RoleResponse.from(requireRole(applicationCode, roleId));
    }

    @Transactional
    public RoleResponse updateStatus(String applicationCode, Long roleId, RoleStatusRequest request) {
        AuthRole existing = requireRole(applicationCode, roleId);
        existing.setStatus(request.status());
        roleMapper.updateById(existing);
        return RoleResponse.from(requireRole(applicationCode, roleId));
    }

    @Transactional
    public void deleteRole(String applicationCode, Long roleId) {
        AuthRole existing = requireRole(applicationCode, roleId);
        existing.setDeleted(true);
        roleMapper.updateById(existing);
    }

    private LambdaQueryWrapper<AuthRole> baseQuery(String applicationCode, String keyword, Integer status) {
        LambdaQueryWrapper<AuthRole> wrapper = new LambdaQueryWrapper<AuthRole>()
                .eq(AuthRole::getApplicationCode, applicationCode)
                .eq(AuthRole::getDeleted, false);
        if (status != null) {
            wrapper.eq(AuthRole::getStatus, status);
        }
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(query -> query
                    .like(AuthRole::getRoleCode, normalizedKeyword)
                    .or()
                    .like(AuthRole::getRoleName, normalizedKeyword)
                    .or()
                    .like(AuthRole::getDescription, normalizedKeyword));
        }
        return wrapper;
    }

    private AuthRole requireRole(String applicationCode, Long roleId) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        if (roleId == null || roleId <= 0) {
            throw new BadRequestException("Invalid role id");
        }
        AuthRole role = roleMapper.selectOne(new LambdaQueryWrapper<AuthRole>()
                .eq(AuthRole::getApplicationCode, normalizedApplicationCode)
                .eq(AuthRole::getId, roleId)
                .eq(AuthRole::getDeleted, false)
                .last("LIMIT 1"));
        if (role == null) {
            throw new NotFoundException("Role not found in application");
        }
        return role;
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
