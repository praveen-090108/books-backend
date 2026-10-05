ALTER TABLE lead_activities
    ADD COLUMN vendor_profile BOOLEAN NOT NULL DEFAULT FALSE AFTER description,
    ADD COLUMN vendor_id BIGINT NULL AFTER vendor_profile,
    ADD CONSTRAINT fk_lead_activity_vendor
        FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE RESTRICT,
    ADD INDEX idx_lead_activity_vendor (vendor_id);
