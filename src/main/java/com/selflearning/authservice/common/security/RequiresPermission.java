package com.selflearning.authservice.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a controller method requires the caller to hold a specific permission within the
 * application context the request declares.
 *
 * <p>{@code value()} is an <b>application-agnostic action code</b>, e.g. {@code "user:manage"} — not a
 * full permission code. {@link PermissionAspect} combines it with the request's {@code applicationCode}
 * parameter as {@code applicationCode.toLowerCase() + ":" + value()} to get the actual permission code to
 * check (e.g. {@code "wms:user:manage"} for {@code applicationCode=WMS}). This is what lets one endpoint
 * (e.g. the global {@code UserController}) be shared by more than one application — WMS today, an AI
 * platform or anything else later — without hardcoding which application it serves.
 *
 * <p>This convention matches how WMS's permissions happen to be named already
 * ({@code V2__init_default_admin.sql}: {@code wms:user:manage}, {@code wms:role:manage}, ...). It is
 * <b>not</b> retroactively enforced on every existing seeded permission — e.g. AI_PLATFORM's seeded codes
 * use an {@code ai:} prefix, not {@code ai_platform:}, because they predate this annotation and cover a
 * different concern (AI credentials/knowledge, not user management). Any *new* application wiring itself
 * up to an endpoint that uses this annotation needs to seed its own {@code <applicationCode-lowercased>:
 * <value>} permission code and grant it via a role — this annotation only checks, it never creates.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {
    String value();
}
