ALTER TABLE business_records
    ADD COLUMN employment_status VARCHAR(24) NULL AFTER status,
    ADD COLUMN resignation_date DATE NULL AFTER closed_date,
    ADD COLUMN notice_start_date DATE NULL AFTER resignation_date,
    ADD COLUMN last_working_day DATE NULL AFTER notice_start_date,
    ADD COLUMN resignation_reason VARCHAR(500) NULL AFTER last_working_day,
    ADD COLUMN employment_remarks VARCHAR(1000) NULL AFTER resignation_reason;

UPDATE business_records
SET employment_status = CASE WHEN LOWER(status) = 'inactive' THEN 'INACTIVE' ELSE 'ACTIVE' END
WHERE module = 'resources' AND type = 'resources' AND employment_status IS NULL;

CREATE TABLE resource_salary_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    previous_salary DECIMAL(14,2) NOT NULL,
    revised_salary DECIMAL(14,2) NOT NULL,
    salary_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    effective_date DATE NOT NULL,
    revision_amount DECIMAL(14,2) NOT NULL,
    revision_percentage DECIMAL(9,4) NOT NULL,
    revision_reason VARCHAR(200) NOT NULL,
    remarks VARCHAR(1000) NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_salary_history_resource FOREIGN KEY (resource_id) REFERENCES business_records(id),
    CONSTRAINT fk_salary_history_user FOREIGN KEY (updated_by) REFERENCES app_users(id),
    CONSTRAINT uk_salary_history_resource_date UNIQUE (resource_id, effective_date),
    INDEX idx_salary_history_effective (resource_id, effective_date DESC)
);

INSERT INTO resource_salary_history
    (resource_id, previous_salary, revised_salary, salary_currency, effective_date,
     revision_amount, revision_percentage, revision_reason, remarks, updated_by)
SELECT id,
       CAST(JSON_UNQUOTE(JSON_EXTRACT(notes, '$.monthlySalary')) AS DECIMAL(14,2)),
       CAST(JSON_UNQUOTE(JSON_EXTRACT(notes, '$.monthlySalary')) AS DECIMAL(14,2)),
       COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(notes, '$.salaryCurrency')), 'null'), 'INR'),
       record_date, 0, 0, 'INITIAL_MIGRATION',
       'Initial known salary migrated from the resource master.', updated_by
FROM business_records
WHERE module = 'resources' AND type = 'resources' AND JSON_VALID(notes)
  AND CAST(JSON_UNQUOTE(JSON_EXTRACT(notes, '$.monthlySalary')) AS DECIMAL(14,2)) > 0;

CREATE TABLE resource_employment_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    employment_status VARCHAR(24) NOT NULL,
    resignation_date DATE NULL,
    notice_start_date DATE NULL,
    last_working_day DATE NULL,
    reason VARCHAR(500) NULL,
    remarks VARCHAR(1000) NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_employment_history_resource FOREIGN KEY (resource_id) REFERENCES business_records(id),
    CONSTRAINT fk_employment_history_user FOREIGN KEY (updated_by) REFERENCES app_users(id),
    INDEX idx_employment_history_resource (resource_id, updated_at DESC)
);

INSERT INTO resource_employment_history(resource_id, employment_status, reason, remarks, updated_by)
SELECT id, employment_status, 'INITIAL_MIGRATION', 'Initial employment state migrated from the resource master.', updated_by
FROM business_records WHERE module = 'resources' AND type = 'resources';

CREATE TABLE project_resource_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    project_type VARCHAR(20) NOT NULL,
    role_designation VARCHAR(160) NULL,
    allocation_start_date DATE NOT NULL,
    planned_release_date DATE NULL,
    release_notification_date DATE NULL,
    actual_release_date DATE NULL,
    last_billing_date DATE NULL,
    allocation_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    allocation_percentage DECIMAL(5,2) NOT NULL DEFAULT 100,
    monthly_billing DECIMAL(14,2) NOT NULL DEFAULT 0,
    billing_currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    billable BOOLEAN NOT NULL DEFAULT TRUE,
    release_confirmation_status VARCHAR(20) NULL,
    release_reason VARCHAR(500) NULL,
    remarks VARCHAR(1000) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_project_allocation_resource FOREIGN KEY (resource_id) REFERENCES business_records(id),
    CONSTRAINT fk_project_allocation_project FOREIGN KEY (project_id) REFERENCES business_records(id),
    CONSTRAINT fk_project_allocation_created_by FOREIGN KEY (created_by) REFERENCES app_users(id),
    CONSTRAINT fk_project_allocation_updated_by FOREIGN KEY (updated_by) REFERENCES app_users(id),
    INDEX idx_project_allocation_project_status (project_id, allocation_status, billable),
    INDEX idx_project_allocation_resource_history (resource_id, allocation_start_date, actual_release_date),
    INDEX idx_project_allocation_release (planned_release_date, release_confirmation_status)
);

INSERT INTO project_resource_allocation
    (resource_id, project_id, project_type, role_designation, allocation_start_date,
     actual_release_date, allocation_status, allocation_percentage, billable,
     release_reason, remarks, created_by, updated_by, created_at, updated_at)
SELECT t.employee_id, t.project_id, 'FIXED_COST', COALESCE(t.role_designation, employee.category), t.added_date,
       t.removed_date, CASE WHEN t.assignment_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'RELEASED' END,
       100, FALSE, t.removal_note, t.notes, t.created_by, t.removed_by, t.created_at, t.updated_at
FROM fixed_cost_project_team_member t
JOIN business_records employee ON employee.id = t.employee_id;

INSERT INTO project_resource_allocation
    (resource_id, project_id, project_type, role_designation, allocation_start_date,
     actual_release_date, last_billing_date, allocation_status, allocation_percentage,
     monthly_billing, billing_currency, billable, release_reason, remarks, created_by, updated_by)
SELECT resource.id, project.id, 'STAFFING', resource.category, project.record_date,
       project.closed_date, project.closed_date,
       CASE WHEN LOWER(project.status) IN ('close','closed') THEN 'CLOSED' ELSE 'ACTIVE' END,
       100, project.amount, LEFT(COALESCE(project.payment_mode, 'INR'), 3), TRUE,
       CASE WHEN project.closed_date IS NOT NULL THEN 'Migrated closed staffing assignment' ELSE NULL END,
       'Migrated from staffing project assignment', project.created_by, project.updated_by
FROM business_records project
JOIN business_records resource
  ON resource.module = 'resources' AND resource.type = 'resources'
 AND LOWER(resource.party_name) = LOWER(COALESCE(
       NULLIF(JSON_UNQUOTE(JSON_EXTRACT(project.notes, '$.workingResource')), ''),
       NULLIF(JSON_UNQUOTE(JSON_EXTRACT(project.notes, '$.selectedResource')), ''), project.owner_name))
WHERE project.module = 'projects' AND project.type = 'staffing' AND JSON_VALID(project.notes);

CREATE TABLE project_release_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    allocation_id BIGINT NOT NULL,
    previous_release_date DATE NULL,
    new_release_date DATE NULL,
    previous_confirmation_status VARCHAR(20) NULL,
    new_confirmation_status VARCHAR(20) NULL,
    reason VARCHAR(500) NULL,
    remarks VARCHAR(1000) NULL,
    changed_by BIGINT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_release_history_allocation FOREIGN KEY (allocation_id) REFERENCES project_resource_allocation(id),
    CONSTRAINT fk_release_history_user FOREIGN KEY (changed_by) REFERENCES app_users(id),
    INDEX idx_release_history_allocation (allocation_id, changed_at DESC)
);
