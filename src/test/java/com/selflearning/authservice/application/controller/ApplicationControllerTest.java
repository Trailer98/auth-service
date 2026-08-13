package com.selflearning.authservice.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.selflearning.authservice.application.request.ApplicationCreateRequest;
import com.selflearning.authservice.application.request.ApplicationStatusRequest;
import com.selflearning.authservice.application.request.ApplicationUpdateRequest;
import com.selflearning.authservice.application.response.ApplicationResponse;
import com.selflearning.authservice.application.service.ApplicationService;
import com.selflearning.authservice.auth.response.AuthContextResponse;
import com.selflearning.authservice.auth.service.AuthContextService;
import com.selflearning.authservice.common.security.PermissionAspect;
import com.selflearning.authservice.common.web.ForbiddenException;
import com.selflearning.authservice.common.web.PageResponse;
import com.selflearning.authservice.common.web.UnauthorizedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Verifies {@link ApplicationController}'s permission gating via the real {@link PermissionAspect}, using
 * the same {@link AspectJProxyFactory} approach as {@code UserAuthorizationControllerTest} (Batch 1) —
 * see that class's Javadoc for why: this project's {@code @SpringBootTest} contexts currently fail to load
 * (pre-existing {@code spring.config.import} gap, unrelated to this change), so this weaves the real,
 * unmodified aspect around a real controller instance instead of booting the full application.
 *
 * <p>Unlike {@code UserAuthorizationController}, {@code ApplicationController} has no {@code
 * {applicationCode}} path segment (it manages a global resource, the application registry itself), so
 * every method takes {@code applicationCode} as a request parameter purely for permission-domain
 * resolution — mirrored here via a query-string-style request parameter on the mock request.
 */
class ApplicationControllerTest {

    private static final String PLATFORM = "PLATFORM";
    private static final String WMS = "WMS";

    private final ApplicationService applicationService = mock(ApplicationService.class);
    private final AuthContextService authContextService = mock(AuthContextService.class);
    private final MockHttpServletRequest servletRequest = new MockHttpServletRequest();

    private ApplicationController proxy;

    @BeforeEach
    void setUp() {
        ApplicationController target = new ApplicationController(applicationService);
        PermissionAspect aspect = new PermissionAspect(authContextService, servletRequest);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    /**
     * ApplicationController 没有 {applicationCode} 路径变量，PermissionAspect 会退化为读取请求参数——
     * 用 setParameter 模拟这一点，同时也把它塞进 servletRequest 让 controller 方法自己能拿到
     * （两者是同一个值，模拟真实 HTTP 请求里同一个查询参数被 Spring 同时用于路由绑定和权限解析）。
     */
    private void withApplicationCodeParam(String applicationCode) {
        servletRequest.setParameter("applicationCode", applicationCode);
    }

    private AuthContextResponse contextWithout(String applicationCode, String... permissions) {
        return new AuthContextResponse(1L, "caller", applicationCode, List.of("VIEWER"), List.of(permissions));
    }

    private ApplicationResponse sampleResponse() {
        return new ApplicationResponse(1L, "CRM", "CRM System", null, 1, LocalDateTime.now(), LocalDateTime.now());
    }

    // 1. 普通用户（无任何 platform:application:* 权限）：GET 和写入均 403
    @Test
    void plainUser_getRejected() {
        withApplicationCodeParam(PLATFORM);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(PLATFORM))).thenReturn(contextWithout(PLATFORM));

