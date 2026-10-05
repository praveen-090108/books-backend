CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL DEFAULT 1,
    purchase_order_number VARCHAR(64) NOT NULL,
    purchase_order_date DATE NOT NULL,
    expected_delivery_date DATE,
    vendor_id BIGINT,
    vendor_name VARCHAR(160) NOT NULL,
    vendor_address_json TEXT,
    delivery_address_json TEXT,
    delivery_address_source VARCHAR(40),
    reference_number VARCHAR(100),
    shipment_preference VARCHAR(80),
    payment_terms VARCHAR(64),
    currency_code VARCHAR(16) NOT NULL DEFAULT 'INR',
    exchange_rate DECIMAL(18, 6) NOT NULL DEFAULT 1,
    gst_treatment VARCHAR(48),
    source_of_supply VARCHAR(2),
    destination_of_supply VARCHAR(2),
    place_of_supply VARCHAR(2),
    project_name VARCHAR(160),
    branch_name VARCHAR(160),
    warehouse_name VARCHAR(160),
    attention VARCHAR(160),
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    amount_type VARCHAR(24) NOT NULL DEFAULT 'TAX_EXCLUSIVE',
    subtotal DECIMAL(18, 2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    taxable_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    cgst_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    sgst_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    igst_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    cess_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    shipping_charge DECIMAL(18, 2) NOT NULL DEFAULT 0,
    adjustment_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    round_off_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_tax_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    notes TEXT,
    terms_and_conditions TEXT,
    attachment_name VARCHAR(255),
    attachment_url VARCHAR(1000),
    linked_bill_id BIGINT,
    linked_bill_number VARCHAR(64),
    issued_at DATETIME(6),
    issued_by VARCHAR(120),
    received_at DATETIME(6),
    received_by VARCHAR(120),
    created_by VARCHAR(120) NOT NULL DEFAULT 'Admin',
    updated_by VARCHAR(120) NOT NULL DEFAULT 'Admin',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_purchase_orders_organization_number (organization_id, purchase_order_number),
    INDEX idx_purchase_orders_organization_date (organization_id, purchase_order_date),
    INDEX idx_purchase_orders_organization_vendor (organization_id, vendor_id),
    INDEX idx_purchase_orders_organization_status (organization_id, status),
    INDEX idx_purchase_orders_expected_delivery (expected_delivery_date),
    INDEX idx_purchase_orders_reference (reference_number),
    INDEX idx_purchase_orders_created_by (created_by),
    CONSTRAINT fk_purchase_orders_vendor FOREIGN KEY (vendor_id) REFERENCES vendors(id),
    CONSTRAINT fk_purchase_orders_source_state FOREIGN KEY (source_of_supply) REFERENCES supply_states(code),
    CONSTRAINT fk_purchase_orders_destination_state FOREIGN KEY (destination_of_supply) REFERENCES supply_states(code),
    CONSTRAINT fk_purchase_orders_place_state FOREIGN KEY (place_of_supply) REFERENCES supply_states(code)
);

CREATE TABLE purchase_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    item_id BIGINT,
    item_name VARCHAR(180) NOT NULL,
    item_sku VARCHAR(100),
    item_type VARCHAR(24),
    description VARCHAR(1000),
    account_name VARCHAR(120),
    hsn_code VARCHAR(24),
    sac_code VARCHAR(24),
    quantity DECIMAL(18, 4) NOT NULL,
    received_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    billed_quantity DECIMAL(18, 4) NOT NULL DEFAULT 0,
    unit VARCHAR(40),
    rate DECIMAL(18, 4) NOT NULL,
    discount_type VARCHAR(24) NOT NULL DEFAULT 'NONE',
    discount_value DECIMAL(18, 4) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    tax_id BIGINT,
    tax_name VARCHAR(120),
    tax_rate DECIMAL(7, 4) NOT NULL DEFAULT 0,
    taxable_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    cgst_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    sgst_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    igst_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    cess_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
    line_total DECIMAL(18, 2) NOT NULL DEFAULT 0,
    warehouse_name VARCHAR(160),
    project_name VARCHAR(160),
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_purchase_order_items_order (purchase_order_id, sort_order),
    INDEX idx_purchase_order_items_item (item_id),
    CONSTRAINT fk_purchase_order_items_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_order_items_tax FOREIGN KEY (tax_id) REFERENCES tax_rates(id)
);

CREATE TABLE purchase_order_activities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    details VARCHAR(1000),
    performed_by VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_purchase_order_activities_order_date (purchase_order_id, created_at),
    CONSTRAINT fk_purchase_order_activities_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE CASCADE
);

INSERT INTO purchase_orders (
    organization_id, purchase_order_number, purchase_order_date, expected_delivery_date,
    vendor_id, vendor_name, reference_number, payment_terms, currency_code, exchange_rate,
    status, subtotal, taxable_amount, total_amount, notes, created_by, updated_by,
    deleted, created_at, updated_at
)
SELECT
    1, legacy.record_number, legacy.record_date, legacy.due_date,
    vendor.id, legacy.party_name, legacy.reference_number, 'Net 30', 'INR', 1,
    CASE
        WHEN UPPER(legacy.status) IN ('APPROVED', 'ISSUED') THEN 'ISSUED'
        WHEN UPPER(legacy.secondary_status) = 'PARTIAL' THEN 'PARTIALLY_RECEIVED'
        ELSE 'DRAFT'
    END,
    legacy.amount, legacy.amount, legacy.amount, legacy.notes,
    COALESCE(legacy.owner_name, 'Admin'), COALESCE(legacy.owner_name, 'Admin'),
    FALSE, legacy.created_at, legacy.updated_at
FROM business_records legacy
LEFT JOIN vendors vendor
    ON vendor.organization_id = 1 AND LOWER(vendor.vendor_name) = LOWER(legacy.party_name)
WHERE legacy.module = 'purchases' AND legacy.type = 'orders'
  AND NOT EXISTS (
      SELECT 1 FROM purchase_orders current_order
      WHERE current_order.organization_id = 1
        AND current_order.purchase_order_number = legacy.record_number
  );

INSERT INTO purchase_order_activities (purchase_order_id, action, details, performed_by, created_at)
SELECT id, 'CREATED', 'Imported from the existing Purchase Order records.', created_by, created_at
FROM purchase_orders;

INSERT INTO document_number_preferences (
    document_type, auto_generate, prefix, suffix, number_separator, number_format,
    starting_number, next_number, created_at, updated_at
)
SELECT 'purchaseOrders', TRUE, 'PO', '', '-', '000000', 129, 129, NOW(6), NOW(6)
WHERE NOT EXISTS (
    SELECT 1 FROM document_number_preferences WHERE document_type = 'purchaseOrders'
);
