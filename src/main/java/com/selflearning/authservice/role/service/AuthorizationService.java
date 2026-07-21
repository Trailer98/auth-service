package com.selflearning.authservice.role.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.auth.domain.AuthUser;
import com.selflearning.authservice.auth.mapper.AuthUserMapper;
import com.selflearning.authservice.permission.domain.AuthPermission;
import com.selflearning.authservice.permission.mapper.AuthPermissionMapper;
import com.selflearning.authservice.permission.response.PermissionResponse;
import com.selflearning.authservice.role.domain.AuthRole;
import com.selflearning.authservice.role.domain.AuthRolePermission;
import com.selflearning.authservice.role.domain.AuthUserRole;
import com.selflearning.authservice.role.mapper.AuthRoleMapper;
import com.selflearning.authservice.role.mapper.AuthRolePermissionMapper;
import com.selflearning.authservice.role.mapper.AuthUserRoleMapper;
import com.selflearning.authservice.role.request.RolePermissionAssignRequest;
import com.selflearning.authservice.role.request.UserRoleAssignRequest;
import com.selflearning.authservice.role.response.RoleResponse;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {

    private final AuthApplicationMapper applicationMapper;
    private final AuthUserMapper userMapper;
    private final AuthRoleMapper roleMapper;
    private final AuthPermissionMapper permissionMapper;
    private final AuthUserRoleMapper userRoleMapper;
    private final AuthRolePermissionMapper rolePermissionMapper;

    public AuthorizationService(
            AuthApplicationMapper applicationMapper,
            AuthUserMapper userMapper,
            AuthRoleMapper roleMapper,
            AuthPermissionMapper permissionMapper,
            AuthUserRoleMapper userRoleMapper,
            AuthRolePermissionMapper rolePermissionMapper) {
        this.applicationMapper = applicationMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    public List<PermissionResponse> listRolePermissions(String applicationCode, Long roleId) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        requireRole(normalizedApplicationCode, roleId);

        Set<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<AuthRolePermission>()
                        .eq(AuthRolePermission::getApplicationCode, normalizedApplicationCode)
                        .eq(AuthRolePermission::getRoleId, roleId))
                .stream()
                .map(AuthRolePermission::getPermissionId)
                .collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return List.of();
        }

        return permissionMapper.selectList(new LambdaQueryWrapper<AuthPermission>()
                        .eq(AuthPermission::getApplicationCode, normalizedApplicationCode)
                        .eq(AuthPermission::getDeleted, false)
                        .in(AuthPermission::getId, permissionIds))
                .stream()
                .sorted(Comparator.comparing(AuthPermission::getSortOrder).thenComparing(AuthPermission::getId))
                .map(PermissionResponse::from)
                .toList();
    }

    @Transactional
    public List<PermissionResponse> replaceRolePermissions(
            String applicationCode,
            Long roleId,
            RolePermissionAssignRequest request) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        requireRole(normalizedApplicationCode, roleId);
        Set<Long> permissionIds = normalizeIds(request.permissionIds(), "Permission ids");
        validatePermissionsBelongToApplication(normalizedApplicationCode, permissionIds);

        rolePermissionMapper.delete(new LambdaQueryWrapper<AuthRolePermission>()
                .eq(AuthRolePermission::getApplicationCode, normalizedApplicationCode)
                .eq(AuthRolePermission::getRoleId, roleId));
        for (Long permissionId : permissionIds) {
            AuthRolePermission relation = new AuthRolePermission();
            relation.setApplicationCode(normalizedApplicationCode);
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }

        return listRolePermissions(normalizedApplicationCode, roleId);
    }

    public List<RoleResponse> listUserRoles(String applicationCode, Long userId) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        requireUser(userId);

        Set<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<AuthUserRole>()
                        .eq(AuthUserRole::getApplicationCode, normalizedApplicationCode)
                        .eq(AuthUserRole::getUserId, userId))
                .stream()
                .map(AuthUserRole::getRoleId)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return List.of();
        }

        return roleMapper.selectList(new LambdaQueryWrapper<AuthRole>()
                        .eq(AuthRole::getApplicationCode, normalizedApplicationCode)
                        .eq(AuthRole::getDeleted, false)
                        .in(AuthRole::getId, roleIds))
                .stream()
                .sorted(Comparator.comparing(AuthRole::getId))
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional
    public List<RoleResponse> replaceUserRoles(String applicationCode, Long userId, UserRoleAssignRequest request) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        requireUser(userId);
        Set<Long> roleIds = normalizeIds(request.roleIds(), "Role ids");
        validateRolesBelongToApplication(normalizedApplicationCode, roleIds);

        userRoleMapper.delete(new LambdaQueryWrapper<AuthUserRole>()
                .eq(AuthUserRole::getApplicationCode, normalizedApplicationCode)
                .eq(AuthUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            AuthUserRole relation = new AuthUserRole();
            relation.setApplicationCode(normalizedApplicationCode);
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }

        return listUserRoles(normalizedApplicationCode, userId);
    }

    public List<PermissionResponse> listUserPermissions(String applicationCode, Long userId) {
        String normalizedApplicationCode = requireApplication(applicationCode);
        requireUser(userId);

        Set<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<AuthUserRole>()
                        .eq(AuthUserRole::getApplicationCode, normalizedApplicationCode)
                        .eq(AuthUserRole::getUserId, userId))
                .stream()
                .map(AuthUserRole::getRoleId)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return List.of();
        }

        Set<Long> activeRoleIds = roleMapper.selectList(new LambdaQueryWrapper<AuthRole>()
                        .eq(AuthRole::getApplicationCode, normalizedApplicationCode)
                        .eq(AuthRole::getDeleted, false)
                        .eq(AuthRole::getStatus, 1)
                        .in(AuthRole::getId, roleIds))
                .stream()
                .map(AuthRole::getId)
                .collect(Collectors.toSet());
        if (activeRoleIds.isEmpty()) {
            return List.of();
        }

        Set<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<AuthRolePermission>()
                        .eq(AuthRolePermission::getApplicationCode, normalizedApplicationCode)
                        .in(AuthRolePermission::getRoleId, activeRoleIds))
                .stream()
                .map(AuthRolePermission::getPermissionId)
                .collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return List.of();
        }

        return permissionMapper.selectList(new LambdaQueryWrapper<AuthPermission>()
                        .eq(AuthPermission::getApplicationCode, normalizedApplicationCode)
                        .eq(AuthPermission::getDeleted, false)
                        .eq(AuthPermission::getStatus, 1)
                        .in(AuthPermission::getId, permissionIds))
                .stream()
                .sorted(Comparator.comparing(AuthPermission::getSortOrder).thenComparing(AuthPermission::getId))
                .map(PermissionResponse::from)
                .toList();
    }

    private void validateRolesBelongToApplication(String applicationCode, Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        long count = roleMapper.selectCount(new LambdaQueryWrapper<AuthRole>()
                .eq(AuthRole::getApplicationCode, applicationCode)
                .eq(AuthRole::getDeleted, false)
                .in(AuthRole::getId, roleIds));
        if (count != roleIds.size()) {
            throw new BadRequestException("Role ids must belong to the same application");
        }
    }

    private void validatePermissionsBelongToApplication(String applicationCode, Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        long count = permissionMapper.selectCount(new LambdaQueryWrapper<AuthPermission>()
                .eq(AuthPermission::getApplicationCode, applicationCode)
                .eq(AuthPermission::getDeleted, false)
                .in(AuthPermission::getId, permissionIds));
        if (count != permissionIds.size()) {
            throw new BadRequestException("Permission ids must belong to the same application");
        }
    }

    private AuthRole requireRole(String applicationCode, Long roleId) {
        if (roleId == null || roleId <= 0) {
            throw new BadRequestException("Invalid role id");
        }
        AuthRole role = roleMapper.selectOne(new LambdaQueryWrapper<AuthRole>()
                .eq(AuthRole::getApplicationCode, applicationCode)
                .eq(AuthRole::getId, roleId)
                .eq(AuthRole::getDeleted, false)
                .last("LIMIT 1"));
        if (role == null) {
            throw new NotFoundException("Role not found in application");
        }
        return role;
    }

    private AuthUser requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("Invalid user id");
        }
        AuthUser user = userMapper.selectOne(new LambdaQueryWrapper<AuthUser>()
                .eq(AuthUser::getId, userId)
                .eq(AuthUser::getDeleted, false)
                .last("LIMIT 1"));
        if (user == null) {
            throw new NotFoundException("User not found");
        }
        return user;
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

    private Set<Long> normalizeIds(Set<Long> ids, String fieldName) {
        if (ids == null) {
            throw new BadRequestException(fieldName + " are required");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BadRequestException(fieldName + " must be positive");
        }
        return ids;
    }

    private String normalizeApplicationCode(String applicationCode) {
        if (applicationCode == null || applicationCode.isBlank()) {
            throw new BadRequestException("Application code is required");
        }
        return applicationCode.trim();
    }
}
