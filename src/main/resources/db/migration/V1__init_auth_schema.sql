CREATE TABLE auth_application (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_code VARCHAR(64) NOT NULL,
    application_name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_application_code (application_code),
    KEY idx_auth_application_status_deleted (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(128) NULL,
    email VARCHAR(128) NULL,
    phone VARCHAR(32) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    last_login_time DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_user_username (username),
    KEY idx_auth_user_email (email),
    KEY idx_auth_user_phone (phone),
    KEY idx_auth_user_status_deleted (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_code VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_role_application_code (application_code, role_code),
    UNIQUE KEY uk_auth_role_id_application (id, application_code),
    KEY idx_auth_role_application_status (application_code, status, deleted),
    CONSTRAINT fk_auth_role_application
        FOREIGN KEY (application_code) REFERENCES auth_application (application_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_permission (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_code VARCHAR(64) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    permission_type VARCHAR(32) NOT NULL,
    parent_id BIGINT UNSIGNED NULL,
    path VARCHAR(255) NULL,
    component VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_permission_application_code (application_code, permission_code),
    UNIQUE KEY uk_auth_permission_id_application (id, application_code),
    KEY idx_auth_permission_parent_sort (application_code, parent_id, sort_order),
    KEY idx_auth_permission_parent_application (parent_id, application_code),
    KEY idx_auth_permission_type_status (application_code, permission_type, status, deleted),
    CONSTRAINT fk_auth_permission_application
        FOREIGN KEY (application_code) REFERENCES auth_application (application_code),
    CONSTRAINT fk_auth_permission_parent
        FOREIGN KEY (parent_id, application_code) REFERENCES auth_permission (id, application_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_user_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    application_code VARCHAR(64) NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_user_role_user_application_role (user_id, application_code, role_id),
    KEY idx_auth_user_role_application_role (application_code, role_id),
    KEY idx_auth_user_role_role_application (role_id, application_code),
    CONSTRAINT fk_auth_user_role_user
        FOREIGN KEY (user_id) REFERENCES auth_user (id),
    CONSTRAINT fk_auth_user_role_application
        FOREIGN KEY (application_code) REFERENCES auth_application (application_code),
    CONSTRAINT fk_auth_user_role_role_application
        FOREIGN KEY (role_id, application_code) REFERENCES auth_role (id, application_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_role_permission (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    application_code VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_role_permission_role_permission (application_code, role_id, permission_id),
    KEY idx_auth_role_permission_permission (application_code, permission_id),
    KEY idx_auth_role_permission_role_application (role_id, application_code),
    KEY idx_auth_role_permission_permission_application (permission_id, application_code),
    CONSTRAINT fk_auth_role_permission_application
        FOREIGN KEY (application_code) REFERENCES auth_application (application_code),
    CONSTRAINT fk_auth_role_permission_role_application
        FOREIGN KEY (role_id, application_code) REFERENCES auth_role (id, application_code),
    CONSTRAINT fk_auth_role_permission_permission_application
        FOREIGN KEY (permission_id, application_code) REFERENCES auth_permission (id, application_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_login_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    username VARCHAR(64) NOT NULL,
    login_ip VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    login_status TINYINT NOT NULL,
    fail_reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_auth_login_log_user_created (user_id, created_at),
    KEY idx_auth_login_log_username_created (username, created_at),
    KEY idx_auth_login_log_status_created (login_status, created_at),
    CONSTRAINT fk_auth_login_log_user
        FOREIGN KEY (user_id) REFERENCES auth_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE auth_token_blacklist (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_id VARCHAR(128) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    expire_time DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_token_blacklist_token_id (token_id),
    KEY idx_auth_token_blacklist_user (user_id),
    KEY idx_auth_token_blacklist_expire_time (expire_time),
    CONSTRAINT fk_auth_token_blacklist_user
        FOREIGN KEY (user_id) REFERENCES auth_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
