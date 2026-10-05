-- The original UI saved INVOICE_TOTAL as an implicit default. Zoho-style TDS
-- settlement uses the taxable value unless the organization changes it later.
UPDATE business_records
SET notes = JSON_OBJECT('tdsBaseType', 'TAXABLE_VALUE')
WHERE module = 'settings'
  AND type = 'organization'
  AND (notes IS NULL OR JSON_VALID(notes) = 0);

UPDATE business_records
SET notes = JSON_SET(notes, '$.tdsBaseType', 'TAXABLE_VALUE')
WHERE module = 'settings'
  AND type = 'organization'
  AND JSON_VALID(notes) = 1
  AND (
      JSON_UNQUOTE(JSON_EXTRACT(notes, '$.tdsBaseType')) IS NULL
      OR JSON_UNQUOTE(JSON_EXTRACT(notes, '$.tdsBaseType')) = 'INVOICE_TOTAL'
  );
