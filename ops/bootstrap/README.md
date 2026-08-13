# ops/bootstrap

一次性、需要人工审批的运维操作脚本，**不属于 Flyway 迁移历史**（Flyway 只管理"环境搭建"这类确定性、可重复执行的基础设施定义；谁在什么时候被授予了什么权限，是需要留痕追责的操作性决策，两者不应该混在一起）。

## grant-platform-admin.sql / revoke-platform-admin.sql

授予 / 撤销某个具体账号的 `PLATFORM_ADMIN` 角色（管理 `auth_application` 应用注册表的权限）。

**执行前必须先有审计记录**：`auth_user_role` 表没有 `granted_by`/`reason`/`created_by` 列，脚本本身无法回答"谁批准的、为什么"。执行前必须在变更工单/运维记录里写明：

```
于 <日期> 由 <批准人> 将 PLATFORM_ADMIN 授予/撤销 userId=<x>（username=<y>），理由：<原因>
```

这条记录是强制前置产出，不能省略、不能事后补。

**依赖**：`V6__add_platform_application_management.sql` 必须已经执行（`PLATFORM` 应用和 `PLATFORM_ADMIN` 角色需要先存在），脚本会在执行时校验这一点，不满足会中止而不是自动创建。

**使用方式**：见各脚本文件头部注释。两个脚本都可重复执行（幂等），执行时会打印操作前后的状态供当场核对。
