CREATE TABLE bills (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL DEFAULT 1,
    bill_number VARCHAR(64) NOT NULL,
    vendor_id BIGINT NULL,
    vendor_name VARCHAR(160) NOT NULL,
    bill_date DATE NOT NULL,
    due_date DATE NOT NULL,
    reference_number VARCHAR(100) NULL,
    purchase_order_id BIGINT NULL,
    purchase_order_number VARCHAR(64) NULL,
    payment_terms VARCHAR(64) NULL,
    place_of_supply VARCHAR(2) NULL,
    source_of_supply VARCHAR(2) NULL,
    destination_of_supply VARCHAR(2) NULL,
    gst_treatment VARCHAR(48) NULL,
    currency_code VARCHAR(16) NOT NULL DEFAULT 'INR',
    exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 1,
    subject VARCHAR(255) NULL,
    amount_type VARCHAR(24) NOT NULL DEFAULT 'TAX_EXCLUSIVE',
    subtotal DECIMAL(18,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    taxable_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    cgst_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    sgst_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    igst_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    cess_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    shipping_charge DECIMAL(18,2) NOT NULL DEFAULT 0,
    adjustment_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    round_off_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    amount_paid DECIMAL(18,2) NOT NULL DEFAULT 0,
    balance_due DECIMAL(18,2) NOT NULL DEFAULT 0,
    notes TEXT NULL,
    terms_and_conditions TEXT NULL,
    attachment_name VARCHAR(255) NULL,
    attachment_url VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    expected_payment_date DATE NULL,
    opened_at DATETIME NULL,
    opened_by VARCHAR(120) NULL,
    paid_at DATETIME NULL,
    voided_at DATETIME NULL,
    voided_by VARCHAR(120) NULL,
    void_reason VARCHAR(1000) NULL,
    created_by VARCHAR(120) NOT NULL DEFAULT 'Admin',
    updated_by VARCHAR(120) NOT NULL DEFAULT 'Admin',
    deleted BIT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bills_org_number (organization_id, bill_number),
    KEY idx_bills_vendor (organization_id, vendor_id),
    KEY idx_bills_dates (organization_id, bill_date, due_date),
    KEY idx_bills_status (organization_id, status, deleted),
    KEY idx_bills_reference (organization_id, reference_number),
    CONSTRAINT fk_bills_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(id),
    CONSTRAINT fk_bills_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_bills_place FOREIGN KEY (place_of_supply) REFERENCES supply_states(code),
    CONSTRAINT fk_bills_source FOREIGN KEY (source_of_supply) REFERENCES supply_states(code),
    CONSTRAINT fk_bills_destination FOREIGN KEY (destination_of_supply) REFERENCES supply_states(code)
);

CREATE TABLE bill_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bill_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_name VARCHAR(160) NOT NULL,
    item_sku VARCHAR(80) NULL,
    item_type VARCHAR(32) NULL,
    description VARCHAR(1000) NULL,
    account_name VARCHAR(120) NULL,
    hsn_code VARCHAR(24) NULL,
    sac_code VARCHAR(24) NULL,
    quantity DECIMAL(18,4) NOT NULL,
    unit VARCHAR(40) NULL,
    rate DECIMAL(18,2) NOT NULL,
    discount_type VARCHAR(24) NOT NULL DEFAULT 'NONE',
    discount_value DECIMAL(18,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    tax_id BIGINT NULL,
    tax_name VARCHAR(120) NULL,
    tax_rate DECIMAL(10,4) NOT NULL DEFAULT 0,
    taxable_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    cgst_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    sgst_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    igst_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    cess_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    line_total DECIMAL(18,2) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_bill_items_bill (bill_id, sort_order),
    KEY idx_bill_items_master (item_id),
    CONSTRAINT fk_bill_items_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE,
    CONSTRAINT fk_bill_items_tax FOREIGN KEY (tax_id) REFERENCES tax_rates(id)
);

CREATE TABLE bill_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bill_id BIGINT NOT NULL,
    payment_number VARCHAR(64) NOT NULL,
    payment_date DATE NOT NULL,
    payment_mode VARCHAR(64) NOT NULL,
    paid_through VARCHAR(160) NOT NULL,
    to_account VARCHAR(160) NULL,
    reference_number VARCHAR(120) NULL,
    amount DECIMAL(18,2) NOT NULL,
    notes VARCHAR(2000) NULL,
    attachment_name VARCHAR(255) NULL,
    attachment_url VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PAID',
    reversed_at DATETIME NULL,
    reversed_by VARCHAR(120) NULL,
    reversal_reason VARCHAR(1000) NULL,
    created_by VARCHAR(120) NOT NULL DEFAULT 'Admin',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_payments_number (payment_number),
    KEY idx_bill_payments_bill (bill_id, payment_date),
    CONSTRAINT fk_bill_payments_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
);

CREATE TABLE bill_activities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bill_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    details VARCHAR(2000) NULL,
    performed_by VARCHAR(120) NOT NULL DEFAULT 'Admin',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_bill_activities_bill (bill_id, created_at),
    CONSTRAINT fk_bill_activities_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE
);

INSERT INTO bills (
    organization_id, bill_number, vendor_name, bill_date, due_date, reference_number,
    currency_code, subtotal, taxable_amount, total_amount, amount_paid, balance_due,
    notes, status, created_by, updated_by, created_at, updated_at
)
SELECT 1, record_number, COALESCE(NULLIF(party_name, ''), 'Legacy Vendor'), record_date,
       COALESCE(due_date, record_date), reference_number, 'INR', amount, amount,
       amount, GREATEST(amount - balance_amount, 0), balance_amount, notes,
       CASE
           WHEN UPPER(status) IN ('PAID', 'DEPOSITED') OR balance_amount <= 0 THEN 'PAID'
           WHEN balance_amount < amount THEN 'PARTIALLY_PAID'
           WHEN UPPER(status) IN ('DRAFT') THEN 'DRAFT'
           WHEN due_date < CURRENT_DATE THEN 'OVERDUE'
           ELSE 'OPEN'
       END,
       COALESCE(NULLIF(owner_name, ''), 'Admin'), COALESCE(NULLIF(owner_name, ''), 'Admin'),
       created_at, updated_at
FROM business_records
WHERE module = 'purchases' AND type = 'bills';

INSERT INTO bill_items (
    bill_id, item_id, item_name, description, quantity, unit, rate,
    taxable_amount, line_total, sort_order
)
SELECT b.id, 0, 'Legacy bill amount', 'Imported from the previous Bills module', 1, 'Nos',
       b.total_amount, b.total_amount, b.total_amount, 0
FROM bills b
WHERE NOT EXISTS (SELECT 1 FROM bill_items bi WHERE bi.bill_id = b.id);

