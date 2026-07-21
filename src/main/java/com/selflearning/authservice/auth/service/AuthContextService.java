package com.selflearning.authservice.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.auth.domain.AuthUser;
import com.selflearning.authservice.auth.mapper.AuthUserMapper;
import com.selflearning.authservice.auth.response.AuthContextResponse;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.UnauthorizedException;
import com.selflearning.authservice.permission.domain.AuthPermission;
import com.selflearning.authservice.permission.mapper.AuthPermissionMapper;
import com.selflearning.authservice.role.domain.AuthRole;
import com.selflearning.authservice.role.domain.AuthRolePermission;
import com.selflearning.authservice.role.domain.AuthUserRole;
import com.selflearning.authservice.role.mapper.AuthRoleMapper;
import com.selflearning.authservice.role.mapper.AuthRolePermissionMapper;
import com.selflearning.authservice.role.mapper.AuthUserRoleMapper;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AuthContextService {

    private static final int STATUS_ENABLED = 1;

    private final AuthService authService;
    private final AuthUserMapper userMapper;
    private final AuthApplicationMapper applicationMapper;
    private final AuthUserRoleMapper userRoleMapper;
    private final AuthRoleMapper roleMapper;
    private final AuthRolePermissionMapper rolePermissionMapper;
    private final AuthPermissionMapper permissionMapper;
    private final PermissionContextCacheService cacheService;

    public AuthContextService(
            AuthService authService,
            AuthUserMapper userMapper,
            AuthApplicationMapper applicationMapper,
            AuthUserRoleMapper userRoleMapper,
            AuthRoleMapper roleMapper,
            AuthRolePermissionMapper rolePermissionMapper,
            AuthPermissionMapper permissionMapper,
            PermissionContextCacheService cacheService) {
        this.authService = authService;
        this.userMapper = userMapper;
        this.applicationMapper = applicationMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.cacheService = cacheService;
    }

    public AuthContextResponse getContext(String authorizationHeader, String applicationCode) {
        AuthenticatedUser authenticatedUser = authService.authenticateAccessToken(authorizationHeader);
        AuthUser user = requireEnabledUser(authenticatedUser.userId());
        String normalizedApplicationCode = requireEnabledApplication(applicationCode);

        return cacheService.get(user.getId(), normalizedApplicationCode)
                .orElseGet(() -> loadAndCacheContext(user, normalizedApplicationCode));
    }

    private AuthContextResponse loadAndCacheContext(AuthUser user, String applicationCode) {
        List<AuthUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<AuthUserRole>()
                .eq(AuthUserRole::getUserId, user.getId())
                .eq(AuthUserRole::getApplicationCode, applicationCode));
        if (userRoles.isEmpty()) {
            throw new UnauthorizedException("User is not authorized for application");
        }

        Set<Long> assignedRoleIds = userRoles.stream()
                .map(AuthUserRole::getRoleId)
                .collect(Collectors.toSet());

        List<AuthRole> enabledRoles = roleMapper.selectList(new LambdaQueryWrapper<AuthRole>()
                        .eq(AuthRole::getApplicationCode, applicationCode)
                        .eq(AuthRole::getDeleted, false)
                        .eq(AuthRole::getStatus, STATUS_ENABLED)
                        .in(AuthRole::getId, assignedRoleIds))
                .stream()
                .sorted(Comparator.comparing(AuthRole::getId))
                .toList();

        Set<Long> enabledRoleIds = enabledRoles.stream()
                .map(AuthRole::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> permissions = List.of();
        if (!enabledRoleIds.isEmpty()) {
            Set<Long> permissionIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<AuthRolePermission>()
                            .eq(AuthRolePermission::getApplicationCode, applicationCode)
                            .in(AuthRolePermission::getRoleId, enabledRoleIds))
                    .stream()
                    .map(AuthRolePermission::getPermissionId)
                    .collect(Collectors.toSet());

            if (!permissionIds.isEmpty()) {
                permissions = permissionMapper.selectList(new LambdaQueryWrapper<AuthPermission>()
                                .eq(AuthPermission::getApplicationCode, applicationCode)
                                .eq(AuthPermission::getDeleted, false)
                                .eq(AuthPermission::getStatus, STATUS_ENABLED)
                                .in(AuthPermission::getId, permissionIds))
                        .stream()
                        .sorted(Comparator.comparing(AuthPermission::getSortOrder).thenComparing(AuthPermission::getId))
                        .map(AuthPermission::getPermissionCode)
                        .distinct()
                        .toList();
            }
        }

        AuthContextResponse response = new AuthContextResponse(
                user.getId(),
                user.getUsername(),
                applicationCode,
                enabledRoles.stream().map(AuthRole::getRoleCode).distinct().toList(),
                permissions);
        cacheService.put(response);
        return response;
    }

    private AuthUser requireEnabledUser(Long userId) {
        AuthUser user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            cacheService.evictUserAllApplications(userId);
            throw new UnauthorizedException("User not found");
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(user.getStatus())) {
            cacheService.evictUserAllApplications(userId);
            throw new UnauthorizedException("User is disabled");
        }
        return user;
    }

    private String requireEnabledApplication(String applicationCode) {
        if (applicationCode == null || applicationCode.isBlank()) {
            throw new BadRequestException("Application code is required");
        }
        String normalizedApplicationCode = applicationCode.trim();
        AuthApplication application = applicationMapper.selectOne(new LambdaQueryWrapper<AuthApplication>()
                .eq(AuthApplication::getApplicationCode, normalizedApplicationCode)
                .eq(AuthApplication::getDeleted, false)
                .last("LIMIT 1"));
        if (application == null) {
            evictApplicationContext(normalizedApplicationCode);
            throw new BadRequestException("Application not found");
        }
        if (!Integer.valueOf(STATUS_ENABLED).equals(application.getStatus())) {
            evictApplicationContext(normalizedApplicationCode);
            throw new BadRequestException("Application is disabled");
        }
        return normalizedApplicationCode;
    }

    private void evictApplicationContext(String applicationCode) {
        Set<Long> userIds = userRoleMapper.selectList(new LambdaQueryWrapper<AuthUserRole>()
                        .eq(AuthUserRole::getApplicationCode, applicationCode))
                .stream()
                .map(AuthUserRole::getUserId)
                .collect(Collectors.toSet());
        cacheService.evictUsersApplication(userIds, applicationCode);
    }
}
