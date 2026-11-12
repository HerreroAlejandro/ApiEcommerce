-- MySQL: run once before starting the application with the expiration feature.
-- Do not run against an instance already updated by Hibernate ddl-auto=update
-- without checking which schema changes are already present.
ALTER TABLE orders ADD COLUMN expiresAt DATETIME(6) NULL;
ALTER TABLE orders MODIFY COLUMN orderStatus
    ENUM('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELED', 'EXPIRED') NOT NULL;
ALTER TABLE order_change_audit MODIFY COLUMN actorUserId BIGINT NULL;

-- Use the same reservation period configured in ORDER_RESERVATION_MINUTES.
SET @reservation_minutes = 30;
UPDATE orders
SET expiresAt = TIMESTAMPADD(MINUTE, @reservation_minutes, purchaseDateOrder)
WHERE expiresAt IS NULL
  AND orderStatus = 'PENDING'
  AND paymentStatus IN ('PENDING', 'FAILED');

CREATE INDEX idx_orders_expiration ON orders (orderStatus, paymentStatus, expiresAt);
