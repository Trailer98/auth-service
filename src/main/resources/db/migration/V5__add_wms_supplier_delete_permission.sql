-- 背景见 V3__add_wms_sku_permissions.sql 顶部注释。同样的缺口，第三次出现：
-- supplier:create/view/update/disable 线上已存在，唯独 supplier:delete 没有
-- （wms-system 的 SupplierController 之前只有 create+list，2026-08-11 补齐了
-- update/changeEnabled/delete，其中 delete 需要这个新权限码）。
--
-- 授权对象沿用 V3/V4 同样的观察规律：delete 类权限只授给 ADMIN 和 DEVELOPER。

INSERT INTO auth_permission (
    application_code, permission_code, permission_name, permission_type,
    parent_id, path, component, sort_order, status, deleted
) VALUES
    ('WMS', 'supplier:delete', '供应商删除', 'API', NULL, NULL, NULL, 117, 1, 0)
ON DUPLICATE KEY UPDATE permission_code = permission_code;

INSERT IGNORE INTO auth_role_permission (role_id, permission_id, application_code)
SELECT r.id, p.id, r.application_code
FROM auth_role r
JOIN auth_permission p ON p.application_code = r.application_code
WHERE r.application_code = 'WMS'
  AND r.role_code IN ('ADMIN', 'DEVELOPER')
  AND p.permission_code = 'supplier:delete';
