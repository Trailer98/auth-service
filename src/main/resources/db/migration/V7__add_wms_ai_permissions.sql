-- 补齐 wms-system 的 AI 知识库/RAG 权限码到 auth-service（auth-service 是当前实际生效的鉴权后端，
-- wms-system 本地的 sys_permission/sys_role_permission 已被拦截器默认阻断，不是生效的鉴权来源）。
--
-- 权限码不带任何前缀（如 'ai-rag:ask'，不是 'wms:ai-rag:ask'）——鉴权发生在 wms-system 自己的
-- PermissionAspect（wms-admin/src/main/java/com/example/wms/admin/aspect/PermissionAspect.java:58），
-- 它直接用 @RequiresPermission 注解的字面值去匹配 auth-service /auth/context 返回的权限码集合，不做
-- 任何前缀拼接——这与 auth-service *自己*的 PermissionAspect（用于保护 auth-service 自己的
-- Controller，比如 ApplicationController）行为不同，那边才会自动拼 applicationCode 前缀。两套规则
-- 服务于两个不同的鉴权位置，此处务必使用不带前缀的字面值，否则鉴权永远不会通过（不是报错，是永远
-- 校验失败——这个坑本身就是本迁移要修的问题，不能在修的过程中再犯一次）。
--
-- application_code 必须是 'WMS'，不是 'AI_PLATFORM'：本轮已通过读 wms-system 的
-- AuthServiceClient.java 源码重新确认，它请求 /auth/context 时固定带 applicationCode=WMS
-- （application.yml:88，auth-service.application-code: WMS，且默认值也是 WMS）。AI_PLATFORM 是为
-- 未来独立 AI 平台服务预留的（V2 已经播种了 ai:admin/ai:credential:manage/ai:knowledge:manage/
-- ai:rag:ask 四个占位权限码，注意 'ai:rag:ask' 与本迁移的 'ai-rag:ask' 只差一个分隔符、极易看混，
-- 但两者 application_code 不同、字符串不同，互不冲突，本迁移不会覆盖或修改那四条），不应该混用。
--
-- 字面值来自 wms-system 源码本轮实际重新读取的结果（非猜测）：
--   AiRagAskController.java:33,41            → "ai-rag:ask"
--   KnowledgeController.java:47,53,59         → "ai-knowledge:view"
--   KnowledgeController.java:65               → "ai-knowledge:create"
--   KnowledgeController.java:73               → "ai-knowledge:update"
--   KnowledgeController.java:81               → "ai-knowledge:disable"
--   KnowledgeController.java:89               → "ai-knowledge:vectorize"
--   KnowledgeController.java:97               → "ai-knowledge:search"
--
-- ID 生成规则：id 全部是 AUTO_INCREMENT（V1__init_auth_schema.sql），本迁移不显式指定 id；
-- auth_role_permission 通过 JOIN role_code/permission_code 解析，写法与 V2/V3/V6 已验证的模式一致。
--
-- 实施前只读核实（本轮已执行，记录在 phase-0-batch-6a-result.md）：
--   目标7个权限码在 auth_permission 里精确匹配 → 0 行，无冲突；
--   auth_role 里 WMS 下的 ADMIN/DEVELOPER/WAREHOUSE_MANAGER/WAREHOUSE_OPERATOR/INVENTORY_VIEWER/
--   WMS_ADMIN 六个角色均已存在、status=1、deleted=0，可以正常接收授权。
-- 因此本迁移使用纯 INSERT（不用 "ON DUPLICATE KEY UPDATE x=x" 这类会静默掩盖冲突的 no-op）。
--
-- 角色矩阵依据（重新从 wms-system 本地 V6__add_ai_knowledge_permissions.sql /
-- V8__add_ai_rag_permissions.sql / V9__add_developer_role.sql 三个迁移推导，而不是直接照抄上一版
-- 方案草案——V9 是用一条不限权限码的 "SELECT ... FROM sys_permission"（无 WHERE 过滤）把 DEVELOPER
-- 设为"当时已存在的全部权限"，AI 权限因此落到 DEVELOPER 头上是这条全量授权的自然结果，不是 V6/V8
-- 里显式列出的，本迁移对 DEVELOPER 采用同样"全量给"的效果，直接列出全部7个码）：
--   ADMIN              —— 本轮用户明确决定：不参与本次 AI 权限授权（ADMIN 正在被重新定位为仅
--                          auth-service 自身的管理员角色，不再代表 WMS 业务管理员，另见本次会话
--                          中记录的、尚未执行的 ADMIN 权限收缩计划，与本迁移是两件独立的事）；
--   WMS_ADMIN          —— 本轮用户明确决定：加入本次 AI 权限矩阵，比照原 ADMIN 的定位，拿全部7个；
--   DEVELOPER          —— 全部7个（比照 wms-system V9 的"全量授权"效果）；
--   WAREHOUSE_MANAGER  —— ai-knowledge:view + ai-knowledge:search + ai-rag:ask；
--   WAREHOUSE_OPERATOR —— ai-knowledge:search + ai-rag:ask；
--   INVENTORY_VIEWER   —— 仅 ai-knowledge:search，不含 ai-rag:ask（该操作触发付费 DeepSeek 调用，
--                          沿用 wms-system 本地 V8__add_ai_rag_permissions.sql 已经确立的排除理由）。

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
    ('WMS', 'ai-knowledge:view',      'AI知识库查看',     'API', NULL, NULL, NULL, 180, 1, 0),
    ('WMS', 'ai-knowledge:create',    'AI知识新增',       'API', NULL, NULL, NULL, 181, 1, 0),
    ('WMS', 'ai-knowledge:update',    'AI知识编辑',       'API', NULL, NULL, NULL, 182, 1, 0),
    ('WMS', 'ai-knowledge:disable',   'AI知识启停',       'API', NULL, NULL, NULL, 183, 1, 0),
    ('WMS', 'ai-knowledge:vectorize', 'AI知识重新向量化', 'API', NULL, NULL, NULL, 184, 1, 0),
    ('WMS', 'ai-knowledge:search',    'AI知识检索测试',   'API', NULL, NULL, NULL, 185, 1, 0),
    ('WMS', 'ai-rag:ask',             'AI RAG 问答',      'API', NULL, NULL, NULL, 186, 1, 0);

