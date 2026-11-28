-- Phiên đăng nhập và khóa tạm khi đăng nhập sai (PR 2 của phương án S1, 27/11/2026)
-- token_version: tăng khi đăng xuất, đổi hoặc đặt lại mật khẩu; token mang phiên bản cũ hết hiệu lực
-- failed_login_count, locked_until: đếm lần nhập sai mật khẩu liên tiếp và hạn khóa tạm
ALTER TABLE app_user
    ADD COLUMN token_version      int NOT NULL DEFAULT 0,
    ADD COLUMN failed_login_count int NOT NULL DEFAULT 0,
    ADD COLUMN locked_until       timestamptz,
    ADD CONSTRAINT ck_app_user_token_version CHECK (token_version >= 0),
    ADD CONSTRAINT ck_app_user_failed_login_count CHECK (failed_login_count >= 0);

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('LOGIN_MAX_FAILED_ATTEMPTS', '5', 'COUNT', 'SECURITY', 'Số lần nhập sai mật khẩu liên tiếp trước khi khóa tạm', 3, 20),
       ('LOGIN_LOCK_MINUTES', '15', 'MINUTE', 'SECURITY', 'Thời gian khóa tạm sau khi nhập sai quá số lần', 1, 1440);
