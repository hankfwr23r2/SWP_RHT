ALTER TABLE booking ADD COLUMN transport_mode varchar(30) NOT NULL DEFAULT 'SHARED'
    CHECK (transport_mode IN ('SHARED', 'PRIVATE'));

ALTER TABLE departure_vehicle
    ADD COLUMN booking_id bigint REFERENCES booking(id),
    DROP CONSTRAINT departure_vehicle_allotment_type_check,
    ADD CONSTRAINT departure_vehicle_allotment_type_check CHECK (allotment_type IN ('BASE', 'SPARE', 'PRIVATE')),
    ADD CONSTRAINT departure_vehicle_private_booking_check CHECK (
        (allotment_type = 'PRIVATE' AND booking_id IS NOT NULL AND vehicle_count = 1)
        OR (allotment_type IN ('BASE', 'SPARE') AND booking_id IS NULL)
    );

CREATE UNIQUE INDEX departure_vehicle_booking_idx ON departure_vehicle(booking_id) WHERE booking_id IS NOT NULL;
