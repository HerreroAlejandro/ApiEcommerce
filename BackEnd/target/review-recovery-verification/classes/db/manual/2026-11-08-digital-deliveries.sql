-- MySQL: inspect existing schema before applying, especially if Hibernate has
-- already created these columns/tables with ddl-auto=update. Run once.
ALTER TABLE order_items ADD COLUMN digitalDownloadLink VARCHAR(200) NULL;

CREATE TABLE digital_deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NULL,
    order_item_id BIGINT NOT NULL,
    sourceUrl VARCHAR(200) NOT NULL,
    status ENUM('AVAILABLE', 'USED', 'REVOKED') NOT NULL,
    enabledAt DATETIME(6) NOT NULL,
    attemptedAt DATETIME(6) NULL,
    revokedAt DATETIME(6) NULL,
    attemptCount INT NOT NULL DEFAULT 0,
    tokenHash VARCHAR(64) NULL,
    tokenExpiresAt DATETIME(6) NULL,
    CONSTRAINT uk_digital_delivery_item UNIQUE (order_item_id),
    CONSTRAINT fk_digital_delivery_item FOREIGN KEY (order_item_id)
        REFERENCES order_items (idOrderItem)
);

-- OPTIONAL LEGACY BACKFILL: review the actual purchased content before running.
-- Historical URLs cannot be reconstructed from the current catalog. Do not
-- automatically grant another attempt to clients who already received a file.
-- For a reviewed order, set its item snapshot explicitly:
-- UPDATE order_items SET digitalDownloadLink = 'https://trusted-host/file.zip'
-- WHERE idOrderItem = <reviewed_item_id>;
-- For a PAID order, submit the existing administrative PAID status again after
-- snapshots are populated to initialize any missing deliveries idempotently.
