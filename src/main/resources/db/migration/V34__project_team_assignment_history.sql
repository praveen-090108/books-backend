ALTER TABLE fixed_cost_project_team_member
    DROP INDEX uk_fixed_project_employee,
    ADD COLUMN role_designation VARCHAR(160) NULL AFTER employee_id,
    ADD COLUMN added_date DATE NULL AFTER role_designation,
    ADD COLUMN notes VARCHAR(1000) NULL AFTER added_date,
    ADD COLUMN assignment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER notes,
    ADD COLUMN removed_date DATE NULL AFTER assignment_status,
    ADD COLUMN removed_by BIGINT NULL AFTER removed_date,
    ADD COLUMN removal_note VARCHAR(1000) NULL AFTER removed_by,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) AFTER created_at,
    ADD INDEX idx_project_team_status (project_id, assignment_status),
    ADD INDEX idx_project_team_employee_history (project_id, employee_id, added_date);

UPDATE fixed_cost_project_team_member team
JOIN business_records project ON project.id=team.project_id
JOIN business_records employee ON employee.id=team.employee_id
SET team.role_designation=employee.category,
    team.added_date=COALESCE(project.record_date,DATE(team.created_at)),
    team.assignment_status='ACTIVE'
WHERE team.added_date IS NULL;

ALTER TABLE fixed_cost_project_team_member MODIFY added_date DATE NOT NULL;
