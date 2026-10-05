ALTER TABLE app_users ADD COLUMN resource_id BIGINT NULL AFTER legacy_record_id;
ALTER TABLE app_users ADD COLUMN manager_user_id BIGINT NULL AFTER resource_id;

ALTER TABLE app_users MODIFY name VARCHAR(160) NULL;
ALTER TABLE app_users MODIFY email VARCHAR(160) NULL;

UPDATE app_users u
JOIN business_records r
  ON r.module = 'resources'
 AND r.type = 'resources'
 AND LOWER(r.party_email) = LOWER(u.email)
SET u.resource_id = r.id
WHERE u.resource_id IS NULL;

UPDATE app_users SET name = NULL, email = NULL, phone = NULL, designation = NULL WHERE resource_id IS NOT NULL;

ALTER TABLE app_users
    ADD CONSTRAINT uk_app_users_resource_id UNIQUE (resource_id),
    ADD CONSTRAINT fk_app_users_resource FOREIGN KEY (resource_id) REFERENCES business_records(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_app_users_manager FOREIGN KEY (manager_user_id) REFERENCES app_users(id) ON DELETE SET NULL;

CREATE INDEX idx_app_users_manager ON app_users(manager_user_id);

ALTER TABLE business_records ADD COLUMN created_by BIGINT NULL AFTER notes;
ALTER TABLE business_records ADD COLUMN updated_by BIGINT NULL AFTER created_by;
CREATE INDEX idx_business_records_created_by ON business_records(created_by);
CREATE INDEX idx_business_records_module_type_creator ON business_records(module, type, created_by);

ALTER TABLE business_records
    ADD CONSTRAINT fk_business_records_created_by FOREIGN KEY (created_by) REFERENCES app_users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_business_records_updated_by FOREIGN KEY (updated_by) REFERENCES app_users(id) ON DELETE SET NULL;

UPDATE business_records
SET created_by = (SELECT id FROM app_users WHERE LOWER(role_name) LIKE '%admin%' ORDER BY id LIMIT 1)
WHERE created_by IS NULL
  AND EXISTS (SELECT 1 FROM app_users WHERE LOWER(role_name) LIKE '%admin%');
