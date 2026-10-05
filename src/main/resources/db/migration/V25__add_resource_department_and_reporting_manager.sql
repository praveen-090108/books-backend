ALTER TABLE business_records
    ADD COLUMN department VARCHAR(50) NULL,
    ADD COLUMN designation VARCHAR(150) NULL,
    ADD COLUMN reporting_manager_id BIGINT NULL;

UPDATE business_records
SET designation = category
WHERE module = 'resources'
  AND type = 'resources'
  AND designation IS NULL;

ALTER TABLE business_records
    ADD CONSTRAINT fk_business_records_reporting_manager
        FOREIGN KEY (reporting_manager_id) REFERENCES business_records(id);

CREATE INDEX idx_business_records_resource_department
    ON business_records(module, type, department, status);

CREATE INDEX idx_business_records_reporting_manager
    ON business_records(reporting_manager_id);
