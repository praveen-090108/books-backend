CREATE TABLE lead_pipelines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(140) NOT NULL, description TEXT, owner_id VARCHAR(120) NOT NULL,
    currency_code VARCHAR(8) NOT NULL DEFAULT 'INR', expected_close_date DATE,
    pipeline_type VARCHAR(24) NOT NULL DEFAULT 'SALES', visibility VARCHAR(24) NOT NULL DEFAULT 'ALL_USERS',
    default_probability INT NOT NULL DEFAULT 10, color VARCHAR(16) NOT NULL DEFAULT '#ef1f2c',
    allow_duplicate_stages BOOLEAN NOT NULL DEFAULT FALSE, auto_probability BOOLEAN NOT NULL DEFAULT TRUE,
    require_stage_age BOOLEAN NOT NULL DEFAULT FALSE, require_lost_reason BOOLEAN NOT NULL DEFAULT TRUE,
    allow_closed_edit BOOLEAN NOT NULL DEFAULT FALSE, active BOOLEAN NOT NULL DEFAULT TRUE,
    archived BOOLEAN NOT NULL DEFAULT FALSE, deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(120) NOT NULL, updated_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lead_pipeline_name UNIQUE (organization_id, name),
    INDEX idx_lead_pipeline_access (organization_id, owner_id, active, deleted)
);

CREATE TABLE pipeline_stages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, pipeline_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL, probability INT NOT NULL DEFAULT 0,
    expected_duration_days INT NOT NULL DEFAULT 0, color VARCHAR(16) NOT NULL DEFAULT '#2563eb',
    stage_type VARCHAR(16) NOT NULL DEFAULT 'OPEN', sort_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_pipeline_stage_pipeline FOREIGN KEY (pipeline_id) REFERENCES lead_pipelines(id),
    CONSTRAINT uk_pipeline_stage_name UNIQUE (pipeline_id, name),
    INDEX idx_pipeline_stage_order (pipeline_id, active, sort_order)
);

CREATE TABLE lead_companies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1,
    company_name VARCHAR(180) NOT NULL, website VARCHAR(255), industry VARCHAR(100), company_type VARCHAR(80),
    email VARCHAR(180), phone VARCHAR(40), other_phone VARCHAR(40), fax VARCHAR(40), gstin VARCHAR(32), pan VARCHAR(20),
    currency_code VARCHAR(8) NOT NULL DEFAULT 'INR', ownership VARCHAR(80), employee_range VARCHAR(60),
    annual_revenue DECIMAL(18,2), lead_source VARCHAR(80), status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', description TEXT,
    primary_contact_id BIGINT, street_address VARCHAR(255), address_line2 VARCHAR(255), city VARCHAR(100), state VARCHAR(100),
    country VARCHAR(100), postal_code VARCHAR(24), linkedin_url VARCHAR(500), facebook_url VARCHAR(500), twitter_url VARCHAR(500),
    tags VARCHAR(1000), notes TEXT, owner_id VARCHAR(120) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by VARCHAR(120) NOT NULL, updated_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lead_company_name UNIQUE (organization_id, company_name),
    INDEX idx_lead_company_search (organization_id, status, owner_id, deleted), INDEX idx_lead_company_city (organization_id, country, city)
);

CREATE TABLE lead_contacts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1,
    first_name VARCHAR(100) NOT NULL, last_name VARCHAR(100) NOT NULL, job_title VARCHAR(120), email VARCHAR(180) NOT NULL,
    phone VARCHAR(40) NOT NULL, other_phone VARCHAR(40), department VARCHAR(100), date_of_birth DATE, assistant_name VARCHAR(160),
    reports_to_id BIGINT, owner_id VARCHAR(120) NOT NULL, lead_source VARCHAR(80), contact_type VARCHAR(24) NOT NULL DEFAULT 'OTHER',
    company_id BIGINT, tags VARCHAR(1000), street_address VARCHAR(255), address_line2 VARCHAR(255), city VARCHAR(100), state VARCHAR(100),
    country VARCHAR(100), postal_code VARCHAR(24), skype_id VARCHAR(120), linkedin_url VARCHAR(500), twitter_url VARCHAR(500),
    description TEXT, notes TEXT, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by VARCHAR(120) NOT NULL, updated_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lead_contact_email UNIQUE (organization_id, email),
    CONSTRAINT fk_lead_contact_company FOREIGN KEY (company_id) REFERENCES lead_companies(id),
    CONSTRAINT fk_lead_contact_reports_to FOREIGN KEY (reports_to_id) REFERENCES lead_contacts(id),
    INDEX idx_lead_contact_search (organization_id, owner_id, status, deleted), INDEX idx_lead_contact_company (company_id, deleted)
);

