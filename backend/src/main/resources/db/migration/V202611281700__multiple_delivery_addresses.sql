ALTER TABLE booking_address
    DROP CONSTRAINT uk_booking_address_booking_address_type,
    ADD CONSTRAINT uk_booking_address_booking_id UNIQUE (booking_id, id);

CREATE UNIQUE INDEX uk_booking_address_pickup
    ON booking_address (booking_id) WHERE address_type = 'PICKUP';

ALTER TABLE booking_horse
    ADD COLUMN delivery_address_id bigint,
    ADD CONSTRAINT fk_booking_horse_delivery_address
        FOREIGN KEY (booking_id, delivery_address_id)
        REFERENCES booking_address (booking_id, id);

UPDATE booking_horse horse
SET delivery_address_id = address.id
FROM booking_address address
WHERE address.booking_id = horse.booking_id AND address.address_type = 'DELIVERY';

CREATE INDEX ix_booking_horse_delivery_address
    ON booking_horse (booking_id, delivery_address_id);

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES
    ('MAX_DELIVERY_PLACES', '3', NULL, 'ROUTING', 'Maximum delivery destinations per booking', 1, 5),
    ('INTERMEDIATE_WAIT_MINUTES', '60', 'MINUTE', 'HANDOVER', 'Maximum wait at an intermediate delivery stop', 1, 1440);
