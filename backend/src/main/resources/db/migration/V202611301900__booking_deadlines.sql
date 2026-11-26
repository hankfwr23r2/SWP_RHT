INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('BOOKING_INTAKE_DUE_HOURS', '4', 'HOUR', 'BOOKING', 'Hạn nội bộ tiếp nhận từ lúc khách gửi đơn', 1, 168),
       ('BOOKING_REVIEW_DUE_HOURS', '24', 'HOUR', 'BOOKING', 'Hạn nội bộ thẩm định từ lúc đơn vào Đang thẩm định', 1, 168),
       ('BOOKING_QUOTE_DUE_HOURS', '24', 'HOUR', 'QUOTATION', 'Hạn nội bộ gửi báo giá từ lúc đơn vào Đang báo giá', 1, 168);

