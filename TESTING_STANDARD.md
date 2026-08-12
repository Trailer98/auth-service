# Testing Standard

> 本文档基于 `auth-service` 当前真实代码和现有测试提炼。
> 这个仓库当前最成熟的测试模式是：**窄范围 JUnit 5 + Mockito 服务层测试**，不是大而全的 Spring 全上下文测试。

## 1. Read Order

1. `PROJECT_CONTEXT.md`
2. `API_IMPLEMENTATION_STANDARD.md`
3. `TESTING_STANDARD.md`（本文）
4. 目标模块相邻的现有测试类

## 2. Current Tooling Reality

- Maven + `spring-boot-starter-test`
- JUnit 5
- Mockito（现有测试直接 `org.mockito.Mockito.mock(...)`）
- `src/test/resources/application.yaml` 使用 H2 内存库
- 测试配置里 `flyway.enabled=false`
- 测试配置里 Nacos discovery 已禁用
- 现有大多数有效测试都不启动 Spring，而是直接测 Service

## 3. Default Test Style

新增或重构代码时，默认优先按下面顺序选测试方式：

1. **Service / business logic 改动**：先补一个不启动 Spring 的 JUnit + Mockito 测试
2. **Controller / HTTP 契约改动**：保留上面的 Service 测试，再补一个更贴近 HTTP 绑定 / 校验 / `ApiResponse` / 权限切面的测试
3. **Bootstrap / 配置装配改动**：只有在真的改启动配置、Nacos、Bean 装配时，才把 `@SpringBootTest` 当默认主验证方式

不要把每个改动都直接升级成全上下文测试。

## 4. Minimum Coverage Required

任何新增或重构的 endpoint / service 路径，至少覆盖：

- 一个 happy path
- 一个失败路径：如校验失败、未找到、无权限、重复数据、非法状态
- 本次改动涉及到的副作用：如密码哈希、逻辑删除、状态更新、缓存淘汰、角色/权限关系更新
- 如果动了分页，覆盖 `page` / `pageSize` 归一化或边界
- 如果动了 `@RequiresPermission` / `applicationCode` 相关逻辑，覆盖权限码拼接或取值来源

## 5. Commands

优先跑定向测试：

- `mvn -Dtest=UserServiceTest test`
- `mvn -Dtest=AuthContextServiceTest test`

需要更大范围时再跑：

- `mvn test`

## 6. Baseline Caveats

- `PROJECT_CONTEXT.md` 已记录：`AuthServiceApplicationTests.contextLoads` 在当前环境里有已知基线问题。若它**原样**失败且本次改动并未触及启动 / Nacos / 全局配置，不要把它误判成新回归，但也不要隐瞒。
- 这个测试配置下 `Flyway` 是关闭的，所以 **Flyway SQL 并没有被 H2 自动真实执行**。如果任务里新增了 migration，结论里要明确说清楚：它是“代码审阅通过”还是“额外在别处验证过”，不要模糊写成“已测试”。

## 7. Done Means

一个 auth-service 任务只有在下面三点都满足时才算“测试完成”：

- 按改动范围补了对应层级的测试或更新了现有测试
- 至少跑了定向测试；如果声明跑了全量，也要写明
- 最终说明里写清楚实际命令、测试类名、以及命中的关键场景
