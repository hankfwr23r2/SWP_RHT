ALTER TABLE incident ADD COLUMN severity varchar(10);

UPDATE incident
SET severity = CASE incident_type
    WHEN 'HEALTH' THEN 'HIGH'
    WHEN 'ACCIDENT' THEN 'HIGH'
    WHEN 'VEHICLE_BREAKDOWN' THEN 'MEDIUM'
    WHEN 'HANDOVER_TIMEOUT' THEN 'MEDIUM'
    WHEN 'DOCUMENT' THEN 'MEDIUM'
    WHEN 'DELAY' THEN 'LOW'
    WHEN 'OTHER' THEN 'LOW'
    WHEN 'COMPLAINT' THEN 'LOW'
END;

ALTER TABLE incident
    ALTER COLUMN severity SET NOT NULL,
    ADD CONSTRAINT ck_incident_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    ADD COLUMN trip_unlocked_at timestamptz,
    ADD COLUMN trip_unlocked_by bigint REFERENCES staff (user_id),
    ADD COLUMN trip_unlock_reason varchar(1000),
    ADD CONSTRAINT ck_incident_trip_unlock CHECK (
        (trip_unlocked_at IS NULL AND trip_unlocked_by IS NULL AND trip_unlock_reason IS NULL)
        OR (trip_unlocked_at IS NOT NULL AND trip_unlocked_by IS NOT NULL
            AND trip_unlock_reason IS NOT NULL AND trip_unlock_reason ~ '[^[:space:]]')
    );
