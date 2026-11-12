-- MySQL: run once before starting this version, checking the current schema
-- first if Hibernate ddl-auto=update has already created any of these columns.
ALTER TABLE orders
    ADD COLUMN itemsSubtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN shippingCost DECIMAL(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN shippingRecipientName VARCHAR(100) NULL,
    ADD COLUMN shippingPhone VARCHAR(25) NULL,
    ADD COLUMN shippingStreet VARCHAR(120) NULL,
    ADD COLUMN shippingStreetNumber VARCHAR(20) NULL,
    ADD COLUMN shippingApartment VARCHAR(80) NULL,
    ADD COLUMN shippingCity VARCHAR(80) NULL,
    ADD COLUMN shippingRegion VARCHAR(80) NULL,
    ADD COLUMN shippingPostalCode VARCHAR(20) NULL,
    ADD COLUMN shippingCountryCode VARCHAR(2) NULL,
    ADD COLUMN carrier VARCHAR(60) NULL,
    ADD COLUMN trackingNumber VARCHAR(80) NULL,
    ADD COLUMN trackingUrl VARCHAR(200) NULL,
    ADD COLUMN shippedAt DATETIME(6) NULL,
    ADD COLUMN deliveredAt DATETIME(6) NULL;

-- Previous totals included products only. Preserve every historical total.
UPDATE orders SET itemsSubtotal = totalOrder WHERE shippingCost = 0;

-- Historical addresses cannot be inferred from the current user profile.
-- Review/backfill actual shipping details for legacy physical orders that
-- still need to be shipped. Never populate addresses with guessed values.
