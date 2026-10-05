ALTER TABLE project_documents
    ADD COLUMN document_type VARCHAR(40) NOT NULL DEFAULT 'PROJECT' AFTER project_type,
    ADD COLUMN sow_version INT NULL AFTER document_type;

CREATE TABLE staffing_project_sow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    staffing_project_id BIGINT NOT NULL,
    sow_version INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    document_id BIGINT NULL,
    notes VARCHAR(2000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    current_sow BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_staffing_sow_project_version (staffing_project_id, sow_version),
    KEY idx_staffing_sow_current (staffing_project_id, current_sow),
    KEY idx_staffing_sow_end_date (end_date, current_sow),
    CONSTRAINT fk_staffing_sow_project FOREIGN KEY (staffing_project_id) REFERENCES business_records(id) ON DELETE CASCADE,
    CONSTRAINT fk_staffing_sow_document FOREIGN KEY (document_id) REFERENCES project_documents(id) ON DELETE RESTRICT,
    CONSTRAINT chk_staffing_sow_dates CHECK (end_date >= start_date)
);

-- Safely bootstrap one historical/current SOW for existing staffing projects.
-- The latest existing project attachment is reused as the signed SOW where one
-- exists; no physical file is copied.
INSERT INTO staffing_project_sow
    (staffing_project_id, sow_version, start_date, end_date, document_id, notes, status, current_sow, created_by, updated_by, created_at, updated_at)
SELECT project.id, 1, project.record_date, project.due_date,
       (SELECT document.id FROM project_documents document
        WHERE document.project_id=project.id AND document.project_type='staffing' AND document.active=TRUE
        ORDER BY document.uploaded_at DESC, document.id DESC LIMIT 1),
       'Migrated from existing Staffing Project dates', 'ACTIVE', TRUE,
       project.created_by, project.updated_by, project.created_at, project.updated_at
FROM business_records project
WHERE project.module='projects' AND project.type='staffing'
  AND project.record_date IS NOT NULL AND project.due_date IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM staffing_project_sow sow WHERE sow.staffing_project_id=project.id);

UPDATE project_documents document
JOIN staffing_project_sow sow ON sow.document_id=document.id
SET document.document_type='SOW', document.sow_version=sow.sow_version;