-- WMS_ADMIN / DEVELOPER：全部 ai-knowledge:* + ai-rag:ask
INSERT INTO auth_role_permission (role_id, permission_id, application_code)
SELECT r.id, p.id, r.application_code
FROM auth_role r
JOIN auth_permission p ON p.application_code = r.application_code
WHERE r.application_code = 'WMS'
  AND r.role_code IN ('WMS_ADMIN', 'DEVELOPER')
  AND p.permission_code IN (
      'ai-knowledge:view', 'ai-knowledge:create', 'ai-knowledge:update',
      'ai-knowledge:disable', 'ai-knowledge:vectorize', 'ai-knowledge:search',
      'ai-rag:ask'
  );

-- WAREHOUSE_MANAGER：查看 + 检索 + 问答（不含新增/编辑/启停/向量化）
INSERT INTO auth_role_permission (role_id, permission_id, application_code)
SELECT r.id, p.id, r.application_code
FROM auth_role r
JOIN auth_permission p ON p.application_code = r.application_code
WHERE r.application_code = 'WMS'
  AND r.role_code = 'WAREHOUSE_MANAGER'
  AND p.permission_code IN ('ai-knowledge:view', 'ai-knowledge:search', 'ai-rag:ask');

-- WAREHOUSE_OPERATOR：检索 + 问答
INSERT INTO auth_role_permission (role_id, permission_id, application_code)
SELECT r.id, p.id, r.application_code
FROM auth_role r
JOIN auth_permission p ON p.application_code = r.application_code
WHERE r.application_code = 'WMS'
  AND r.role_code = 'WAREHOUSE_OPERATOR'
  AND p.permission_code IN ('ai-knowledge:search', 'ai-rag:ask');

-- INVENTORY_VIEWER：仅检索，不含 ai-rag:ask
INSERT INTO auth_role_permission (role_id, permission_id, application_code)
SELECT r.id, p.id, r.application_code
FROM auth_role r
JOIN auth_permission p ON p.application_code = r.application_code
WHERE r.application_code = 'WMS'
  AND r.role_code = 'INVENTORY_VIEWER'
  AND p.permission_code = 'ai-knowledge:search';
