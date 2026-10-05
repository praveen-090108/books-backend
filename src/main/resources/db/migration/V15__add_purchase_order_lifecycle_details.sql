ALTER TABLE purchase_orders
    ADD COLUMN cancelled_at DATETIME(6),
    ADD COLUMN cancelled_by VARCHAR(120),
    ADD COLUMN cancellation_reason VARCHAR(1000),
    ADD COLUMN closed_at DATETIME(6),
    ADD COLUMN closed_by VARCHAR(120),
    ADD COLUMN closing_reason VARCHAR(1000);

CREATE INDEX idx_purchase_orders_cancelled_at ON purchase_orders (cancelled_at);
CREATE INDEX idx_purchase_orders_closed_at ON purchase_orders (closed_at);
