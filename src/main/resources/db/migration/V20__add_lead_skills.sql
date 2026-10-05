CREATE TABLE IF NOT EXISTS skill_master (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL DEFAULT 1,
    skill_name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_skill_master_name UNIQUE (organization_id, skill_name),
    INDEX idx_skill_master_lookup (organization_id, active, deleted, skill_name)
);

INSERT IGNORE INTO skill_master (organization_id, skill_name) VALUES
    (1, 'Java'), (1, 'Spring Boot'), (1, 'ReactJS'), (1, 'JavaScript'),
    (1, 'TypeScript'), (1, 'Python'), (1, '.NET'), (1, 'PHP'),
    (1, 'Node.js'), (1, 'AWS'), (1, 'DevOps'), (1, 'QA Automation'),
    (1, 'Data Engineering'), (1, 'UI/UX Design'), (1, 'Business Analysis');

ALTER TABLE leads
    ADD COLUMN primary_skill_id BIGINT NULL AFTER lead_type,
    ADD COLUMN secondary_skill VARCHAR(500) NULL AFTER primary_skill_id,
    ADD INDEX idx_lead_primary_skill (organization_id, primary_skill_id, deleted),
    ADD CONSTRAINT fk_lead_primary_skill FOREIGN KEY (primary_skill_id) REFERENCES skill_master(id);
