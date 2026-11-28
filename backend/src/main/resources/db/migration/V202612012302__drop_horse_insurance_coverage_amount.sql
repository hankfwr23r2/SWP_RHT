ALTER TABLE horse_insurance
    DROP CONSTRAINT ck_horse_insurance_coverage_amount,
    DROP CONSTRAINT ck_horse_insurance_coverage,
    ADD CONSTRAINT ck_horse_insurance_coverage
        CHECK (status <> 'CONFIRMED' OR coverage_option <> 'ARRANGED' OR currency_code IS NOT NULL),
    DROP COLUMN coverage_amount;
