CREATE TABLE supply_states (
    code VARCHAR(2) NOT NULL,
    name VARCHAR(120) NOT NULL,
    territory_type VARCHAR(32) NOT NULL DEFAULT 'STATE',
    PRIMARY KEY (code),
    UNIQUE KEY uk_supply_states_name (name)
);

INSERT INTO supply_states (code, name, territory_type) VALUES
('01', 'Jammu and Kashmir', 'UNION_TERRITORY'), ('02', 'Himachal Pradesh', 'STATE'),
('03', 'Punjab', 'STATE'), ('04', 'Chandigarh', 'UNION_TERRITORY'),
('05', 'Uttarakhand', 'STATE'), ('06', 'Haryana', 'STATE'),
('07', 'Delhi', 'UNION_TERRITORY'), ('08', 'Rajasthan', 'STATE'),
('09', 'Uttar Pradesh', 'STATE'), ('10', 'Bihar', 'STATE'),
('11', 'Sikkim', 'STATE'), ('12', 'Arunachal Pradesh', 'STATE'),
('13', 'Nagaland', 'STATE'), ('14', 'Manipur', 'STATE'),
('15', 'Mizoram', 'STATE'), ('16', 'Tripura', 'STATE'),
('17', 'Meghalaya', 'STATE'), ('18', 'Assam', 'STATE'),
('19', 'West Bengal', 'STATE'), ('20', 'Jharkhand', 'STATE'),
('21', 'Odisha', 'STATE'), ('22', 'Chhattisgarh', 'STATE'),
('23', 'Madhya Pradesh', 'STATE'), ('24', 'Gujarat', 'STATE'),
('26', 'Dadra and Nagar Haveli and Daman and Diu', 'UNION_TERRITORY'),
('27', 'Maharashtra', 'STATE'), ('29', 'Karnataka', 'STATE'),
('30', 'Goa', 'STATE'), ('31', 'Lakshadweep', 'UNION_TERRITORY'),
('32', 'Kerala', 'STATE'), ('33', 'Tamil Nadu', 'STATE'),
('34', 'Puducherry', 'UNION_TERRITORY'), ('35', 'Andaman and Nicobar Islands', 'UNION_TERRITORY'),
('36', 'Telangana', 'STATE'), ('37', 'Andhra Pradesh', 'STATE'),
('38', 'Ladakh', 'UNION_TERRITORY'), ('97', 'Other Territory', 'OTHER');

CREATE TABLE tax_rates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    rate DECIMAL(7, 4) NOT NULL DEFAULT 0,
    tax_category VARCHAR(32) NOT NULL DEFAULT 'TAXABLE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tax_rates_code (code),
    INDEX idx_tax_rates_active_order (active, display_order)
);

INSERT INTO tax_rates (code, name, rate, tax_category, display_order) VALUES
('NON_TAXABLE', 'Non-Taxable', 0, 'NON_TAXABLE', 1),
('GST_0', 'GST 0%', 0, 'TAXABLE', 2),
('GST_5', 'GST 5%', 5, 'TAXABLE', 3),
('GST_12', 'GST 12%', 12, 'TAXABLE', 4),
('GST_18', 'GST 18%', 18, 'TAXABLE', 5),
('GST_28', 'GST 28%', 28, 'TAXABLE', 6),
('EXEMPT', 'Exempt', 0, 'EXEMPT', 7),
('OUT_OF_SCOPE', 'Out of Scope', 0, 'OUT_OF_SCOPE', 8);

CREATE TABLE expenses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL DEFAULT 1,
    expense_number VARCHAR(64) NOT NULL,
    expense_date DATE NOT NULL,
    expense_account VARCHAR(120) NOT NULL,
    expense_title VARCHAR(180) NOT NULL,
    expense_type VARCHAR(20) NOT NULL,
    vendor_id BIGINT,
    invoice_number VARCHAR(100),
    hsn_code VARCHAR(16),
    sac_code VARCHAR(16),
    gst_treatment VARCHAR(48) NOT NULL,
    source_of_supply VARCHAR(2),
    destination_of_supply VARCHAR(2),
    tax_id BIGINT,
    tax_rate DECIMAL(7, 4) NOT NULL DEFAULT 0,
    tax_name VARCHAR(120),
    amount_type VARCHAR(24) NOT NULL,
    entered_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    taxable_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    cgst_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    sgst_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    igst_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    cess_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(16, 2) NOT NULL DEFAULT 0,
    currency VARCHAR(64) NOT NULL DEFAULT 'INR - Indian Rupee',
    reference_number VARCHAR(100),
    description VARCHAR(1000),
    notes VARCHAR(1000),
    payment_mode VARCHAR(64),
    paid_through VARCHAR(120),
    project_name VARCHAR(160),
    status VARCHAR(24) NOT NULL DEFAULT 'PAID',
    attachment_name VARCHAR(255),
    attachment_url VARCHAR(1000),
    created_by VARCHAR(120) NOT NULL DEFAULT 'Admin',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_expenses_organization_number (organization_id, expense_number),
    UNIQUE KEY uk_expenses_vendor_invoice (organization_id, vendor_id, invoice_number),
    INDEX idx_expenses_organization_date (organization_id, expense_date),
    INDEX idx_expenses_organization_vendor (organization_id, vendor_id),
    INDEX idx_expenses_organization_status (organization_id, status),
    INDEX idx_expenses_gst_treatment (gst_treatment),
    INDEX idx_expenses_invoice_number (invoice_number),
    CONSTRAINT fk_expenses_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(id),
    CONSTRAINT fk_expenses_tax_rate FOREIGN KEY (tax_id) REFERENCES tax_rates(id),
    CONSTRAINT fk_expenses_source_state FOREIGN KEY (source_of_supply) REFERENCES supply_states(code),
    CONSTRAINT fk_expenses_destination_state FOREIGN KEY (destination_of_supply) REFERENCES supply_states(code)
);

INSERT INTO expenses (
    organization_id, expense_number, expense_date, expense_account, expense_title, expense_type,
    vendor_id, invoice_number, gst_treatment, amount_type, entered_amount, taxable_amount,
    total_amount, currency, reference_number, description, payment_mode, status, created_by,
    deleted, created_at, updated_at
)
SELECT
    1, legacy.record_number, legacy.record_date, COALESCE(legacy.category, 'General Expenses'),
    COALESCE(NULLIF(legacy.reference_number, ''), legacy.category, 'Business Expense'), 'SERVICES',
    vendor.id, NULL, CASE WHEN vendor.gstin IS NOT NULL AND vendor.gstin <> ''
        THEN 'REGISTERED_BUSINESS_REGULAR' ELSE 'UNREGISTERED_BUSINESS' END,
    'TAX_EXCLUSIVE', legacy.amount, legacy.amount, legacy.amount, 'INR - Indian Rupee',
    legacy.reference_number, legacy.notes, legacy.payment_mode,
    CASE WHEN UPPER(legacy.status) = 'PENDING' THEN 'PENDING' ELSE 'PAID' END,
    COALESCE(legacy.owner_name, 'Admin'), FALSE, legacy.created_at, legacy.updated_at
FROM business_records legacy
LEFT JOIN vendors vendor ON vendor.organization_id = 1 AND LOWER(vendor.vendor_name) = LOWER(legacy.party_name)
WHERE legacy.module = 'purchases' AND legacy.type = 'expenses'
  AND NOT EXISTS (SELECT 1 FROM expenses current_expense WHERE current_expense.expense_number = legacy.record_number);
