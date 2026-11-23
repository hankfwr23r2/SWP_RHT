ALTER TABLE location ADD COLUMN is_headquarters boolean NOT NULL DEFAULT false;
ALTER TABLE location ADD CONSTRAINT ck_location_headquarters_type
    CHECK (NOT is_headquarters OR location_type = 'HUB');
CREATE UNIQUE INDEX uk_location_headquarters ON location (is_headquarters) WHERE is_headquarters;

DO $$
DECLARE
    hub_count bigint;
    headquarters_id bigint;
BEGIN
    SELECT count(*), min(hub_id) INTO hub_count, headquarters_id
    FROM (
        SELECT home_hub_location_id AS hub_id FROM vehicle
        UNION
        SELECT home_hub_location_id FROM staff WHERE home_hub_location_id IS NOT NULL
    ) AS home_hubs;

    IF hub_count > 1 THEN
        RAISE EXCEPTION 'Cannot infer headquarters: vehicles and staff have multiple home hubs';
    END IF;

    IF headquarters_id IS NOT NULL THEN
        UPDATE location SET is_headquarters = true WHERE id = headquarters_id;
    END IF;
END $$;
