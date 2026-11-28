ALTER TABLE trip_stop
    ADD COLUMN expected_arrival_at timestamptz,
    ADD COLUMN told_arrival_at timestamptz,
    ADD COLUMN told_at timestamptz,
    ADD CONSTRAINT ck_trip_stop_told_pair CHECK ((told_arrival_at IS NULL) = (told_at IS NULL));

ALTER TABLE vehicle_trip
    ADD COLUMN delivery_window_warned_at timestamptz,
    ADD COLUMN delivery_ride_warned_at timestamptz;

DELETE FROM system_config WHERE config_key = 'DELIVERY_NOTICE_WINDOW_MINUTES';
INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('DELIVERY_TOLERANCE_MINUTES', '30', 'MINUTE', 'HANDOVER', 'Dung sai trước và sau giờ giao đã báo', 10, 120);