ALTER TABLE lead_companies ADD CONSTRAINT fk_lead_company_primary_contact FOREIGN KEY (primary_contact_id) REFERENCES lead_contacts(id);

CREATE TABLE leads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1,
    lead_number VARCHAR(48) NOT NULL, lead_name VARCHAR(180) NOT NULL, lead_type VARCHAR(24) NOT NULL,
    pipeline_id BIGINT NOT NULL, stage_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'NEW', source VARCHAR(80) NOT NULL,
    owner_id VARCHAR(120) NOT NULL, expected_value DECIMAL(18,2) NOT NULL DEFAULT 0, expected_close_date DATE,
    probability INT NOT NULL DEFAULT 0, lead_score INT NOT NULL DEFAULT 0, next_step VARCHAR(255), priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    description TEXT, company_id BIGINT, primary_contact_id BIGINT, type_details_json LONGTEXT, tags VARCHAR(1000),
    converted_deal_id BIGINT, converted_at DATETIME(6), lost_reason VARCHAR(255), draft BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE, deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(120) NOT NULL, updated_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lead_number UNIQUE (organization_id, lead_number),
    CONSTRAINT fk_lead_pipeline FOREIGN KEY (pipeline_id) REFERENCES lead_pipelines(id), CONSTRAINT fk_lead_stage FOREIGN KEY (stage_id) REFERENCES pipeline_stages(id),
    CONSTRAINT fk_lead_company FOREIGN KEY (company_id) REFERENCES lead_companies(id), CONSTRAINT fk_lead_primary_contact FOREIGN KEY (primary_contact_id) REFERENCES lead_contacts(id),
    INDEX idx_lead_access (organization_id, owner_id, status, deleted), INDEX idx_lead_pipeline_stage (pipeline_id, stage_id, deleted),
    INDEX idx_lead_created (organization_id, created_at), INDEX idx_lead_company_contact (company_id, primary_contact_id)
);

CREATE TABLE lead_contact_mapping (
    lead_id BIGINT NOT NULL, contact_id BIGINT NOT NULL, primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY (lead_id, contact_id),
    CONSTRAINT fk_lcm_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE,
    CONSTRAINT fk_lcm_contact FOREIGN KEY (contact_id) REFERENCES lead_contacts(id)
);

CREATE TABLE company_contact_mapping (
    company_id BIGINT NOT NULL, contact_id BIGINT NOT NULL, primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY (company_id, contact_id),
    CONSTRAINT fk_ccm_company FOREIGN KEY (company_id) REFERENCES lead_companies(id) ON DELETE CASCADE,
    CONSTRAINT fk_ccm_contact FOREIGN KEY (contact_id) REFERENCES lead_contacts(id)
);

CREATE TABLE lead_deals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1, lead_id BIGINT NOT NULL,
    deal_name VARCHAR(180) NOT NULL, pipeline_id BIGINT NOT NULL, stage_id BIGINT NOT NULL, amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    probability INT NOT NULL DEFAULT 0, expected_close_date DATE, owner_id VARCHAR(120) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    active BOOLEAN NOT NULL DEFAULT TRUE, deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by VARCHAR(120) NOT NULL, updated_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_deal_lead FOREIGN KEY (lead_id) REFERENCES leads(id), CONSTRAINT fk_deal_pipeline FOREIGN KEY (pipeline_id) REFERENCES lead_pipelines(id),
    CONSTRAINT fk_deal_stage FOREIGN KEY (stage_id) REFERENCES pipeline_stages(id), INDEX idx_deal_access (organization_id, owner_id, status, deleted)
);
ALTER TABLE leads ADD CONSTRAINT fk_lead_converted_deal FOREIGN KEY (converted_deal_id) REFERENCES lead_deals(id);

CREATE TABLE lead_stage_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, lead_id BIGINT NOT NULL, from_stage_id BIGINT, to_stage_id BIGINT NOT NULL,
    previous_probability INT, new_probability INT, reason VARCHAR(500), changed_by VARCHAR(120) NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_lsh_lead FOREIGN KEY (lead_id) REFERENCES leads(id), CONSTRAINT fk_lsh_from FOREIGN KEY (from_stage_id) REFERENCES pipeline_stages(id),
    CONSTRAINT fk_lsh_to FOREIGN KEY (to_stage_id) REFERENCES pipeline_stages(id), INDEX idx_lsh_lead (lead_id, changed_at)
);

