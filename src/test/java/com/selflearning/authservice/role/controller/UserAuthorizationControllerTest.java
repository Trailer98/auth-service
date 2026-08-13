package com.selflearning.authservice.role.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.selflearning.authservice.auth.response.AuthContextResponse;
import com.selflearning.authservice.auth.service.AuthContextService;
import com.selflearning.authservice.common.security.PermissionAspect;
import com.selflearning.authservice.common.web.ForbiddenException;
import com.selflearning.authservice.common.web.UnauthorizedException;
import com.selflearning.authservice.role.request.UserRoleAssignRequest;
import com.selflearning.authservice.role.service.AuthorizationService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Verifies that {@link UserAuthorizationController}'s three endpoints are actually gated by
 * {@code @RequiresPermission("role:manage")}, by weaving the real, unmodified {@link PermissionAspect}
 * around a real controller instance with Spring AOP's {@link AspectJProxyFactory} — this is the same
 * annotation + aspect combination Spring Boot wires up in production via component scanning, just built
 * by hand here so the test doesn't have to boot the full application context.
 *
 * <p><b>Why not {@code @SpringBootTest} + {@code MockMvc}</b>: attempted first, but the full application
 * context currently fails to load in this project for a reason unrelated to this change — {@code
 * spring-cloud-starter-alibaba-nacos-config} enforces {@code spring.config.import} being explicitly set,
 * and neither {@code src/test/resources/application.yaml} nor any test sets it. This is a pre-existing gap:
 * {@code AuthServiceApplicationTests.contextLoads()} (already in the suite, untouched by this change) fails
 * with the identical {@code ConfigDataMissingEnvironmentPostProcessor$ImportException: No spring.config.import
 * set} before and after this batch's changes — confirmed by running it in isolation. Fixing that is outside
 * this batch's scope (Batch 1 is UserAuthorizationController only), so this test is designed to not depend on
 * it, while still exercising the real {@link PermissionAspect} class and the real {@code @RequiresPermission}
 * annotations on the controller, not a re-implementation of either.
 *
 * <p>{@link AuthContextService} and {@link AuthorizationService} are mocked directly — this test's job is
 * "does the controller enforce the permission the aspect reports", not JWT/Redis plumbing (covered separately
 * by {@code AuthContextServiceTest} and friends).
 */
class UserAuthorizationControllerTest {

    private static final String WMS = "WMS";
    private static final String AI_PLATFORM = "AI_PLATFORM";
    private static final long TARGET_USER_ID = 42L;
    private static final long CALLER_USER_ID = 42L; // same as target, for the self-escalation scenario

    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final AuthContextService authContextService = mock(AuthContextService.class);
    private final MockHttpServletRequest servletRequest = new MockHttpServletRequest();

    private UserAuthorizationController proxy;

    @BeforeEach
    void setUp() {
        UserAuthorizationController target = new UserAuthorizationController(authorizationService);
        PermissionAspect aspect = new PermissionAspect(authContextService, servletRequest);
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        proxy = factory.getProxy();
    }

