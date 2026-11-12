-- Select the existing ecommerce schema. Apply before starting the updated backend.
-- If ddl-auto=update already created columns, inspect SHOW CREATE TABLE orders first
-- and execute only the missing changes. Do not recreate the schema.
ALTER TABLE orders
    ADD COLUMN salesChannel ENUM('ONLINE', 'IN_STORE') NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN deliveryMethod ENUM('SHIPPING', 'STORE_PICKUP', 'DIGITAL_DOWNLOAD') NOT NULL DEFAULT 'SHIPPING',
    ADD COLUMN storeSaleReference VARCHAR(36) NULL,
    ADD COLUMN storeSaleRequestHash VARCHAR(64) NULL,
    ADD COLUMN storePaymentMethod ENUM('CASH', 'CARD_TERMINAL') NULL,
    ADD COLUMN storePaymentReference VARCHAR(100) NULL,
    ADD COLUMN storePaidAt DATETIME(6) NULL,
    ADD COLUMN storeCashierUserId BIGINT NULL,
    ADD COLUMN storeCustomerName VARCHAR(100) NULL,
    ADD COLUMN storeCustomerPhone VARCHAR(25) NULL,
    ADD COLUMN storeDeliveredAt DATETIME(6) NULL,
    ADD COLUMN storeRefundedAt DATETIME(6) NULL,
    ADD COLUMN storeRefundedBy BIGINT NULL,
    ADD COLUMN storeRefundReason VARCHAR(500) NULL,
    ADD CONSTRAINT uk_orders_store_reference UNIQUE (storeSaleReference);

-- No existing user relation, payment, historical price or stock quantity is changed.

-- Classify historical digital-only online orders without changing fulfillment/payment history.
UPDATE orders o
SET o.deliveryMethod = 'DIGITAL_DOWNLOAD'
WHERE o.salesChannel = 'ONLINE'
  AND EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.idOrder)
  AND NOT EXISTS (
      SELECT 1 FROM order_items i JOIN products p ON p.idProduct = i.product_id
      WHERE i.order_id = o.idOrder AND p.type = 'PHYSICAL'
  );
