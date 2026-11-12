-- MySQL. Run with the intended database selected. Existing tables are retained.
-- Inspect any schema already created by Hibernate before running in production.
SET @mp_schema_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'providerPaymentId'),
    'SELECT 1',
    'ALTER TABLE orders ADD COLUMN providerPaymentId VARCHAR(64) NULL'
);
PREPARE mp_schema_statement FROM @mp_schema_sql;
EXECUTE mp_schema_statement;
DEALLOCATE PREPARE mp_schema_statement;

ALTER TABLE orders MODIFY COLUMN providerPaymentId VARCHAR(64) NULL;

ALTER TABLE orders MODIFY COLUMN paymentStatus
    ENUM('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'REVIEW') NOT NULL;

ALTER TABLE order_change_audit MODIFY COLUMN actorUserId BIGINT NULL;

CREATE TABLE IF NOT EXISTS payment_checkouts (
    reference VARCHAR(36) NOT NULL PRIMARY KEY,
    version BIGINT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    collectorId VARCHAR(30) NOT NULL,
    liveMode BOOLEAN NOT NULL,
    expiresAt DATETIME(6) NOT NULL,
    createdAt DATETIME(6) NOT NULL,
    providerOrderId VARCHAR(64) NULL,
    requestBody LONGTEXT NULL,
    checkoutUrl VARCHAR(1000) NULL,
    CONSTRAINT uk_payment_checkout_order UNIQUE (order_id),
    CONSTRAINT uk_payment_checkout_provider_order UNIQUE (providerOrderId),
    CONSTRAINT fk_payment_checkout_order FOREIGN KEY (order_id) REFERENCES orders(idOrder)
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NULL,
    checkout_reference VARCHAR(36) NOT NULL,
    providerPaymentId VARCHAR(64) NOT NULL,
    providerStatus VARCHAR(40) NOT NULL,
    amount DECIMAL(16,2) NOT NULL,
    refundedAmount DECIMAL(16,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    collectorId VARCHAR(30) NOT NULL,
    liveMode BOOLEAN NOT NULL,
    paymentMethod VARCHAR(50) NULL,
    paymentType VARCHAR(40) NULL,
    reviewReason ENUM('NONE','AMOUNT_MISMATCH','CURRENCY_MISMATCH','COLLECTOR_MISMATCH',
        'MODE_MISMATCH','LATE_APPROVAL','DUPLICATE_PAYMENT','PARTIAL_REFUND',
        'CHARGEBACK','UNSUPPORTED_METHOD','INVALID_PROVIDER_STATE') NOT NULL,
    approvedAt DATETIME(6) NULL,
    providerUpdatedAt DATETIME(6) NOT NULL,
    verifiedAt DATETIME(6) NOT NULL,
    CONSTRAINT uk_mercadopago_payment UNIQUE (providerPaymentId),
    CONSTRAINT fk_payment_checkout FOREIGN KEY (checkout_reference)
        REFERENCES payment_checkouts(reference)
);

-- Upgrade tables which ddl-auto=update may already have created for Preferences API.
-- Keep any old preferenceId column/data; it is not a provider order ID.
SET @mp_schema_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'payment_checkouts' AND column_name = 'providerOrderId'),
    'SELECT 1',
    'ALTER TABLE payment_checkouts ADD COLUMN providerOrderId VARCHAR(64) NULL UNIQUE'
);
PREPARE mp_schema_statement FROM @mp_schema_sql;
EXECUTE mp_schema_statement;
DEALLOCATE PREPARE mp_schema_statement;

SET @mp_schema_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'payment_checkouts' AND column_name = 'requestBody'),
    'SELECT 1',
    'ALTER TABLE payment_checkouts ADD COLUMN requestBody LONGTEXT NULL'
);
PREPARE mp_schema_statement FROM @mp_schema_sql;
EXECUTE mp_schema_statement;
DEALLOCATE PREPARE mp_schema_statement;

ALTER TABLE payments MODIFY COLUMN providerPaymentId VARCHAR(64) NOT NULL;
ALTER TABLE payment_checkouts MODIFY COLUMN requestBody LONGTEXT NULL;

-- System-generated payment audit entries require actorUserId to allow NULL.
-- This is already part of 2026-11-08-order-expiration.sql.
