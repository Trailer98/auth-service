INSERT INTO auth_application (
    application_code,
    application_name,
    description,
    status,
    deleted
) VALUES
    ('WMS', 'WMS', '仓储管理系统', 1, 0),
    ('AI_PLATFORM', 'AI Platform', 'AI 平台', 1, 0)
ON DUPLICATE KEY UPDATE application_code = application_code;

INSERT INTO auth_user (
    username,
    password_hash,
    nickname,
    email,
    phone,
    status,
    deleted
) VALUES (
    'admin',
    '$2a$10$zrqtb8NxXWaYK89ez44gl.kpM/mmnqu/HvLuvDZmubD8f6fQw/aY2',
    '内置管理员',
    NULL,
    NULL,
    1,
    0
)
ON DUPLICATE KEY UPDATE username = username;

INSERT INTO auth_role (
    application_code,
    role_code,
    role_name,
    description,
    status,
    deleted
) VALUES
    ('WMS', 'WMS_ADMIN', 'WMS 管理员', 'WMS 系统内置管理员角色', 1, 0),
    ('AI_PLATFORM', 'AI_ADMIN', 'AI 平台管理员', 'AI 平台内置管理员角色', 1, 0)
ON DUPLICATE KEY UPDATE role_code = role_code;

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
    ('WMS', 'wms:admin', 'WMS 管理权限', 'API', NULL, NULL, NULL, 10, 1, 0),
    ('WMS', 'wms:user:manage', 'WMS 用户管理', 'API', NULL, NULL, NULL, 20, 1, 0),
    ('WMS', 'wms:role:manage', 'WMS 角色管理', 'API', NULL, NULL, NULL, 30, 1, 0),
    ('WMS', 'wms:permission:manage', 'WMS 权限管理', 'API', NULL, NULL, NULL, 40, 1, 0),
    ('AI_PLATFORM', 'ai:admin', 'AI 平台管理权限', 'API', NULL, NULL, NULL, 10, 1, 0),
    ('AI_PLATFORM', 'ai:credential:manage', 'AI 凭据管理', 'API', NULL, NULL, NULL, 20, 1, 0),
    ('AI_PLATFORM', 'ai:knowledge:manage', 'AI 知识库管理', 'API', NULL, NULL, NULL, 30, 1, 0),
    ('AI_PLATFORM', 'ai:rag:ask', 'AI RAG 问答', 'API', NULL, NULL, NULL, 40, 1, 0)
ON DUPLICATE KEY UPDATE permission_code = permission_code;

INSERT IGNORE INTO auth_user_role (
    user_id,
    application_code,
    role_id
)
SELECT
    u.id,
    r.application_code,
    r.id
FROM auth_user u
JOIN auth_role r ON r.role_code = 'WMS_ADMIN' AND r.application_code = 'WMS'
WHERE u.username = 'admin';

INSERT IGNORE INTO auth_user_role (
    user_id,
    application_code,
    role_id
)
SELECT
    u.id,
    r.application_code,
    r.id
FROM auth_user u
JOIN auth_role r ON r.role_code = 'AI_ADMIN' AND r.application_code = 'AI_PLATFORM'
WHERE u.username = 'admin';

INSERT IGNORE INTO auth_role_permission (
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
WHERE r.application_code = 'WMS'
  AND r.role_code = 'WMS_ADMIN'
  AND p.permission_code IN (
      'wms:admin',
      'wms:user:manage',
      'wms:role:manage',
      'wms:permission:manage'
  );

INSERT IGNORE INTO auth_role_permission (
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
WHERE r.application_code = 'AI_PLATFORM'
  AND r.role_code = 'AI_ADMIN'
  AND p.permission_code IN (
      'ai:admin',
      'ai:credential:manage',
      'ai:knowledge:manage',
      'ai:rag:ask'
  );
