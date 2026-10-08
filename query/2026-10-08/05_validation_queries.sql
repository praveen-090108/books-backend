-- Read-only validation for the IntelliaTech Books V46-V49 production upgrade.
-- Select the application database before executing this file.

SELECT DATABASE() AS selected_database, VERSION() AS mysql_version;

SELECT installed_rank, version, description, installed_on, success
FROM flyway_schema_history
WHERE version IN ('46', '47', '48', '49')
ORDER BY installed_rank;

SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'e_invoice_details',
    'e_invoice_api_logs',
    'irp_configuration',
    'irp_configuration_audit'
  )
ORDER BY table_name;

SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'e_invoice_details' AND column_name IN (
      'invoice_id', 'credit_note_id', 'irn', 'ack_no', 'ack_date',
      'signed_invoice', 'signed_qr_code', 'status', 'eway_bill_no',
      'eway_bill_date', 'eway_bill_valid_till', 'remarks', 'irp_provider',
      'irp_environment', 'irp_gstin'
    ))
    OR (table_name = 'irp_configuration' AND column_name IN (
      'environment', 'api_base_url', 'client_id_encrypted',
      'client_secret_encrypted', 'api_username_encrypted',
      'api_password_encrypted', 'gstin', 'is_active', 'is_configured',
      'credential_version', 'last_connection_test', 'last_connection_status'
    ))
  )
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index) AS indexed_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('e_invoice_details', 'e_invoice_api_logs', 'irp_configuration')
GROUP BY table_name, index_name, non_unique
ORDER BY table_name, index_name;

SELECT constraint_name, table_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name IN ('e_invoice_details', 'e_invoice_api_logs', 'irp_configuration')
ORDER BY table_name, constraint_name;

-- At most one environment may be active for a company.
SELECT company_id, SUM(is_active = b'1') AS active_environment_count
FROM irp_configuration
GROUP BY company_id
HAVING SUM(is_active = b'1') > 1;

-- Historical records are reported, not rewritten. Review any NULL provenance
-- before using cancellation or retrieval for an older IRN.
SELECT COUNT(*) AS historical_irns_without_environment
FROM e_invoice_details
WHERE irn IS NOT NULL AND irp_environment IS NULL;

-- Every row must belong to exactly one supported document type after V48.
SELECT id, invoice_id, credit_note_id
FROM e_invoice_details
WHERE (invoice_id IS NULL AND credit_note_id IS NULL)
   OR (invoice_id IS NOT NULL AND credit_note_id IS NOT NULL);