        assertThatThrownBy(() -> proxy.pageApplications(null, null, null, null, PLATFORM))
                .isInstanceOf(ForbiddenException.class);
        verify(applicationService, never()).pageApplications(any(), any(), any(), any());
    }

    @Test
    void plainUser_writeRejected() {
        withApplicationCodeParam(PLATFORM);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(PLATFORM))).thenReturn(contextWithout(PLATFORM));

        assertThatThrownBy(() -> proxy.createApplication(
                        new ApplicationCreateRequest("NEW", "New App", null, null), PLATFORM))
                .isInstanceOf(ForbiddenException.class);
        verify(applicationService, never()).createApplication(any());
    }

    @Test
    void noToken_unauthorized() {
        withApplicationCodeParam(PLATFORM);
        when(authContextService.getContext(any(), eq(PLATFORM))).thenThrow(new UnauthorizedException("missing token"));

        assertThatThrownBy(() -> proxy.pageApplications(null, null, null, null, PLATFORM))
                .isInstanceOf(UnauthorizedException.class);
    }

    // 2. 只读权限（platform:application:read）：GET 放行，写入全部 403
    @Test
    void readOnlyPermission_getAllowed() {
        withApplicationCodeParam(PLATFORM);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(PLATFORM)))
                .thenReturn(contextWithout(PLATFORM, "platform:application:read"));
        when(applicationService.getApplication(1L)).thenReturn(sampleResponse());

        assertThat(proxy.getApplication(1L, PLATFORM).data().applicationCode()).isEqualTo("CRM");
    }

    @Test
    void readOnlyPermission_writeRejected() {
        withApplicationCodeParam(PLATFORM);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(PLATFORM)))
                .thenReturn(contextWithout(PLATFORM, "platform:application:read"));

        assertThatThrownBy(() -> proxy.updateStatus(1L, new ApplicationStatusRequest(0), PLATFORM))
                .isInstanceOf(ForbiddenException.class);
        verify(applicationService, never()).updateStatus(anyLong(), any());
    }

    // 3. 管理权限（platform:application:manage）：写入放行
    @Test
    void managePermission_writeAllowed() {
        withApplicationCodeParam(PLATFORM);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(PLATFORM)))
                .thenReturn(contextWithout(PLATFORM, "platform:application:manage"));
        when(applicationService.createApplication(any())).thenReturn(sampleResponse());

        ApplicationResponse response = proxy.createApplication(
                new ApplicationCreateRequest("NEW", "New App", null, null), PLATFORM).data();

        assertThat(response.applicationCode()).isEqualTo("CRM");
        verify(applicationService).createApplication(any());
    }

    @Test
    void managePermission_alsoAllowsRead() {
        // 5.3节迁移把两个码都绑给了 PLATFORM_ADMIN，所以持有 manage 的账号同时也持有 read——
        // 这里直接模拟"该账号在 PLATFORM 下的权限集合同时包含两者"这一真实场景。
        withApplicationCodeParam(PLATFORM);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(PLATFORM)))
                .thenReturn(contextWithout(PLATFORM, "platform:application:read", "platform:application:manage"));
        when(applicationService.pageApplications(any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(sampleResponse()), 1, 1, 20));

        assertThat(proxy.pageApplications(null, null, null, null, PLATFORM).data().total()).isEqualTo(1);
    }

    // 4. applicationCode 不能被伪造用于绕过权限域：调用者在 WMS 下持有 wms:application:manage
    // （假设有这么一个码），但请求声明 applicationCode=PLATFORM 且该用户在 PLATFORM 下没有权限
    // —— 必须 403，证明 WMS 下的权限不会被"伪造成"对 PLATFORM 的权限。
    @Test
    void applicationCodeCannotBeSpoofedToBorrowPermissionFromAnotherDomain() {
        withApplicationCodeParam(PLATFORM);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        // 调用者确实在 WMS 下有 manage 权限……
        when(authContextService.getContext(any(), eq(WMS)))
                .thenReturn(contextWithout(WMS, "wms:application:manage"));
        // ……但请求的 applicationCode 是 PLATFORM，而调用者在 PLATFORM 下什么权限都没有
        when(authContextService.getContext(any(), eq(PLATFORM))).thenReturn(contextWithout(PLATFORM));

        assertThatThrownBy(() -> proxy.deleteApplication(1L, PLATFORM))
                .isInstanceOf(ForbiddenException.class);
        verify(applicationService, never()).deleteApplication(anyLong());
    }

    // applicationCode 缺失（既没有路径变量也没有查询参数）—— PermissionAspect 直接 400，
    // 连"调用者是谁"都不会去查，进一步确认这个参数不是可选的旁路。
    @Test
    void missingApplicationCodeParam_badRequest() {
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        // 不调用 withApplicationCodeParam(...)

        assertThatThrownBy(() -> proxy.pageApplications(null, null, null, null, null))
                .isInstanceOf(com.selflearning.authservice.common.web.BadRequestException.class);
        verify(authContextService, never()).getContext(any(), any());
    }
}
