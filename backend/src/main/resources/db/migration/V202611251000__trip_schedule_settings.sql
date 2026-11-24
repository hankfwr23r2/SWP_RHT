-- Tham số tính giờ lượt đón, giao và báo khách (người dùng duyệt 24/11/2026)
INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value) VALUES
    ('PICKUP_WINDOW_START_HOUR', '6', 'COUNT', 'SCHEDULE_WINDOW', 'Giờ sớm nhất xe đến đón', 0, 23),
    ('PICKUP_WINDOW_END_HOUR', '18', 'COUNT', 'SCHEDULE_WINDOW', 'Giờ muộn nhất ngựa tới hub trong ngày đón', 1, 24),
    ('DELIVERY_WINDOW_START_HOUR', '6', 'COUNT', 'SCHEDULE_WINDOW', 'Giờ sớm nhất xe đến giao', 0, 23),
    ('DELIVERY_WINDOW_END_HOUR', '18', 'COUNT', 'SCHEDULE_WINDOW', 'Giờ muộn nhất xe đến giao', 1, 24),
    ('STOP_BASE_MINUTES', '20', 'MINUTE', 'SCHEDULE_WINDOW', 'Thời gian dừng cố định ở mỗi điểm nhận hoặc giao ngựa', 5, 120),
    ('STOP_MINUTES_PER_HORSE', '10', 'MINUTE', 'SCHEDULE_WINDOW', 'Thời gian dừng thêm cho mỗi con', 1, 60),
    ('SCHEDULE_CHANGE_NOTICE_MINUTES', '30', 'MINUTE', 'SCHEDULE_WINDOW', 'Giờ đón, giao dời quá số phút này thì báo khách', 5, 240)
ON CONFLICT (config_key) DO NOTHING;
