ALTER TABLE app_users
    ADD COLUMN platform_admin BOOLEAN NOT NULL DEFAULT FALSE AFTER account_status;

CREATE INDEX idx_app_users_platform_admin ON app_users(platform_admin, account_status);
