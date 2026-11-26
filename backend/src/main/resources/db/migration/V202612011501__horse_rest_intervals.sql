INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES
    ('MAX_TRANSFER_HORSE_HOURS', '8', 'HOUR', 'ROUTING', 'Giờ ngựa ngồi xe tối đa trên chặng đón hoặc giao', 1, 8),
    ('MAX_DIRECT_HORSE_HOURS', '10', 'HOUR', 'ROUTING', 'Giờ ngựa ngồi xe tối đa trên chặng chạy thẳng', 1, 10),
    ('HORSE_REST_INTERVAL_HOURS', '4', 'HOUR', 'ROUTING', 'Giờ ngựa ngồi xe liên tục tối đa trước trạm nghỉ', 1, 8);
