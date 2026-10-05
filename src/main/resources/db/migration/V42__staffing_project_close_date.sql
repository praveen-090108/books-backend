ALTER TABLE business_records
    ADD COLUMN closed_date DATE NULL AFTER due_date;

UPDATE business_records
SET closed_date = due_date,
    status = 'Close'
WHERE module = 'projects'
  AND type = 'staffing'
  AND LOWER(TRIM(status)) IN ('close', 'closed');

UPDATE business_records
SET status = 'Hold'
WHERE module = 'projects'
  AND type = 'staffing'
  AND LOWER(TRIM(status)) IN ('hold', 'on hold');

CREATE INDEX idx_business_records_project_closed_date
    ON business_records(module, type, closed_date);
