ALTER TABLE horse_insurance
    ADD COLUMN insurer_name varchar(200);

UPDATE horse_insurance insurance
SET insurer_name = btrim(party.display_name)
FROM party
WHERE insurance.insurer_party_id = party.id;

ALTER TABLE horse_insurance
    DROP CONSTRAINT ck_horse_insurance_arranged,
    ADD CONSTRAINT ck_horse_insurance_arranged
        CHECK (status <> 'CONFIRMED' OR coverage_option <> 'ARRANGED'
            OR (insurer_name IS NOT NULL AND btrim(insurer_name) <> ''));

DROP INDEX ix_horse_insurance_insurer_party_id;

ALTER TABLE horse_insurance
    DROP CONSTRAINT fk_horse_insurance_insurer_party_id,
    DROP COLUMN insurer_party_id;
