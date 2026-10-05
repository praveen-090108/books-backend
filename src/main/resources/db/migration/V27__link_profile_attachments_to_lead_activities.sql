ALTER TABLE lead_attachments
    ADD COLUMN activity_id BIGINT NULL AFTER entity_id,
    ADD CONSTRAINT fk_lead_attachment_activity
        FOREIGN KEY (activity_id) REFERENCES lead_activities(id) ON DELETE CASCADE,
    ADD INDEX idx_lead_attachment_activity (activity_id, deleted);
