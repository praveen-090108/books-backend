INSERT IGNORE INTO skill_master (organization_id, skill_name, active, deleted)
VALUES (1, 'SAP', TRUE, FALSE),
       (1, 'Salesforce', TRUE, FALSE),
       (1, 'Flutter', TRUE, FALSE),
       (1, 'Android', TRUE, FALSE),
       (1, 'iOS', TRUE, FALSE),
       (1, 'ServiceNow', TRUE, FALSE);

ALTER TABLE leads ADD COLUMN assigned_user_id BIGINT NULL AFTER owner_id;
ALTER TABLE leads ADD INDEX idx_leads_assigned_user (assigned_user_id);
ALTER TABLE leads ADD CONSTRAINT fk_leads_assigned_user FOREIGN KEY (assigned_user_id) REFERENCES app_users(id);

CREATE TABLE lead_assignment_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lead_id BIGINT NOT NULL,
    previous_assignee_id BIGINT NULL,
    new_assignee_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT NOT NULL,
    assignment_note VARCHAR(1000) NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lead_assignment_history_lead FOREIGN KEY (lead_id) REFERENCES leads(id),
    CONSTRAINT fk_lead_assignment_history_previous_user FOREIGN KEY (previous_assignee_id) REFERENCES app_users(id),
    CONSTRAINT fk_lead_assignment_history_new_user FOREIGN KEY (new_assignee_id) REFERENCES app_users(id),
    CONSTRAINT fk_lead_assignment_history_actor FOREIGN KEY (assigned_by_user_id) REFERENCES app_users(id),
    INDEX idx_lead_assignment_history_lead (lead_id, assigned_at)
);

CREATE TABLE app_notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    notification_type VARCHAR(60) NOT NULL,
    lead_id BIGINT NULL,
    assigned_by_user_id BIGINT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    target_path VARCHAR(255) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    CONSTRAINT fk_app_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES app_users(id),
    CONSTRAINT fk_app_notification_lead FOREIGN KEY (lead_id) REFERENCES leads(id),
    CONSTRAINT fk_app_notification_actor FOREIGN KEY (assigned_by_user_id) REFERENCES app_users(id),
    INDEX idx_app_notification_recipient (recipient_user_id, is_read, created_at)
);
