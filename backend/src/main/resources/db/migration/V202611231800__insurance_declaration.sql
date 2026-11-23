ALTER TABLE horse_insurance
    DROP CONSTRAINT ck_horse_insurance_arranged,
    DROP CONSTRAINT ck_horse_insurance_coverage,
    ADD CONSTRAINT ck_horse_insurance_arranged
        CHECK (status <> 'CONFIRMED' OR coverage_option <> 'ARRANGED' OR insurer_party_id IS NOT NULL),
    ADD CONSTRAINT ck_horse_insurance_coverage
        CHECK (status <> 'CONFIRMED' OR coverage_option = 'NONE'
            OR (coverage_amount IS NOT NULL AND currency_code IS NOT NULL));
