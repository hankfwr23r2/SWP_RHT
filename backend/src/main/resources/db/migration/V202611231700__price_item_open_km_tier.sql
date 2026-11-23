ALTER TABLE price_item
    DROP CONSTRAINT ck_price_item_km_pair,
    ADD CONSTRAINT ck_price_item_km_pair CHECK (km_to IS NULL OR km_from IS NOT NULL);
