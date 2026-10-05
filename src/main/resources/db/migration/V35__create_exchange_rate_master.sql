CREATE TABLE exchange_rate_master (
    id BIGINT NOT NULL AUTO_INCREMENT,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(20,10) NOT NULL,
    effective_date DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_exchange_rate_effective UNIQUE (from_currency, to_currency, effective_date),
    CONSTRAINT fk_exchange_rate_from_currency FOREIGN KEY (from_currency) REFERENCES currency_master(code),
    CONSTRAINT fk_exchange_rate_to_currency FOREIGN KEY (to_currency) REFERENCES currency_master(code),
    CONSTRAINT chk_exchange_rate_positive CHECK (rate > 0)
);

CREATE INDEX idx_exchange_rate_lookup
    ON exchange_rate_master(from_currency, to_currency, active, effective_date);

-- Rate means one unit of FROM currency expressed in TO currency. These initial
-- values are administrative seed data and can be superseded by a later dated rate.
INSERT INTO exchange_rate_master (from_currency, to_currency, rate, effective_date) VALUES
('INR','AUD',0.0180000000,'2026-04-01'),
('INR','CAD',0.0166000000,'2026-04-01'),
('INR','USD',0.0120000000,'2026-04-01'),
('INR','GBP',0.0092000000,'2026-04-01'),
('INR','EUR',0.0105000000,'2026-04-01'),
('INR','AED',0.0440000000,'2026-04-01'),
('INR','SGD',0.0155000000,'2026-04-01'),
('INR','NZD',0.0195000000,'2026-04-01'),
('INR','JPY',1.7800000000,'2026-04-01'),
('INR','CHF',0.0098000000,'2026-04-01'),
('INR','CNY',0.0860000000,'2026-04-01'),
('INR','HKD',0.0935000000,'2026-04-01'),
('INR','ZAR',0.2200000000,'2026-04-01');
