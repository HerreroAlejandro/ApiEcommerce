-- Select the EXISTING ecommerce schema. This script performs no remote operations.
-- Inspect SHOW CREATE TABLE first if Hibernate already added a column or table.
ALTER TABLE payments
    ADD COLUMN providerReferenceId VARCHAR(30) NULL,
    ADD COLUMN providerStatusDetail VARCHAR(40) NULL,
    ADD CONSTRAINT uk_payment_numeric_reference UNIQUE (providerReferenceId);

ALTER TABLE payments MODIFY COLUMN reviewReason
    ENUM('NONE','AMOUNT_MISMATCH','CURRENCY_MISMATCH','COLLECTOR_MISMATCH',
         'MODE_MISMATCH','LATE_APPROVAL','DUPLICATE_PAYMENT','PARTIAL_REFUND',
         'CHARGEBACK','CHARGEBACK_LOST','UNSUPPORTED_METHOD','INVALID_PROVIDER_STATE') NOT NULL;

ALTER TABLE orders MODIFY COLUMN paymentStatus
    ENUM('PENDING','PAID','FAILED','REFUNDED','REVIEW','PARTIALLY_REFUNDED','CHARGED_BACK') NOT NULL;

CREATE TABLE IF NOT EXISTS chargeback_cases (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NULL,
    payment_id BIGINT NOT NULL,
    providerCaseId VARCHAR(30) NOT NULL,
    resolution VARCHAR(20) NOT NULL,
    detectedAt DATETIME(6) NOT NULL,
    verifiedAt DATETIME(6) NULL,
    providerUpdatedAt DATETIME(6) NULL,
    documentationDeadline DATETIME(6) NULL,
    amount DECIMAL(16,2) NULL,
    currency VARCHAR(3) NULL,
    coverageApplied BOOLEAN NULL,
    documentationStatus VARCHAR(40) NULL,
    providerReason VARCHAR(100) NULL,
    grouped BOOLEAN NOT NULL DEFAULT FALSE,
    lastAdminNote VARCHAR(500) NULL,
    lastActorUserId BIGINT NULL,
    lastNoteAt DATETIME(6) NULL,
    CONSTRAINT uk_chargeback_payment_case UNIQUE (payment_id, providerCaseId),
    CONSTRAINT fk_chargeback_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

-- No historical case, refund, stock or payment outcome is invented here.
-- Sync the remote order to obtain numeric references/case IDs from Mercado Pago.
