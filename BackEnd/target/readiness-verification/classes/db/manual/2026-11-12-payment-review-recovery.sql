-- Select the existing ecommerce schema. Inspect it first if Hibernate already added the column.
-- If acceptedAt already exists, skip ALTER but still run the conservative backfill below.
ALTER TABLE payments ADD COLUMN acceptedAt DATETIME(6) NULL;

-- Backfill only purchases currently accepted as paid by this application.
-- A provider payment binding alone is insufficient evidence for a legacy REVIEW.
UPDATE payments p JOIN payment_checkouts c ON c.reference = p.checkout_reference
JOIN orders o ON o.idOrder = c.order_id AND o.providerPaymentId = p.providerPaymentId
SET p.acceptedAt = p.verifiedAt
WHERE p.acceptedAt IS NULL
  AND o.salesChannel = 'ONLINE'
  AND o.paymentStatus IN ('PAID', 'PARTIALLY_REFUNDED')
  AND o.orderStatus IN ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED')
  AND o.stockDeducted = TRUE
  AND p.providerStatus = 'approved' AND p.reviewReason = 'NONE'
  AND p.collectorId = c.collectorId AND p.currency = c.currency
  AND p.liveMode = c.liveMode AND p.amount = c.amount AND c.amount = o.totalOrder;

-- Historical REVIEW rows without evidence remain blocked, never forced to PAID.
-- This script sends no payments/refunds and changes no stock or download attempts.