CREATE TABLE lead_activities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1, entity_type VARCHAR(24) NOT NULL,
    entity_id BIGINT NOT NULL, activity_type VARCHAR(24) NOT NULL, title VARCHAR(180) NOT NULL, description TEXT,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), created_by VARCHAR(120) NOT NULL,
    INDEX idx_lead_activity_entity (organization_id, entity_type, entity_id, occurred_at)
);
CREATE TABLE lead_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1, entity_type VARCHAR(24) NOT NULL, entity_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL, description TEXT, due_date DATETIME(6), priority VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    assigned_user VARCHAR(120), status VARCHAR(20) NOT NULL DEFAULT 'OPEN', reminder_at DATETIME(6), created_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_lead_task_entity (organization_id, entity_type, entity_id, status, due_date)
);
CREATE TABLE lead_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1, entity_type VARCHAR(24) NOT NULL, entity_id BIGINT NOT NULL,
    content TEXT NOT NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by VARCHAR(120) NOT NULL, updated_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_lead_note_entity (organization_id, entity_type, entity_id, deleted, created_at)
);
CREATE TABLE lead_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1, entity_type VARCHAR(24) NOT NULL, entity_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL, file_url VARCHAR(1000) NOT NULL, content_type VARCHAR(120), file_size BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by VARCHAR(120) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_lead_attachment_entity (organization_id, entity_type, entity_id, deleted)
);
CREATE TABLE lead_tags (id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1, name VARCHAR(80) NOT NULL, color VARCHAR(16) NOT NULL DEFAULT '#2563eb', CONSTRAINT uk_lead_tag UNIQUE (organization_id, name));
CREATE TABLE lead_user_share (lead_id BIGINT NOT NULL, user_id VARCHAR(120) NOT NULL, shared_by VARCHAR(120) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY (lead_id, user_id), CONSTRAINT fk_lead_share FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE);
CREATE TABLE pipeline_user_visibility (pipeline_id BIGINT NOT NULL, user_id VARCHAR(120) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY (pipeline_id, user_id), CONSTRAINT fk_pipeline_visibility FOREIGN KEY (pipeline_id) REFERENCES lead_pipelines(id) ON DELETE CASCADE);
CREATE TABLE lost_reasons (id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1, pipeline_id BIGINT, reason VARCHAR(180) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE, CONSTRAINT fk_lost_reason_pipeline FOREIGN KEY (pipeline_id) REFERENCES lead_pipelines(id), INDEX idx_lost_reason (organization_id, pipeline_id, active));
CREATE TABLE lead_audit_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, organization_id BIGINT NOT NULL DEFAULT 1, entity_type VARCHAR(24) NOT NULL, entity_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL, old_values_json LONGTEXT, new_values_json LONGTEXT, performed_by VARCHAR(120) NOT NULL, performed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), INDEX idx_lead_audit_entity (organization_id, entity_type, entity_id, performed_at));

INSERT INTO lead_pipelines (organization_id,name,description,owner_id,currency_code,pipeline_type,visibility,default_probability,color,created_by,updated_by)
VALUES (1,'Fixed Cost Pipeline','Project-based sales opportunities.','admin','INR','SALES','ALL_USERS',10,'#ef1f2c','admin','admin'),
       (1,'Staffing Pipeline','Resource and staffing opportunities.','admin','INR','SALES','ALL_USERS',10,'#2563eb','admin','admin');
INSERT INTO pipeline_stages (pipeline_id,name,probability,expected_duration_days,color,stage_type,sort_order)
SELECT id,'New',10,3,'#2563eb','OPEN',1 FROM lead_pipelines;
INSERT INTO pipeline_stages (pipeline_id,name,probability,expected_duration_days,color,stage_type,sort_order)
SELECT id,'Contacted',20,5,'#0891b2','OPEN',2 FROM lead_pipelines;
INSERT INTO pipeline_stages (pipeline_id,name,probability,expected_duration_days,color,stage_type,sort_order)
SELECT id,'Qualified',40,7,'#16a34a','OPEN',3 FROM lead_pipelines;
INSERT INTO pipeline_stages (pipeline_id,name,probability,expected_duration_days,color,stage_type,sort_order)
SELECT id,'Proposal',60,10,'#f59e0b','OPEN',4 FROM lead_pipelines;
INSERT INTO pipeline_stages (pipeline_id,name,probability,expected_duration_days,color,stage_type,sort_order)
SELECT id,'Negotiation',80,12,'#f97316','OPEN',5 FROM lead_pipelines;
INSERT INTO pipeline_stages (pipeline_id,name,probability,expected_duration_days,color,stage_type,sort_order)
SELECT id,'Won',100,0,'#16a34a','WON',6 FROM lead_pipelines;
INSERT INTO pipeline_stages (pipeline_id,name,probability,expected_duration_days,color,stage_type,sort_order)
SELECT id,'Lost',0,0,'#ef4444','LOST',7 FROM lead_pipelines;
