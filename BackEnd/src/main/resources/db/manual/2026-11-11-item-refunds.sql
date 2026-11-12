-- Select the existing ecommerce schema. Run after refund-rejections/refund-attempts.
-- This script changes local records only; it sends no refunds to Mercado Pago.
ALTER TABLE orders MODIFY COLUMN paymentStatus
    ENUM('PENDING','PAID','FAILED','REFUNDED','REVIEW','PARTIALLY_REFUNDED') NOT NULL;

ALTER TABLE order_items
    ADD COLUMN refundRequestedQuantity INT NOT NULL DEFAULT 0,
    ADD COLUMN refundedQuantity INT NOT NULL DEFAULT 0,
    ADD COLUMN canceledQuantity INT NOT NULL DEFAULT 0;

ALTER TABLE payment_refunds ADD COLUMN itemized BOOLEAN NOT NULL DEFAULT FALSE;

-- Keep a non-unique index for the payment foreign key before removing the old unique key.
-- The unique key may have been named by Hibernate, so resolve its actual name.
CREATE INDEX idx_refund_payment_history ON payment_refunds(payment_id, requestedAt);
SET @refund_unique_index = (
    SELECT INDEX_NAME FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payment_refunds'
        AND NON_UNIQUE = 0 AND COLUMN_NAME = 'payment_id'
    LIMIT 1
);
SET @refund_drop_sql = IF(@refund_unique_index IS NULL, 'SELECT 1',
    CONCAT('ALTER TABLE payment_refunds DROP INDEX `', REPLACE(@refund_unique_index, '`', '``'), '`'));
PREPARE refund_drop_statement FROM @refund_drop_sql;
EXECUTE refund_drop_statement;
DEALLOCATE PREPARE refund_drop_statement;

CREATE TABLE IF NOT EXISTS refund_item_allocations (
    refund_id VARCHAR(36) NOT NULL,
    orderItemId BIGINT NOT NULL,
    quantity INT NOT NULL,
    unitPrice DECIMAL(10,2) NOT NULL,
    CONSTRAINT uk_refund_item UNIQUE (refund_id, orderItemId),
    CONSTRAINT fk_refund_allocation FOREIGN KEY (refund_id) REFERENCES payment_refunds(id),
    CONSTRAINT fk_refund_order_item FOREIGN KEY (orderItemId) REFERENCES order_items(idOrderItem)
);

-- Preserve historical fully refunded orders in the item counters too.
UPDATE order_items i JOIN orders o ON o.idOrder = i.order_id
SET i.refundRequestedQuantity = i.amountOrderItem,
    i.refundedQuantity = i.amountOrderItem,
    i.canceledQuantity = IF(o.orderStatus = 'CANCELED', i.amountOrderItem, 0)
WHERE o.paymentStatus = 'REFUNDED';

-- If Hibernate already created a column/index, execute only missing DDL.
-- Do not omit removing UNIQUE(payment_id): multiple sequential refunds now require it.
