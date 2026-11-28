ALTER TABLE vehicle_trip ADD COLUMN return_late_notified_at timestamptz;

INSERT INTO document_type (code, name, owner_type)
VALUES ('VEHICLE_RETURN_PHOTO', 'Ảnh xe về hub', 'VEHICLE_TRIP');

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('RETURN_LATE_MINUTES', '30', 'MINUTE', 'SCHEDULE_WINDOW', 'Thời gian ân hạn khi xe về hub cuối của lượt', 1, 240);
