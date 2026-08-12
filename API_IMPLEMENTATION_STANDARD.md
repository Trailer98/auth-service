# API Implementation Standard

> 本文档基于 `auth-service` 当前真实代码提炼，不是通用 Spring Boot 教程。
> 用途：新增、修改、重构任何 `auth-service` HTTP 接口时，先读本文档，再动代码。
> 如果本文档与代码冲突，以代码为准，并同步更新本文档与 `PROJECT_CONTEXT.md`。

## 1. Read Order

1. `PROJECT_CONTEXT.md`
2. `API_IMPLEMENTATION_STANDARD.md`（本文）
3. 目标控制器/服务/DTO/Mapper
4. 如果改动涉及跨服务契约，再看 `gateway-service` 或 `wms-system` 的 `PROJECT_CONTEXT.md`

## 2. Ownership First

新增接口前先判断它是否真的属于 `auth-service`：

- 这里拥有：登录、登出、refresh、`/auth/me`、`/auth/context`、内部 token 校验、用户/角色/权限/RBAC、应用注册
- 这里不拥有：WMS 业务逻辑、Gateway 路由、前端页面逻辑

如果需求本质是“仓储业务接口”或“Gateway 路由行为”，不要把接口加在这个仓库。

## 3. Package Placement

按现有模块落位，不要新造一套平行目录：

- 认证入口：`auth/controller/AuthController.java`
- Gateway 内部鉴权入口：`auth/controller/InternalTokenController.java`
- 全局用户管理：`auth/controller/UserController.java`
- 应用内角色管理：`role/controller/RoleController.java`
- 应用内权限管理：`permission/controller/PermissionController.java`
- 用户在某应用下的角色/权限：`role/controller/UserAuthorizationController.java`
- 应用注册：`application/controller/ApplicationController.java`

配套文件跟着模块走：

- 请求 DTO：`<module>/request/`
- 响应 DTO：`<module>/response/`
- 业务逻辑：`<module>/service/`
- 持久化对象与 Mapper：`<module>/domain/`、`<module>/mapper/`
- 通用异常/响应/鉴权切面：`common/web/`、`common/security/`

## 4. Controller Contract

遵守当前仓库真实接口风格：

- 控制器统一 `@RestController`
- 返回统一使用 `ApiResponse<T>`
- 分页接口统一返回 `ApiResponse<PageResponse<T>>`
- `ApiResponse` 形状固定是 `{code,message,data}`
- `PageResponse` 形状固定是 `{items,total,page,pageSize}`
- 不直接返回 Entity、`ResponseEntity<T>` 或自定义第二套响应包装，除非是在 `GlobalExceptionHandler` 里做异常映射

新增分页接口时，沿用现有参数命名：

- `auth-service` 分页参数是 `page` / `pageSize`
- 默认页大小和 1–100 上限要与现有行为保持一致，除非本次任务明确要统一修改全部相关接口

## 5. URL and Scope Rules

这个仓库有两种真实存在的作用域模式，不能混用：

- **全局资源**：像 `UserController` 这样，资源本身不是某个单一应用私有的，URL 不带 `{applicationCode}`，但请求参数里必须带 `applicationCode`
- **应用内资源**：像 `RoleController` / `PermissionController` 这样，URL 路径带 `/applications/{applicationCode}/...`

判断规则：

- 如果资源本体就是按应用隔离的，优先走路径变量模式
- 如果资源本体是全局的，但操作权限取决于“调用方站在哪个应用视角”，走请求参数模式

不要把新的管理接口写死成 `WMS` 专用。

## 6. Validation and Error Handling

沿用现有异常模型，不要每个控制器手写响应：

- 请求体校验：`@Valid`
- 参数错误：抛 `BadRequestException`
- 未登录/Token 无效：抛 `UnauthorizedException`
- 已登录但没权限：抛 `ForbiddenException`
- 资源不存在：抛 `NotFoundException`

让 `common/web/GlobalExceptionHandler.java` 统一出响应，不要在控制器里手写 `try/catch` 返回错误 JSON。

## 7. Permission Rules

新增管理接口时，先判断是否应该接入 `@RequiresPermission`：

- 如果是用户/角色/权限这类管理面接口，默认应接入 `@RequiresPermission`
- 注解里填的是**动作码**，不是完整权限码
- 真正校验的权限码由 `PermissionAspect` 组装成 `applicationCode.toLowerCase() + ":" + value()`

因此新增接口时要同时想清楚：

1. `applicationCode` 从路径变量来还是请求参数来
2. 对应权限码是否已在 auth DB 存在
3. 需要的话是否要补 Flyway migration 来新增该权限和授予关系

## 8. Service Boundary

控制器只负责：

- 收参
- 调服务
- 返回 `ApiResponse`

业务判断放到 service：

- 分页参数归一化
- 唯一性检查
- 状态校验
- RBAC 关系变更
- cache eviction

不要把复杂业务逻辑塞进 Controller。

## 9. RBAC, Cache, and Migration Side Effects

这是这个仓库最容易漏的点。任何改动如果影响以下对象，要同时考虑副作用：

- `auth_user`
- `auth_role`
- `auth_permission`
- `auth_user_role`
- `auth_role_permission`
- `/auth/context` 返回值
- `PermissionContextCacheService` 的缓存清理

尤其注意：

- 新权限码通常不只是“加个接口注解”就完了，还要考虑 Flyway migration
- 变更用户状态、角色状态、权限状态、绑定关系时，要确认缓存是否会失效
- 文档已确认 live auth DB 与 Flyway 种子数据严重漂移；凡是依赖“现网到底已有哪个权限码/角色”的任务，先查 live DB，再写 migration

## 10. Cross-Service Contracts

以下契约变更会影响别的仓库，不能只改这里：

- `/auth/internal/token/validate`：影响 `gateway-service`
- `/auth/context`：影响 `wms-system`
- 登录/refresh/me/logout：影响 `wms-web-refactor`

这类接口新增字段、改字段名、改错误语义前，必须先看调用方上下文文档。

## 11. Checklist

- [ ] 这个接口真的属于 `auth-service`
- [ ] 控制器、DTO、Service、Mapper 放在现有模块结构里
- [ ] 返回值使用 `ApiResponse` / `PageResponse`
- [ ] 分页参数与现有 `auth-service` 约定一致
- [ ] `applicationCode` 的来源（路径/参数）设计清楚
- [ ] 管理接口是否已接入 `@RequiresPermission`
- [ ] 如需新权限码，是否补了 migration，并考虑了现网 RBAC 漂移
- [ ] 如影响 `/auth/context`，是否确认了缓存失效路径
- [ ] 如影响 Gateway/WMS/前端契约，是否同步检查调用方文档

