CREATE TABLE invoice_project_links (
    invoice_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    project_type VARCHAR(32) NOT NULL,
    created_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (invoice_id),
    CONSTRAINT fk_invoice_project_link_invoice FOREIGN KEY (invoice_id) REFERENCES business_records(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_project_link_project FOREIGN KEY (project_id) REFERENCES business_records(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_project_link_created_by FOREIGN KEY (created_by) REFERENCES app_users(id),
    INDEX idx_invoice_project_link_project (project_id, project_type)
);
