-- ============================================================================
-- revoke-platform-admin.sql —— 撤销某个具体账号的 PLATFORM_ADMIN
-- ============================================================================
--
-- 与 grant-platform-admin.sql 对称的回滚脚本，同样不是环境搭建的一部分，不进 Flyway 版本历史。
--
-- 【审计要求 —— 强制前置产出，执行本脚本前必须已经完成，不能省略】
-- 与授权同理：在变更工单/运维记录里写明
--   "于 <日期> 由 <批准人> 撤销 userId=<x>（username=<y>）的 PLATFORM_ADMIN，理由：<原因>"
--
-- 【用法】
-- 1. 把下面 CALL 语句里的 'REPLACE_ME_BEFORE_RUNNING' 改成目标账号的真实 username。
-- 2. 完整执行本文件。
-- 3. 脚本会先打印"撤销前状态"，执行撤销，再打印"撤销后状态"。
-- 4. 可重复执行：目标账号本来就没有 PLATFORM_ADMIN 时重复执行不会报错（DELETE 影响 0 行），
--    不是错误状态。
--
-- 【前置校验，任意一项不满足会立即中止（SIGNAL）】
--   - 目标用户必须存在（不要求处于启用状态——撤销一个已经被禁用账号的残留角色绑定应该被允许）
--   - PLATFORM_ADMIN 角色必须存在
-- 撤销操作本身不会自动清理 Redis 权限上下文缓存（该用户在缓存过期前，短暂时间内可能仍然
-- 表现为持有旧权限）——这是运行时状态清理，属于 Batch 6A 的"清理明确列出的测试用户 Redis
-- 权限缓存"同一类操作，需要在执行本脚本后单独、显式地确认是否需要处理，不在本脚本范围内。

DELIMITER $$

DROP PROCEDURE IF EXISTS revoke_platform_admin$$

CREATE PROCEDURE revoke_platform_admin(IN p_username VARCHAR(64))
BEGIN
    DECLARE v_user_id BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_role_id BIGINT UNSIGNED DEFAULT NULL;

    SELECT id INTO v_user_id FROM auth_user WHERE username = p_username LIMIT 1;
    IF v_user_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: target user not found';
    END IF;

    SELECT id INTO v_role_id
    FROM auth_role WHERE application_code = 'PLATFORM' AND role_code = 'PLATFORM_ADMIN' LIMIT 1;
    IF v_role_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: PLATFORM_ADMIN role does not exist';
    END IF;

    DELETE FROM auth_user_role
    WHERE user_id = v_user_id AND application_code = 'PLATFORM' AND role_id = v_role_id;
END$$

DELIMITER ;

-- ============================================================================
-- 执行区：撤销前状态 → 调用存储过程 → 撤销后状态 → 清理存储过程
-- ============================================================================

SELECT '=== 撤销前：当前持有 PLATFORM_ADMIN 的账号 ===' AS marker;
SELECT ur.user_id, u.username, u.status AS user_status, ur.created_at AS granted_at
FROM auth_user_role ur
JOIN auth_user u ON u.id = ur.user_id
JOIN auth_role r ON r.id = ur.role_id
WHERE ur.application_code = 'PLATFORM' AND r.role_code = 'PLATFORM_ADMIN'
ORDER BY ur.created_at;

CALL revoke_platform_admin('REPLACE_ME_BEFORE_RUNNING');

SELECT '=== 撤销后：当前持有 PLATFORM_ADMIN 的账号 ===' AS marker;
SELECT ur.user_id, u.username, u.status AS user_status, ur.created_at AS granted_at
FROM auth_user_role ur
JOIN auth_user u ON u.id = ur.user_id
JOIN auth_role r ON r.id = ur.role_id
WHERE ur.application_code = 'PLATFORM' AND r.role_code = 'PLATFORM_ADMIN'
ORDER BY ur.created_at;

DROP PROCEDURE IF EXISTS revoke_platform_admin;
