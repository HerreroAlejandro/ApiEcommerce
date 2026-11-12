-- Existing ecommerce schema. Requires payment-refunds and refund-rejections migrations.
-- Inspect the schema first if Hibernate ddl-auto=update already created any columns/table.
ALTER TABLE payment_refunds
    ADD COLUMN currentAttemptId VARCHAR(36) NULL,
    ADD COLUMN attemptNumber INT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS payment_refund_attempts (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    version BIGINT NULL,
    refund_id VARCHAR(36) NOT NULL,
    attemptNumber INT NOT NULL,
    previousAttemptId VARCHAR(36) NULL,
    actorUserId BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requestedAt DATETIME(6) NOT NULL,
    status ENUM('REQUESTED', 'PROCESSING', 'CONFIRMED', 'REJECTED') NOT NULL,
    providerRefundId VARCHAR(64) NULL,
    confirmedAt DATETIME(6) NULL,
    rejectedAt DATETIME(6) NULL,
    rejectionCode VARCHAR(64) NULL,
    rejectionReason VARCHAR(500) NULL,
    CONSTRAINT uk_refund_attempt_number UNIQUE (refund_id, attemptNumber),
    CONSTRAINT uk_refund_attempt_previous UNIQUE (previousAttemptId),
    CONSTRAINT uk_refund_attempt_provider UNIQUE (providerRefundId),
    CONSTRAINT fk_refund_attempt_request FOREIGN KEY (refund_id) REFERENCES payment_refunds(id)
);

-- Preserve historical UUIDs as the first attempt's idempotency key. Never replace them.
INSERT INTO payment_refund_attempts
    (id, refund_id, attemptNumber, actorUserId, reason, requestedAt, status,
     providerRefundId, confirmedAt, rejectedAt, rejectionCode, rejectionReason)
SELECT r.id, r.id, 1, r.actorUserId, r.reason, r.requestedAt,
       CASE WHEN r.rejectedAt IS NOT NULL THEN 'REJECTED' ELSE r.status END,
       r.providerRefundId,
       CASE WHEN r.rejectedAt IS NOT NULL THEN NULL ELSE r.confirmedAt END,
       r.rejectedAt, r.rejectionCode, r.rejectionReason
FROM payment_refunds r
WHERE NOT EXISTS (SELECT 1 FROM payment_refund_attempts a WHERE a.refund_id = r.id);

UPDATE payment_refunds SET currentAttemptId = id WHERE currentAttemptId IS NULL;

-- No provider call, money transfer, stock change or new refund is performed.
