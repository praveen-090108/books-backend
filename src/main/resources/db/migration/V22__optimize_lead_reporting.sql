-- Cover the filters and joins used by Lead Management reporting queries.
CREATE INDEX idx_lead_report_filters ON leads (organization_id, created_at, pipeline_id, owner_id, lead_type, status, source, deleted);
CREATE INDEX idx_lead_report_skill ON leads (organization_id, primary_skill_id, created_at, deleted);
CREATE INDEX idx_lead_activity_report ON lead_activities (organization_id, activity_type, occurred_at, entity_type, entity_id);
CREATE INDEX idx_lead_task_report ON lead_tasks (organization_id, status, due_date, entity_type, entity_id);
