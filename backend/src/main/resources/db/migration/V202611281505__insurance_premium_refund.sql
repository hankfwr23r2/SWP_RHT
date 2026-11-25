ALTER TABLE horse_insurance ADD COLUMN refunded_premium_amount numeric(15,2);
ALTER TABLE horse_insurance ADD CONSTRAINT ck_horse_insurance_refunded_premium
    CHECK (refunded_premium_amount IS NULL OR
        (premium_amount IS NOT NULL AND refunded_premium_amount > 0 AND refunded_premium_amount <= premium_amount));
