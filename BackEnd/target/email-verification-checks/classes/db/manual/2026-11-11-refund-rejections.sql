-- Existing ecommerce schema. Inspect SHOW CREATE TABLE payment_refunds first
-- if Hibernate ddl-auto=update has already applied any of these changes.
ALTER TABLE payment_refunds
    MODIFY COLUMN status ENUM('REQUESTED', 'PROCESSING', 'CONFIRMED', 'REJECTED') NOT NULL,
    ADD COLUMN rejectedAt DATETIME(6) NULL,
    ADD COLUMN rejectionCode VARCHAR(64) NULL,
    ADD COLUMN rejectionReason VARCHAR(500) NULL;

-- No money, stock, order status or historical refund is modified by this script.
