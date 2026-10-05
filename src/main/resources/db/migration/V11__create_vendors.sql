CREATE TABLE vendors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL DEFAULT 1,
    vendor_number VARCHAR(64) NOT NULL,
    vendor_name VARCHAR(160) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    company_name VARCHAR(160),
    vendor_type VARCHAR(64),
    source_of_supply VARCHAR(120),
    currency VARCHAR(64) NOT NULL DEFAULT 'INR - Indian Rupee',
    payment_terms VARCHAR(64),
    tax_treatment VARCHAR(80),
    gstin VARCHAR(20),
    pan VARCHAR(16),
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    primary_contact VARCHAR(160),
    email VARCHAR(180),
    phone VARCHAR(40),
    mobile VARCHAR(40),
    website VARCHAR(255),
    billing_address_line1 VARCHAR(255),
    billing_address_line2 VARCHAR(255),
    billing_city VARCHAR(120),
    billing_state VARCHAR(120),
    billing_pincode VARCHAR(20),
    billing_country VARCHAR(120),
    shipping_address_line1 VARCHAR(255),
    shipping_address_line2 VARCHAR(255),
    shipping_city VARCHAR(120),
    shipping_state VARCHAR(120),
    shipping_pincode VARCHAR(20),
    shipping_country VARCHAR(120),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vendors_organization_number (organization_id, vendor_number),
    INDEX idx_vendors_organization_status (organization_id, status),
    INDEX idx_vendors_organization_name (organization_id, vendor_name),
    INDEX idx_vendors_created_at (created_at)
);

CREATE TABLE vendor_bank_details (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vendor_id BIGINT NOT NULL,
    account_holder_name VARCHAR(160),
    beneficiary_name VARCHAR(160),
    bank_name VARCHAR(160),
    account_number VARCHAR(80),
    ifsc_code VARCHAR(16),
    branch_name VARCHAR(160),
    account_type VARCHAR(24),
    swift_code VARCHAR(16),
    iban VARCHAR(40),
    bank_country VARCHAR(120),
    bank_address VARCHAR(500),
    upi_id VARCHAR(160),
    notes VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vendor_bank_details_vendor (vendor_id),
    CONSTRAINT fk_vendor_bank_details_vendor
        FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE CASCADE
);

INSERT INTO vendors (
    organization_id,
    vendor_number,
    vendor_name,
    display_name,
    company_name,
    vendor_type,
    source_of_supply,
    currency,
    payment_terms,
    tax_treatment,
    status,
    primary_contact,
    email,
    phone,
    billing_city,
    billing_country,
    created_at,
    updated_at
)
SELECT
    1,
    record_number,
    party_name,
    party_name,
    party_name,
    category,
    party_city,
    'INR - Indian Rupee',
    'Net 30',
    'Business',
    CASE WHEN UPPER(status) = 'INACTIVE' THEN 'INACTIVE' ELSE 'ACTIVE' END,
    owner_name,
    party_email,
    party_phone,
    party_city,
    'India',
    created_at,
    updated_at
FROM business_records legacy
WHERE legacy.module = 'purchases'
  AND legacy.type = 'vendors'
  AND NOT EXISTS (
      SELECT 1
      FROM vendors current_vendor
      WHERE current_vendor.organization_id = 1
        AND current_vendor.vendor_number = legacy.record_number
  );
