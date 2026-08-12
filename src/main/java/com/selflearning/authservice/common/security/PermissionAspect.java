package com.selflearning.authservice.common.security;

import com.selflearning.authservice.auth.response.AuthContextResponse;
import com.selflearning.authservice.auth.service.AuthContextService;
import com.selflearning.authservice.common.web.BadRequestException;
import com.selflearning.authservice.common.web.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Enforces {@link RequiresPermission} on controller methods.
 *
 * <p>auth-service is itself the identity/RBAC source of truth, so this does not call out over HTTP the
 * way wms-system's equivalent aspect calls auth-service — it reuses {@link AuthContextService} (the same
 * permission-resolution + Redis-cache path already used by {@code GET /auth/context}) directly.
 *
 * <p><b>Not application-specific.</b> {@code applicationCode} is resolved per-request, from whichever of
 * these the endpoint actually has (checked in this order):
 * <ol>
 *   <li>a {@code {applicationCode}} path variable — e.g. {@code RoleController}/{@code PermissionController},
 *       which are already scoped under {@code /applications/{applicationCode}/...}</li>
 *   <li>an {@code applicationCode} query/request parameter — e.g. {@code UserController}, which manages a
 *       global resource with no natural path segment to put it in</li>
 * </ol>
 * There is no default and no hardcoded application — every protected endpoint must supply one of the
 * above, or the request fails with 400 before any permission check runs.
 */
@Aspect
@Component
public class PermissionAspect {

    private static final String APPLICATION_CODE_PARAM = "applicationCode";

    private final AuthContextService authContextService;
    private final HttpServletRequest request;

    public PermissionAspect(AuthContextService authContextService, HttpServletRequest request) {
        this.authContextService = authContextService;
        this.request = request;
    }

    @Around("@annotation(requiresPermission)")
    public Object around(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        String applicationCode = resolveApplicationCode();
        if (!StringUtils.hasText(applicationCode)) {
            throw new BadRequestException(APPLICATION_CODE_PARAM + " is required");
        }

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Throws UnauthorizedException (401) itself if the token is missing/invalid/expired, or if the
        // caller has no role at all under this applicationCode — both map to 401 via
        // GlobalExceptionHandler, which is the existing, correct behavior for "not authenticated for this
        // application" (as opposed to 403, "authenticated but missing this specific permission").
        AuthContextResponse context = authContextService.getContext(authorizationHeader, applicationCode);

        String requiredPermission = applicationCode.toLowerCase() + ":" + requiresPermission.value();
        if (CollectionUtils.isEmpty(context.permissions()) || !context.permissions().contains(requiredPermission)) {
            throw new ForbiddenException("missing permission: " + requiredPermission);
        }

        return joinPoint.proceed();
    }

    /**
     * Path variables aren't visible via {@link HttpServletRequest#getParameter}; Spring exposes them
     * separately as a request attribute once the handler has been resolved (which happens before this
     * advice runs). Falls back to a query/request parameter for endpoints with no
     * {@code {applicationCode}} path segment.
     */
    @SuppressWarnings("unchecked")
    private String resolveApplicationCode() {
        Object pathVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables instanceof Map<?, ?> variables) {
            Object fromPath = ((Map<String, ?>) variables).get(APPLICATION_CODE_PARAM);
            if (fromPath instanceof String value && StringUtils.hasText(value)) {
                return value;
            }
        }
        return request.getParameter(APPLICATION_CODE_PARAM);
    }
}
