-- 背景见 V3__add_wms_sku_permissions.sql 顶部注释：live auth 库的 WMS 权限/角色数据早已超出
-- Flyway 迁移文件的记录范围。这里同样只补一个缺口：warehouse:create/view/update/disable 线上已
-- 存在，唯独 warehouse:delete 没有（wms-system 的 WarehouseController 之前只有 create+list，
-- 2026-08-11 补齐了 update/changeEnabled/delete 三个端点，其中 delete 需要这个新权限码）。
--
-- 授权对象沿用 V3 同样的观察规律：delete 类权限目前只授给 ADMIN 和 DEVELOPER 两个角色
-- （customer:delete、sku:delete、sys-dict:delete 均如此），WAREHOUSE_MANAGER/OPERATOR/
-- INVENTORY_VIEWER 都没有任何 *:delete 权限。

INSERT INTO auth_permission (
    application_code, permission_code, permission_name, permission_type,
    parent_id, path, component, sort_order, status, deleted
) VALUES
    ('WMS', 'warehouse:delete', '仓库删除', 'API', NULL, NULL, NULL, 116, 1, 0)
ON DUPLICATE KEY UPDATE permission_code = permission_code;

INSERT IGNORE INTO auth_role_permission (role_id, permission_id, application_code)
SELECT r.id, p.id, r.application_code
FROM auth_role r
JOIN auth_permission p ON p.application_code = r.application_code
WHERE r.application_code = 'WMS'
  AND r.role_code IN ('ADMIN', 'DEVELOPER')
  AND p.permission_code = 'warehouse:delete';
