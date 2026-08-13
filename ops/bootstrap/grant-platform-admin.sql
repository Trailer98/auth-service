-- ============================================================================
-- grant-platform-admin.sql —— 首次(或后续)将 PLATFORM_ADMIN 授予某个具体账号
-- ============================================================================
--
-- 这是一次性的、需要人对人负责的授权决策，不是环境搭建的一部分，因此不通过 Flyway 迁移执行、
-- 不进 Flyway 版本历史（Flyway 迁移只负责"环境里有没有这个角色、这个角色有没有对应权限"这种
-- 确定性的、可重复执行的基础设施定义；"这一次具体把这个角色给了谁"是操作性决策，不应该以
-- "看起来像是环境搭建一部分"的方式悄悄混进迁移文件）。
--
-- 【审计要求 —— 强制前置产出，执行本脚本前必须已经完成，不能省略】
-- auth_user_role 表没有 granted_by / reason / created_by 之类的列，无法回答"谁批准的、为什么"。
-- 这次授权必须在数据库之外留痕：在变更工单/运维记录里写明
--   "于 <日期> 由 <批准人> 将 PLATFORM_ADMIN 授予 userId=<x>（username=<y>），理由：<原因>"
-- 这条记录本身不是本脚本的产物，但是本操作的强制前置条件。
--
-- 【用法】
-- 1. 把下面 CALL 语句里的 'REPLACE_ME_BEFORE_RUNNING' 改成目标账号的真实 username。
-- 2. 用具备生产库写权限的账号连接目标数据库，完整执行本文件（不要只选中中间几行执行——
--    存储过程定义和调用必须作为一个整体跑完）。
-- 3. 脚本会先打印"授权前状态"，执行授权，再打印"授权后状态"，供当场核对。
-- 4. 可重复执行：目标账号已经持有 PLATFORM_ADMIN 时重复执行不会报错、不会产生重复行、
--    不会有任何副作用（INSERT IGNORE + 唯一键 uk_auth_user_role_user_application_role）。
--
-- 【前置校验，任意一项不满足会立即中止（SIGNAL），不会插入任何数据】
--   - 目标用户必须存在（auth_user.username 命中）
--   - 目标用户未被软删除（deleted=0）且处于启用状态（status=1）
--   - PLATFORM 应用必须已存在、未被软删除、处于启用状态（即 V6 迁移已执行）
--   - PLATFORM_ADMIN 角色必须已存在、未被软删除、处于启用状态
-- 这些校验故意不会"自动修复"（比如自动启用一个被禁用的用户）——发现异常就中止，交给人工判断。

DELIMITER $$

DROP PROCEDURE IF EXISTS grant_platform_admin$$

CREATE PROCEDURE grant_platform_admin(IN p_username VARCHAR(64))
BEGIN
    DECLARE v_user_id BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_user_status TINYINT DEFAULT NULL;
    DECLARE v_user_deleted TINYINT DEFAULT NULL;
    DECLARE v_app_status TINYINT DEFAULT NULL;
    DECLARE v_app_deleted TINYINT DEFAULT NULL;
    DECLARE v_role_id BIGINT UNSIGNED DEFAULT NULL;
    DECLARE v_role_status TINYINT DEFAULT NULL;
    DECLARE v_role_deleted TINYINT DEFAULT NULL;

    SELECT id, status, deleted INTO v_user_id, v_user_status, v_user_deleted
    FROM auth_user WHERE username = p_username LIMIT 1;

    IF v_user_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: target user not found';
    END IF;
    IF v_user_deleted = 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: target user is soft-deleted';
    END IF;
    IF v_user_status <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: target user is disabled (status != 1)';
    END IF;

    SELECT status, deleted INTO v_app_status, v_app_deleted
    FROM auth_application WHERE application_code = 'PLATFORM' LIMIT 1;

    IF v_app_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: PLATFORM application does not exist - run V6 migration first';
    END IF;
    IF v_app_deleted = 1 OR v_app_status <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: PLATFORM application is disabled or soft-deleted';
    END IF;

    SELECT id, status, deleted INTO v_role_id, v_role_status, v_role_deleted
    FROM auth_role WHERE application_code = 'PLATFORM' AND role_code = 'PLATFORM_ADMIN' LIMIT 1;

    IF v_role_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: PLATFORM_ADMIN role does not exist - run V6 migration first';
    END IF;
    IF v_role_deleted = 1 OR v_role_status <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ABORT: PLATFORM_ADMIN role is disabled or soft-deleted';
    END IF;

    INSERT IGNORE INTO auth_user_role (user_id, application_code, role_id)
    VALUES (v_user_id, 'PLATFORM', v_role_id);
END$$

DELIMITER ;

-- ============================================================================
-- 执行区：授权前状态 → 调用存储过程 → 授权后状态 → 清理存储过程
-- ============================================================================

SELECT '=== 授权前：当前持有 PLATFORM_ADMIN 的账号 ===' AS marker;
SELECT ur.user_id, u.username, u.status AS user_status, ur.created_at AS granted_at
FROM auth_user_role ur
JOIN auth_user u ON u.id = ur.user_id
JOIN auth_role r ON r.id = ur.role_id
WHERE ur.application_code = 'PLATFORM' AND r.role_code = 'PLATFORM_ADMIN'
ORDER BY ur.created_at;

CALL grant_platform_admin('REPLACE_ME_BEFORE_RUNNING');

SELECT '=== 授权后：当前持有 PLATFORM_ADMIN 的账号 ===' AS marker;
SELECT ur.user_id, u.username, u.status AS user_status, ur.created_at AS granted_at
FROM auth_user_role ur
JOIN auth_user u ON u.id = ur.user_id
JOIN auth_role r ON r.id = ur.role_id
WHERE ur.application_code = 'PLATFORM' AND r.role_code = 'PLATFORM_ADMIN'
ORDER BY ur.created_at;

DROP PROCEDURE IF EXISTS grant_platform_admin;
