-- 新增 PLATFORM 保留应用、PLATFORM_ADMIN 角色，以及 application:read / application:manage 两个
-- 权限点并绑定给 PLATFORM_ADMIN。这一步只做"环境搭建"——不插入任何 auth_user_role 行，"哪个具体账号
-- 应该拿到 PLATFORM_ADMIN"是一次性的人工审批决策，由 ops/bootstrap/grant-platform-admin.sql 独立执行，
-- 不进 Flyway 版本历史（理由见该脚本头部注释）。
--
-- ID 生成规则：id 全部是 AUTO_INCREMENT（V1__init_auth_schema.sql），本迁移不显式指定 id；
-- 角色-权限绑定通过 JOIN role_code/permission_code 解析，写法与 V2/V3 已验证的模式一致。
--
-- 本迁移刻意不使用 "ON DUPLICATE KEY UPDATE x = x" 这类会静默掩盖冲突的 no-op：Flyway 版本化迁移
-- 本身就只会成功执行一次，如果 PLATFORM/PLATFORM_ADMIN/这两个权限码在执行前已经以任何形式存在
-- （包括被软删除 deleted=1 或被停用 status=0 的历史记录），应该让 INSERT 因唯一键冲突而报错并中止，
-- 而不是被静默吞掉——冲突意味着这次迁移的前提假设不成立，需要人工介入核实，不能自动吞掉或自动恢复
-- 软删除/停用记录。
--
-- 执行前已做的只读核实（本轮，未在此文件里体现，记录在 phase-0-batch-5-result.md）：
--   SELECT ... FROM auth_application WHERE application_code = 'PLATFORM'              → 0 行
--   SELECT ... FROM auth_role WHERE role_code = 'PLATFORM_ADMIN'                       → 0 行
--   SELECT ... FROM auth_permission WHERE permission_code IN
--       ('platform:application:read','platform:application:manage')                   → 0 行
-- 三项检查均为空，无冲突，因此本迁移可以安全使用纯 INSERT。

INSERT INTO auth_application (
    application_code,
    application_name,
    description,
    status,
    deleted
) VALUES (
    'PLATFORM',
    'Identity Platform',
    '身份平台自身管理面（应用注册表等跨应用资源），非业务消费方，不接受业务应用注册用户',
    1,
    0
);

INSERT INTO auth_role (
    application_code,
    role_code,
    role_name,
    description,
    status,
    deleted
) VALUES (
    'PLATFORM',
    'PLATFORM_ADMIN',
    '平台超级管理员',
    '可管理应用注册表（auth_application）。不通过本迁移授予任何具体账号，见 ops/bootstrap/grant-platform-admin.sql',
    1,
    0
);

-- 读/写拆分为两个独立权限点：pageApplications/getApplication 是纯查询，与
-- createApplication/updateApplication/updateStatus/deleteApplication 这类写操作是完全不同风险等级
-- 的操作，未来如果要新增一个只读审计角色，只需绑 platform:application:read 即可，不需要改代码。
INSERT INTO auth_permission (
    application_code,
    permission_code,
    permission_name,
    permission_type,
    parent_id,
    path,
    component,
    sort_order,
    status,
    deleted
) VALUES
    ('PLATFORM', 'platform:application:read',   '应用注册表查看', 'API', NULL, NULL, NULL, 10, 1, 0),
    ('PLATFORM', 'platform:application:manage', '应用注册表管理', 'API', NULL, NULL, NULL, 20, 1, 0);

-- PLATFORM_ADMIN 同时拿到读和写：读写拆分是为将来留扩展空间（比如给审计人员只绑 read），不是现在就
-- 要一个只读角色，所以两个码都直接绑给同一个角色，不产生"能管理但不能查看"这种反直觉的中间状态。
INSERT INTO auth_role_permission (
    role_id,
    permission_id,
    application_code
)
SELECT
    r.id,
    p.id,
    r.application_code
FROM auth_role r
JOIN auth_permission p ON p.application_code = r.application_code
WHERE r.application_code = 'PLATFORM'
  AND r.role_code = 'PLATFORM_ADMIN'
  AND p.permission_code IN ('platform:application:read', 'platform:application:manage');