    /** Simulates what Spring MVC populates on the request once {applicationCode}/{userId} are resolved. */
    private void withPathVariables(String applicationCode, long userId) {
        servletRequest.setAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("applicationCode", applicationCode, "userId", String.valueOf(userId)));
    }

    private AuthContextResponse contextWithoutRoleManage(String applicationCode) {
        return new AuthContextResponse(
                CALLER_USER_ID, "caller", applicationCode, List.of("VIEWER"), List.of(applicationCode.toLowerCase() + ":some:other"));
    }

    private AuthContextResponse contextWithRoleManage(String applicationCode) {
        return new AuthContextResponse(
                CALLER_USER_ID, "caller", applicationCode, List.of("ADMIN"), List.of(applicationCode.toLowerCase() + ":role:manage"));
    }

    // 1. 无 Token / 无有效身份 -> 401（UnauthorizedException），且不进入 Service 层
    @Test
    void listUserRoles_withoutValidIdentity_throwsUnauthorized() {
        withPathVariables(WMS, TARGET_USER_ID);
        when(authContextService.getContext(any(), eq(WMS))).thenThrow(new UnauthorizedException("Missing bearer token"));

        assertThatThrownBy(() -> proxy.listUserRoles(WMS, TARGET_USER_ID))
                .isInstanceOf(UnauthorizedException.class);

        verify(authorizationService, never()).listUserRoles(anyString(), anyLong());
    }

    // 2. 有效 Token 但无 role:manage -> 403（ForbiddenException）
    @Test
    void listUserRoles_validTokenWithoutRoleManage_throwsForbidden() {
        withPathVariables(WMS, TARGET_USER_ID);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(WMS))).thenReturn(contextWithoutRoleManage(WMS));

        assertThatThrownBy(() -> proxy.listUserRoles(WMS, TARGET_USER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(authorizationService, never()).listUserRoles(anyString(), anyLong());
    }

    // 3. 持有正确 applicationCode 下 role:manage -> 成功，请求真正到达 Service 层
    @Test
    void listUserRoles_withRoleManage_succeedsAndReachesService() {
        withPathVariables(WMS, TARGET_USER_ID);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(WMS))).thenReturn(contextWithRoleManage(WMS));
        when(authorizationService.listUserRoles(WMS, TARGET_USER_ID)).thenReturn(List.of());

        assertThat(proxy.listUserRoles(WMS, TARGET_USER_ID).data()).isEmpty();

        verify(authorizationService).listUserRoles(WMS, TARGET_USER_ID);
    }

    @Test
    void listUserPermissions_withRoleManage_succeeds() {
        withPathVariables(WMS, TARGET_USER_ID);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(WMS))).thenReturn(contextWithRoleManage(WMS));
        when(authorizationService.listUserPermissions(WMS, TARGET_USER_ID)).thenReturn(List.of());

        assertThat(proxy.listUserPermissions(WMS, TARGET_USER_ID).data()).isEmpty();

        verify(authorizationService).listUserPermissions(WMS, TARGET_USER_ID);
    }

    // 4. 用户试图给自己提权：调用者自身没有 role:manage，尝试 PUT 自己（userId == 调用者ID）的角色 -> 403，
    //    且 replaceUserRoles 从未被调用，证明提权请求在到达业务逻辑之前就被拦下。
    @Test
    void replaceUserRoles_selfEscalationAttemptWithoutPermission_throwsForbiddenAndNeverCallsService() {
        withPathVariables(WMS, CALLER_USER_ID);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(WMS))).thenReturn(contextWithoutRoleManage(WMS));
        UserRoleAssignRequest request = new UserRoleAssignRequest(Set.of(999L)); // 试图把自己加进某个高权限角色

        assertThatThrownBy(() -> proxy.replaceUserRoles(WMS, CALLER_USER_ID, request))
                .isInstanceOf(ForbiddenException.class);

        verify(authorizationService, never()).replaceUserRoles(anyString(), anyLong(), any());
    }

    // 4b. 持有 role:manage 的合法管理员执行同样的 PUT -> 成功（区分"合法管理员操作"与上面的越权尝试）
    @Test
    void replaceUserRoles_withRoleManage_succeeds() {
        withPathVariables(WMS, TARGET_USER_ID);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(WMS))).thenReturn(contextWithRoleManage(WMS));
        UserRoleAssignRequest request = new UserRoleAssignRequest(Set.of(1L));
        when(authorizationService.replaceUserRoles(eq(WMS), eq(TARGET_USER_ID), any())).thenReturn(List.of());

        assertThat(proxy.replaceUserRoles(WMS, TARGET_USER_ID, request).data()).isEmpty();

        verify(authorizationService).replaceUserRoles(eq(WMS), eq(TARGET_USER_ID), any());
    }

    // 5. 跨 applicationCode 权限不能混用：调用者在 AI_PLATFORM 下持有 role:manage，
    //    但请求的 applicationCode 是 WMS、且该用户在 WMS 下没有 role:manage -> 403，
    //    证明 AI_PLATFORM 下的权限不会被 WMS 的请求复用。
    @Test
    void listUserRoles_permissionFromDifferentApplicationCode_doesNotGrantAccess() {
        withPathVariables(WMS, TARGET_USER_ID);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(AI_PLATFORM))).thenReturn(contextWithRoleManage(AI_PLATFORM));
        when(authContextService.getContext(any(), eq(WMS))).thenReturn(contextWithoutRoleManage(WMS));

        assertThatThrownBy(() -> proxy.listUserRoles(WMS, TARGET_USER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(authorizationService, never()).listUserRoles(anyString(), anyLong());
    }

    // 6. GET 查询接口同样受保护（与 2 号场景一起，覆盖 /roles 与 /permissions 两个 GET 端点）
    @Test
    void listUserPermissions_validTokenWithoutRoleManage_throwsForbidden() {
        withPathVariables(WMS, TARGET_USER_ID);
        servletRequest.addHeader("Authorization", "Bearer valid-token");
        when(authContextService.getContext(any(), eq(WMS))).thenReturn(contextWithoutRoleManage(WMS));

        assertThatThrownBy(() -> proxy.listUserPermissions(WMS, TARGET_USER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(authorizationService, never()).listUserPermissions(anyString(), anyLong());
    }
}
