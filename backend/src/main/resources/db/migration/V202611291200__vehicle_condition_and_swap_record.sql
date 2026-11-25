ALTER TABLE vehicle_trip
    ADD COLUMN end_vehicle_condition varchar(30),
    ADD COLUMN end_vehicle_note varchar(1000),
    ADD CONSTRAINT ck_vehicle_trip_end_vehicle_condition CHECK (
        end_vehicle_condition IS NULL
        OR (status = 'COMPLETED' AND end_vehicle_condition IN ('NORMAL', 'NEEDS_MAINTENANCE'))
    ),
    ADD CONSTRAINT ck_vehicle_trip_end_vehicle_note CHECK (
        (end_vehicle_note IS NULL OR end_vehicle_condition IS NOT NULL)
        AND (end_vehicle_condition IS DISTINCT FROM 'NEEDS_MAINTENANCE'
             OR (end_vehicle_note IS NOT NULL AND end_vehicle_note ~ '[^[:space:]]'))
    );

INSERT INTO document_type (code, name, owner_type)
VALUES ('VEHICLE_SWAP_RECORD', 'Biên bản đổi xe', 'INCIDENT');
