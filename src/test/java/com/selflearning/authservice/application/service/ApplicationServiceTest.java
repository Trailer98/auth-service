package com.selflearning.authservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.selflearning.authservice.application.request.ApplicationCreateRequest;
import com.selflearning.authservice.application.request.ApplicationStatusRequest;
import com.selflearning.authservice.application.request.ApplicationUpdateRequest;
import com.selflearning.authservice.application.response.ApplicationResponse;
import com.selflearning.authservice.application.domain.AuthApplication;
import com.selflearning.authservice.application.mapper.AuthApplicationMapper;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.PageResponse;
import com.selflearning.authservice.auth.service.PermissionContextCacheService;
import com.selflearning.authservice.role.mapper.AuthUserRoleMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class ApplicationServiceTest {

    private final AuthApplicationMapper applicationMapper = org.mockito.Mockito.mock(AuthApplicationMapper.class);
    private final AuthUserRoleMapper userRoleMapper = org.mockito.Mockito.mock(AuthUserRoleMapper.class);
    private final PermissionContextCacheService permissionContextCacheService =
            org.mockito.Mockito.mock(PermissionContextCacheService.class);
    private final ApplicationService applicationService = new ApplicationService(
            applicationMapper,
            userRoleMapper,
            permissionContextCacheService);

    @Test
    void pageApplicationsUsesMybatisPlusPageQuery() {
        AuthApplication application = new AuthApplication();
        application.setId(10L);
        application.setApplicationCode("CRM");
        application.setApplicationName("CRM System");
        application.setStatus(1);
        application.setDeleted(false);

        when(applicationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            Page<AuthApplication> page = invocation.getArgument(0);
            page.setRecords(List.of(application));
            page.setTotal(1);
            return page;
        });

        PageResponse<ApplicationResponse> response = applicationService.pageApplications("CRM", 1, 2L, 10L);

        ArgumentCaptor<Page<AuthApplication>> captor = ArgumentCaptor.forClass(Page.class);
        verify(applicationMapper).selectPage(captor.capture(), any(LambdaQueryWrapper.class));
        assertThat(captor.getValue().getCurrent()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(10);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).applicationCode()).isEqualTo("CRM");
    }

    @Test
    void createApplicationDefaultsToEnabledAndTrimsFields() {
        when(applicationMapper.insert(any(AuthApplication.class))).thenAnswer(invocation -> {
            AuthApplication application = invocation.getArgument(0);
            application.setId(10L);
            return 1;
        });
        when(applicationMapper.selectById(10L)).thenAnswer(invocation -> {
            AuthApplication application = new AuthApplication();
            application.setId(10L);
            application.setApplicationCode("CRM");
            application.setApplicationName("CRM System");
            application.setStatus(1);
            application.setDeleted(false);
            return application;
        });

        ApplicationResponse response = applicationService.createApplication(new ApplicationCreateRequest(
                " CRM ",
                " CRM System ",
                "   ",
                null));

        ArgumentCaptor<AuthApplication> captor = ArgumentCaptor.forClass(AuthApplication.class);
        verify(applicationMapper).insert(captor.capture());
        AuthApplication inserted = captor.getValue();
        assertThat(inserted.getApplicationCode()).isEqualTo("CRM");
        assertThat(inserted.getApplicationName()).isEqualTo("CRM System");
        assertThat(inserted.getDescription()).isNull();
        assertThat(inserted.getStatus()).isEqualTo(1);
        assertThat(inserted.getDeleted()).isFalse();
        assertThat(response.applicationCode()).isEqualTo("CRM");
    }

    @Test
    void createApplicationRejectsDuplicateApplicationCode() {
        when(applicationMapper.insert(any(AuthApplication.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> applicationService.createApplication(new ApplicationCreateRequest(
                "CRM",
                "CRM System",
                null,
                1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Application code already exists");
    }

    @Test
    void updateApplicationKeepsApplicationCodeImmutable() {
        AuthApplication existing = new AuthApplication();
        existing.setId(10L);
        existing.setApplicationCode("CRM");
        existing.setApplicationName("Old Name");
        existing.setStatus(1);
        existing.setDeleted(false);

        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(applicationMapper.updateById(any(AuthApplication.class))).thenReturn(1);

        ApplicationResponse response = applicationService.updateApplication(10L, new ApplicationUpdateRequest(
                "New Name",
                "New Description",
                0));

        ArgumentCaptor<AuthApplication> captor = ArgumentCaptor.forClass(AuthApplication.class);
        verify(applicationMapper).updateById(captor.capture());
        AuthApplication updated = captor.getValue();
        assertThat(updated.getApplicationCode()).isEqualTo("CRM");
        assertThat(updated.getApplicationName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("New Description");
        assertThat(updated.getStatus()).isEqualTo(0);
        assertThat(response.applicationCode()).isEqualTo("CRM");
    }

    private AuthApplication platformApplication() {
        AuthApplication platform = new AuthApplication();
        platform.setId(1L);
        platform.setApplicationCode("PLATFORM");
        platform.setApplicationName("Identity Platform");
        platform.setStatus(1);
        platform.setDeleted(false);
        return platform;
    }

    // PLATFORM 不能被禁用或删除（6.6/6.7 场景）——通过 updateStatus 这条路径尝试禁用
    @Test
    void updateStatusRejectsDisablingPlatform() {
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(platformApplication());

        assertThatThrownBy(() -> applicationService.updateStatus(1L, new ApplicationStatusRequest(0)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PLATFORM");

        verify(applicationMapper, never()).updateById(any(AuthApplication.class));
    }

    // 同一条不变量，另一条能触碰 status 字段的路径：updateApplication（PUT）本身也能改 status，
    // 必须同样被挡住，不能只挡 updateStatus（PATCH）这一条路径。
    @Test
    void updateApplicationRejectsDisablingPlatformViaStatusField() {
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(platformApplication());

        assertThatThrownBy(() -> applicationService.updateApplication(1L, new ApplicationUpdateRequest(
                        "Identity Platform", "desc", 0)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PLATFORM");

        verify(applicationMapper, never()).updateById(any(AuthApplication.class));
    }

    // updateApplication 不改 status（request.status()==null）时，对 PLATFORM 改名称/描述应该正常放行
    // ——证明这条保护只挡"禁用尝试"，不是把 PLATFORM 整行锁死到不能编辑任何字段。
    @Test
    void updateApplicationAllowsNonStatusEditsToPlatform() {
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(platformApplication());
        when(applicationMapper.updateById(any(AuthApplication.class))).thenReturn(1);

        ApplicationResponse response = applicationService.updateApplication(1L, new ApplicationUpdateRequest(
                "Identity Platform (renamed)", "new desc", null));

        assertThat(response.applicationCode()).isEqualTo("PLATFORM");
        verify(applicationMapper).updateById(any(AuthApplication.class));
    }

    @Test
    void deleteApplicationRejectsDeletingPlatform() {
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(platformApplication());

        assertThatThrownBy(() -> applicationService.deleteApplication(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("PLATFORM");

        verify(applicationMapper, never()).updateById(any(AuthApplication.class));
    }

    // 回归验证：其他应用不受 PLATFORM 保护逻辑影响，仍然可以正常禁用/删除
    @Test
    void updateStatusStillAllowsDisablingNonPlatformApplication() {
        AuthApplication crm = new AuthApplication();
        crm.setId(10L);
        crm.setApplicationCode("CRM");
        crm.setApplicationName("CRM System");
        crm.setStatus(1);
        crm.setDeleted(false);
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(crm);
        when(applicationMapper.updateById(any(AuthApplication.class))).thenReturn(1);

        ApplicationResponse response = applicationService.updateStatus(10L, new ApplicationStatusRequest(0));

        assertThat(response.applicationCode()).isEqualTo("CRM");
        verify(applicationMapper).updateById(any(AuthApplication.class));
    }

    @Test
    void deleteApplicationStillAllowsDeletingNonPlatformApplication() {
        AuthApplication crm = new AuthApplication();
        crm.setId(10L);
        crm.setApplicationCode("CRM");
        crm.setApplicationName("CRM System");
        crm.setStatus(1);
        crm.setDeleted(false);
        when(applicationMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(crm);
        when(applicationMapper.updateById(any(AuthApplication.class))).thenReturn(1);

        applicationService.deleteApplication(10L);

        ArgumentCaptor<AuthApplication> captor = ArgumentCaptor.forClass(AuthApplication.class);
        verify(applicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getDeleted()).isTrue();
    }
}
