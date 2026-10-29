CREATE TABLE ey_qualification_test_run (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_key VARCHAR(48) NOT NULL UNIQUE,
    suite VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    total_scenarios INT NOT NULL DEFAULT 0,
    passed_count INT NOT NULL DEFAULT 0,
    expected_failure_count INT NOT NULL DEFAULT 0,
    unexpected_failure_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    stop_requested BOOLEAN NOT NULL DEFAULT FALSE,
    summary VARCHAR(1000)
);

CREATE TABLE ey_qualification_test_result (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NOT NULL,
    scenario_id VARCHAR(80) NOT NULL,
    api_name VARCHAR(32) NOT NULL,
    scenario_name VARCHAR(255) NOT NULL,
    expected_outcome VARCHAR(32) NOT NULL,
    actual_outcome VARCHAR(32) NOT NULL,
    result_status VARCHAR(32) NOT NULL,
    http_status INT,
    provider_error_code VARCHAR(255),
    safe_message VARCHAR(1000),
    correlation_id VARCHAR(64) NOT NULL,
    duration_ms BIGINT NOT NULL,
    executed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ey_qualification_result_run FOREIGN KEY (test_run_id) REFERENCES ey_qualification_test_run(id) ON DELETE CASCADE,
    KEY idx_ey_qualification_result_run (test_run_id, api_name)
);

CREATE TABLE ey_qualification_test_document (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    test_run_id BIGINT NOT NULL,
    scenario_id VARCHAR(80) NOT NULL,
    document_number VARCHAR(32) NOT NULL UNIQUE,
    document_type VARCHAR(8) NOT NULL,
    sandbox_irn VARCHAR(128),
    acknowledgement_number VARCHAR(64),
    acknowledgement_date DATETIME(6),
    irn_status VARCHAR(24) NOT NULL,
    details_retrieved BOOLEAN NOT NULL DEFAULT FALSE,
    cancellation_status VARCHAR(24) NOT NULL DEFAULT 'NOT_CANCELLED',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ey_qualification_document_run FOREIGN KEY (test_run_id) REFERENCES ey_qualification_test_run(id) ON DELETE CASCADE,
    KEY idx_ey_qualification_document_irn (sandbox_irn)
);

CREATE TABLE ey_qualification_portal_progress (
    api_name VARCHAR(32) NOT NULL PRIMARY KEY,
    target_success INT NOT NULL,
    target_failure INT NOT NULL,
    portal_success INT,
    portal_failure INT,
    verified_at DATETIME(6),
    verification_source VARCHAR(80),
    updated_by VARCHAR(120),
    updated_at DATETIME(6) NOT NULL
);

INSERT INTO ey_qualification_portal_progress(api_name,target_success,target_failure,portal_success,portal_failure,updated_at)
VALUES ('AUTHENTICATE',30,10,NULL,NULL,NOW(6)),('GENERATE_IRN',100,50,NULL,NULL,NOW(6)),
       ('CANCEL_IRN',40,20,NULL,NULL,NOW(6)),('GET_IRN_DETAILS',30,10,NULL,NULL,NOW(6));
