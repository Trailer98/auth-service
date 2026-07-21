package com.selflearning.authservice.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.auth.domain.AuthUser;
import com.selflearning.authservice.auth.mapper.AuthUserMapper;
import com.selflearning.authservice.auth.service.PermissionContextCacheService;
import com.selflearning.authservice.permission.domain.AuthPermission;
import com.selflearning.authservice.permission.mapper.AuthPermissionMapper;
import com.selflearning.authservice.role.domain.AuthRole;
import com.selflearning.authservice.role.domain.AuthUserRole;
import com.selflearning.authservice.role.mapper.AuthRoleMapper;
import com.selflearning.authservice.role.mapper.AuthRolePermissionMapper;
import com.selflearning.authservice.role.mapper.AuthUserRoleMapper;
import com.selflearning.authservice.role.request.RolePermissionAssignRequest;
import com.selflearning.authservice.role.request.UserRoleAssignRequest;
import com.selflearning.authservice.role.response.RoleResponse;
import com.selflearning.authservice.common.web.BadRequestException;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {

    private final AuthApplicationMapper applicationMapper = org.mockito.Mockito.mock(AuthApplicationMapper.class);
    private final AuthUserMapper userMapper = org.mockito.Mockito.mock(AuthUserMapper.class);
    private final AuthRoleMapper roleMapper = org.mockito.Mockito.mock(AuthRoleMapper.class);
    private final AuthPermissionMapper permissionMapper = org.mockito.Mockito.mock(AuthPermissionMapper.class);
    private final AuthUserRoleMapper userRoleMapper = org.mockito.Mockito.mock(AuthUserRoleMapper.class);
    private final AuthRolePermissionMapper rolePermissionMapper = org.mockito.Mockito.mock(AuthRolePermissionMapper.class);
    private final PermissionContextCacheService permissionContextCacheService =
            org.mockito.Mockito.mock(PermissionContextCacheService.class);
    private final AuthorizationService authorizationService = new AuthorizationService(
            applicationMapper,
            userMapper,
            roleMapper,
            permissionMapper,
            userRoleMapper,
            rolePermissionMapper,
            permissionContextCacheService);

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant mapperBuilderAssistant = new MapperBuilderAssistant(new Configuration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthApplication.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthUser.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthRole.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthPermission.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, AuthUserRole.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(mapperBuilderAssistant, com.selflearning.authservice.role.domain.AuthRolePermission.class);
    }

    @Test
    void replaceUserRolesRejectsRoleIdsOutsideApplication() {
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(application("CRM"));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user(100L));
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> authorizationService.replaceUserRoles(
                "CRM",
                100L,
                new UserRoleAssignRequest(Set.of(10L, 20L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Role ids must belong to the same application");

        verify(userRoleMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(userRoleMapper, never()).insert(any(AuthUserRole.class));
    }

    @Test
    void replaceRolePermissionsRejectsPermissionIdsOutsideApplication() {
        AuthRole role = new AuthRole();
        role.setId(10L);
        role.setApplicationCode("CRM");
        role.setDeleted(false);

        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(application("CRM"));
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(role);
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> authorizationService.replaceRolePermissions(
                "CRM",
                10L,
                new RolePermissionAssignRequest(Set.of(100L, 200L))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Permission ids must belong to the same application");

        verify(rolePermissionMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void replaceUserRolesAllowsClearingOnlyCurrentApplicationRoles() {
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(application("CRM"));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user(100L));
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<RoleResponse> response = authorizationService.replaceUserRoles(
                "CRM",
                100L,
                new UserRoleAssignRequest(Set.of()));

        assertThat(response).isEmpty();
        verify(userRoleMapper).delete(any(LambdaQueryWrapper.class));
        verify(userRoleMapper, never()).insert(any(AuthUserRole.class));
    }

    private AuthApplication application(String applicationCode) {
        AuthApplication application = new AuthApplication();
        application.setApplicationCode(applicationCode);
        application.setDeleted(false);
        return application;
    }

    private AuthUser user(Long userId) {
        AuthUser user = new AuthUser();
        user.setId(userId);
        user.setDeleted(false);
        return user;
    }
}
