CREATE TABLE currency_master (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(3) NOT NULL,
    name VARCHAR(80) NOT NULL,
    symbol VARCHAR(12) NOT NULL,
    decimal_places INT NOT NULL DEFAULT 2,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_currency_master_code UNIQUE (code)
);

INSERT INTO currency_master (code, name, symbol, decimal_places, active, display_order) VALUES
('INR', 'Indian Rupee', '₹', 2, TRUE, 10),
('AUD', 'Australian Dollar', 'A$', 2, TRUE, 20),
('USD', 'US Dollar', '$', 2, TRUE, 30),
('CAD', 'Canadian Dollar', 'C$', 2, TRUE, 40),
('GBP', 'British Pound', '£', 2, TRUE, 50),
('EUR', 'Euro', '€', 2, TRUE, 60),
('AED', 'UAE Dirham', 'AED', 2, TRUE, 70),
('SGD', 'Singapore Dollar', 'S$', 2, TRUE, 80),
('NZD', 'New Zealand Dollar', 'NZ$', 2, TRUE, 90),
('JPY', 'Japanese Yen', '¥', 0, TRUE, 100),
('CHF', 'Swiss Franc', 'CHF', 2, TRUE, 110),
('CNY', 'Chinese Yuan', '¥', 2, TRUE, 120),
('HKD', 'Hong Kong Dollar', 'HK$', 2, TRUE, 130),
('ZAR', 'South African Rand', 'R', 2, TRUE, 140);
