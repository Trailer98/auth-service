package com.selflearning.authservice.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.auth.domain.AuthUser;
import com.selflearning.authservice.auth.mapper.AuthUserMapper;
import com.selflearning.authservice.auth.response.AuthContextResponse;
import com.selflearning.authservice.permission.domain.AuthPermission;
import com.selflearning.authservice.permission.mapper.AuthPermissionMapper;
import com.selflearning.authservice.role.domain.AuthRole;
import com.selflearning.authservice.role.domain.AuthRolePermission;
import com.selflearning.authservice.role.domain.AuthUserRole;
import com.selflearning.authservice.role.mapper.AuthRoleMapper;
import com.selflearning.authservice.role.mapper.AuthRolePermissionMapper;
import com.selflearning.authservice.role.mapper.AuthUserRoleMapper;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthContextServiceTest {

    private final AuthService authService = org.mockito.Mockito.mock(AuthService.class);
    private final AuthUserMapper userMapper = org.mockito.Mockito.mock(AuthUserMapper.class);
    private final AuthApplicationMapper applicationMapper = org.mockito.Mockito.mock(AuthApplicationMapper.class);
    private final AuthUserRoleMapper userRoleMapper = org.mockito.Mockito.mock(AuthUserRoleMapper.class);
    private final AuthRoleMapper roleMapper = org.mockito.Mockito.mock(AuthRoleMapper.class);
    private final AuthRolePermissionMapper rolePermissionMapper = org.mockito.Mockito.mock(AuthRolePermissionMapper.class);
    private final AuthPermissionMapper permissionMapper = org.mockito.Mockito.mock(AuthPermissionMapper.class);
    private final PermissionContextCacheService cacheService = org.mockito.Mockito.mock(PermissionContextCacheService.class);
    private final AuthContextService authContextService = new AuthContextService(
            authService,
            userMapper,
            applicationMapper,
            userRoleMapper,
            roleMapper,
            rolePermissionMapper,
            permissionMapper,
            cacheService);

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant mapperBuilderAssistant = new MapperBuilderAssistant(new Configuration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthApplication.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthUserRole.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthRole.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthRolePermission.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthPermission.class);
    }

    @Test
    void getContextReturnsCachedContextWithoutQueryingRbacDetails() {
        AuthContextResponse cached = new AuthContextResponse(
                1L,
                "admin",
                "WMS",
                List.of("WMS_ADMIN"),
                List.of("wms:inventory:view"));
        when(authService.authenticateAccessToken("Bearer token"))
                .thenReturn(new AuthenticatedUser(1L, "admin", "jti", 100L));
        when(userMapper.selectById(1L)).thenReturn(enabledUser());
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledApplication());
        when(cacheService.get(1L, "WMS")).thenReturn(Optional.of(cached));

        AuthContextResponse response = authContextService.getContext("Bearer token", "WMS");

        assertThat(response).isEqualTo(cached);
        verify(userRoleMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(cacheService, never()).put(any(AuthContextResponse.class));
    }

    @Test
    void getContextLoadsEnabledRolesAndPermissionsThenWritesCache() {
        when(authService.authenticateAccessToken("Bearer token"))
                .thenReturn(new AuthenticatedUser(1L, "admin", "jti", 100L));
        when(userMapper.selectById(1L)).thenReturn(enabledUser());
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledApplication());
        when(cacheService.get(1L, "WMS")).thenReturn(Optional.empty());

        AuthUserRole userRole = new AuthUserRole();
        userRole.setUserId(1L);
        userRole.setApplicationCode("WMS");
        userRole.setRoleId(10L);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(userRole));

        AuthRole role = new AuthRole();
        role.setId(10L);
        role.setApplicationCode("WMS");
        role.setRoleCode("WMS_ADMIN");
        role.setStatus(1);
        role.setDeleted(false);
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(role));

        AuthRolePermission rolePermission = new AuthRolePermission();
        rolePermission.setApplicationCode("WMS");
        rolePermission.setRoleId(10L);
        rolePermission.setPermissionId(100L);
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rolePermission));

        AuthPermission permission = new AuthPermission();
        permission.setId(100L);
        permission.setApplicationCode("WMS");
        permission.setPermissionCode("wms:inventory:view");
        permission.setSortOrder(10);
        permission.setStatus(1);
        permission.setDeleted(false);
        when(permissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(permission));

        AuthContextResponse response = authContextService.getContext("Bearer token", " WMS ");

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.applicationCode()).isEqualTo("WMS");
        assertThat(response.roles()).containsExactly("WMS_ADMIN");
        assertThat(response.permissions()).containsExactly("wms:inventory:view");
        verify(cacheService).put(response);
    }

    private AuthUser enabledUser() {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setStatus(1);
        user.setDeleted(false);
        return user;
    }

    private AuthApplication enabledApplication() {
        AuthApplication application = new AuthApplication();
        application.setApplicationCode("WMS");
        application.setStatus(1);
        application.setDeleted(false);
        return application;
    }
}
