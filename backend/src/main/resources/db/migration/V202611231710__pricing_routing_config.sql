INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES
    ('TRUCK_TOP_SPEED_KMH', '60', 'COUNT', 'ROUTING', 'Tốc độ tối đa khi tính tuyến xe tải (km/h), số mẫu', 10, 120),
    ('TRUCK_WEIGHT_TONS', '10', 'COUNT', 'ROUTING', 'Tải trọng khi tính tuyến xe tải (tấn), số mẫu', 1, 60)
ON CONFLICT (config_key) DO NOTHING;
