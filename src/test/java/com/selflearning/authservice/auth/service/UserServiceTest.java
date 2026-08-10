package com.selflearning.authservice.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.selflearning.authservice.auth.domain.AuthUser;
import com.selflearning.authservice.auth.mapper.AuthUserMapper;
import com.selflearning.authservice.auth.request.UserCreateRequest;
import com.selflearning.authservice.auth.request.UserStatusRequest;
import com.selflearning.authservice.auth.request.UserUpdateRequest;
import com.selflearning.authservice.auth.response.UserResponse;
import com.selflearning.authservice.common.web.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserServiceTest {

    private final AuthUserMapper userMapper = org.mockito.Mockito.mock(AuthUserMapper.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final PermissionContextCacheService permissionContextCacheService =
            org.mockito.Mockito.mock(PermissionContextCacheService.class);
    private final UserService userService = new UserService(
            userMapper,
            passwordEncoder,
            permissionContextCacheService);

    @Test
    void pageUsersUsesMybatisPlusPageQuery() {
        AuthUser user = new AuthUser();
        user.setId(10L);
        user.setUsername("admin");
        user.setNickname("Admin");
        user.setStatus(1);
        user.setDeleted(false);

        when(userMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            Page<AuthUser> page = invocation.getArgument(0);
            page.setRecords(List.of(user));
            page.setTotal(1);
            return page;
        });

        PageResponse<UserResponse> response = userService.pageUsers("admin", 1, 2L, 10L);

        ArgumentCaptor<Page<AuthUser>> captor = ArgumentCaptor.forClass(Page.class);
        verify(userMapper).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(10);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).username()).isEqualTo("admin");
    }

    @Test
    void createUserHashesPasswordAndTrimsFields() {
        when(userMapper.insert(any(AuthUser.class))).thenAnswer(invocation -> {
            AuthUser user = invocation.getArgument(0);
            user.setId(10L);
            return 1;
        });
        when(userMapper.selectById(10L)).thenAnswer(invocation -> {
            AuthUser user = new AuthUser();
            user.setId(10L);
            user.setUsername("admin");
            user.setNickname("Admin");
            user.setEmail("admin@example.com");
            user.setStatus(1);
            user.setDeleted(false);
            return user;
        });

        UserResponse response = userService.createUser(new UserCreateRequest(
                " admin ",
                "password123",
                " Admin ",
                " admin@example.com ",
                "   ",
                null));

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(userMapper).insert(captor.capture());
        AuthUser inserted = captor.getValue();
        assertThat(inserted.getUsername()).isEqualTo("admin");
        assertThat(passwordEncoder.matches("password123", inserted.getPasswordHash())).isTrue();
        assertThat(inserted.getNickname()).isEqualTo("Admin");
        assertThat(inserted.getEmail()).isEqualTo("admin@example.com");
        assertThat(inserted.getPhone()).isNull();
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(inserted.getDeleted()).isFalse();
        assertThat(response.username()).isEqualTo("admin");
    }

    @Test
    void updateUserRehashesPasswordAndEvictsCacheWhenStatusChanges() {
        AuthUser existing = new AuthUser();
        existing.setId(10L);
        existing.setUsername("admin");
        existing.setPasswordHash(passwordEncoder.encode("old-password"));
        existing.setStatus(1);
        existing.setDeleted(false);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(userMapper.updateById(any(AuthUser.class))).thenReturn(1);

        UserResponse response = userService.updateUser(10L, new UserUpdateRequest(
                "Admin",
                "admin@example.com",
                null,
                "new-password",
                0));

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(userMapper).updateById(captor.capture());
        AuthUser updated = captor.getValue();
        assertThat(passwordEncoder.matches("new-password", updated.getPasswordHash())).isTrue();
        assertThat(updated.getStatus()).isEqualTo(0);
        assertThat(response.status()).isEqualTo(0);
        verify(permissionContextCacheService).evictUserAllApplications(10L);
    }

    @Test
    void updateStatusEvictsCacheWhenStatusChanges() {
        AuthUser existing = new AuthUser();
        existing.setId(10L);
        existing.setUsername("admin");
        existing.setStatus(1);
        existing.setDeleted(false);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(userMapper.updateById(any(AuthUser.class))).thenReturn(1);

        userService.updateStatus(10L, new UserStatusRequest(0));

        verify(permissionContextCacheService).evictUserAllApplications(10L);
    }

    @Test
    void deleteUserMarksDeletedAndEvictsCache() {
        AuthUser existing = new AuthUser();
        existing.setId(10L);
        existing.setUsername("admin");
        existing.setStatus(1);
        existing.setDeleted(false);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(userMapper.updateById(any(AuthUser.class))).thenReturn(1);

        userService.deleteUser(10L);

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeleted()).isTrue();
        verify(permissionContextCacheService).evictUserAllApplications(10L);
    }
}
