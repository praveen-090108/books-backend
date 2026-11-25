CREATE TABLE lead_source_master (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lead_source_org_name UNIQUE (organization_id, name),
    INDEX idx_lead_source_org_active_order (organization_id, active, display_order, name)
);

INSERT IGNORE INTO lead_source_master (organization_id, name, description, display_order)
SELECT organization.id, source.name, source.description, source.display_order
FROM organizations organization
CROSS JOIN (
    SELECT 'Website' name, 'Lead captured from the organization website.' description, 10 display_order
    UNION ALL SELECT 'Referral', 'Lead referred by a customer, partner or employee.', 20
    UNION ALL SELECT 'Email', 'Lead received through an email campaign or enquiry.', 30
    UNION ALL SELECT 'Social Media', 'Lead generated through a social media channel.', 40
    UNION ALL SELECT 'Campaign', 'Lead generated from a marketing campaign.', 50
    UNION ALL SELECT 'Partner', 'Lead received through a business partner.', 60
    UNION ALL SELECT 'Event', 'Lead generated from an event or conference.', 70
    UNION ALL SELECT 'Cold Call', 'Lead created from outbound calling.', 80
    UNION ALL SELECT 'Other', 'Any source not covered by the standard options.', 999
) source;

INSERT IGNORE INTO lead_source_master (organization_id, name, description, display_order)
SELECT legacy.organization_id, legacy.name, 'Preserved from existing lead-management records.', 900
FROM (
    SELECT organization_id, TRIM(source) name FROM leads
    WHERE source IS NOT NULL AND TRIM(source) <> ''
    UNION
    SELECT organization_id, TRIM(lead_source) name FROM lead_companies
    WHERE lead_source IS NOT NULL AND TRIM(lead_source) <> ''
    UNION
    SELECT organization_id, TRIM(lead_source) name FROM lead_contacts
    WHERE lead_source IS NOT NULL AND TRIM(lead_source) <> ''
) legacy;
