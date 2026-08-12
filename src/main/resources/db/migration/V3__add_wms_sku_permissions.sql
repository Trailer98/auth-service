-- IMPORTANT CONTEXT (discovered 2026-08-11, not previously documented anywhere in this repo):
--
-- The live `auth` database that this project's dev environment actually points at has a much
-- richer WMS RBAC setup than V1/V2 alone would produce: 74 WMS permission codes (customer:*,
-- warehouse:*, area:*, location:*, sku:*, inbound:*, outbound:*, inventory:*, stock-adjust:*,
-- stock-count:*, exception:*, sys-dict:*, operation-log:*, user:*, role:*, permission:*, ...) and
-- six WMS roles (ADMIN, DEVELOPER, WAREHOUSE_MANAGER, WAREHOUSE_OPERATOR, INVENTORY_VIEWER, plus
-- V2's own WMS_ADMIN) with ~245 role<->permission bindings between them. None of that exists in
-- V1 or V2 — it was added directly against the live database, outside Flyway, at some point.
-- `flyway_schema_history` only lists V1 and V2 as applied.
--
-- Practical consequence: the real `admin` user is on role ADMIN (not V2's WMS_ADMIN, which has
-- zero permissions bound to it and appears to be an unused leftover). A fresh database built by
-- running only the Flyway migrations in this repo would NOT reproduce this — it would only get
-- V2's four generic wms:*:manage codes and the single WMS_ADMIN role. That gap is not fixed here;
-- doing so would mean writing a migration for all ~245 bindings across 6 roles, which is a much
-- larger task than what prompted this file (see wms-web-refactor/PROJECT_CONTEXT.md and
-- auth-service/PROJECT_CONTEXT.md for where this is flagged as a recommended follow-up).
--
-- What THIS migration actually does: `sku:view`/`sku:create`/`sku:update`/`sku:disable` already
-- exist live and are already granted to ADMIN/DEVELOPER/WAREHOUSE_MANAGER (view/create/update) —
-- only `sku:delete` was missing, because WMS's SkuController never had a delete endpoint until now.
-- Grants follow the observed live pattern exactly: delete-type permissions in this system are only
-- ever held by ADMIN and DEVELOPER (e.g. customer:delete, sys-dict:delete) — never by
-- WAREHOUSE_MANAGER/WAREHOUSE_OPERATOR/INVENTORY_VIEWER, which hold zero `:delete` permissions of
-- any kind. This migration's role_permission insert depends on those roles already existing; on a
-- database built from Flyway alone (no live-DB carryover) it will insert the permission row but
-- silently grant it to nobody, since ADMIN/DEVELOPER don't exist there either.

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
    ('WMS', 'sku:delete', 'SKU 删除', 'API', NULL, NULL, NULL, 115, 1, 0)
ON DUPLICATE KEY UPDATE permission_code = permission_code;

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
  AND r.role_code IN ('ADMIN', 'DEVELOPER')
  AND p.permission_code = 'sku:delete';
