ALTER TABLE e_invoice_details
    ADD COLUMN eway_bill_no VARCHAR(32) NULL AFTER cancel_remarks,
    ADD COLUMN eway_bill_date DATETIME(6) NULL AFTER eway_bill_no,
    ADD COLUMN eway_bill_valid_till DATETIME(6) NULL AFTER eway_bill_date,
    ADD COLUMN remarks VARCHAR(1000) NULL AFTER eway_bill_valid_till;
