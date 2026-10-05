ALTER TABLE app_notifications
    ADD COLUMN project_record_id BIGINT NULL AFTER lead_id,
    ADD COLUMN reference_key VARCHAR(180) NULL AFTER notification_type,
    ADD CONSTRAINT fk_app_notification_project_record
        FOREIGN KEY (project_record_id) REFERENCES business_records(id),
    ADD UNIQUE INDEX uk_app_notification_reference (recipient_user_id, reference_key),
    ADD INDEX idx_app_notification_project_record (project_record_id);
