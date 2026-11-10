-- MySQL. Select the existing ecommerce database before running this script.
-- Existing orders/payments are retained. This creates no refunds in Mercado Pago.
CREATE TABLE IF NOT EXISTS payment_refunds (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    version BIGINT NULL,
    payment_id BIGINT NOT NULL,
    actorUserId BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    amount DECIMAL(16,2) NOT NULL,
    previouslyRefundedAmount DECIMAL(16,2) NOT NULL,
    providerOrderId VARCHAR(64) NOT NULL,
    status ENUM('REQUESTED', 'PROCESSING', 'CONFIRMED') NOT NULL,
    providerRefundId VARCHAR(64) NULL,
    requestedAt DATETIME(6) NOT NULL,
    confirmedAt DATETIME(6) NULL,
    CONSTRAINT uk_refund_payment UNIQUE (payment_id),
    CONSTRAINT uk_refund_provider UNIQUE (providerRefundId),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);
