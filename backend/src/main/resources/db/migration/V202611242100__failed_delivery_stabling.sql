-- Giao thất bại (B5): ngựa về hub được giữ và chăm sóc tối đa vài ngày, phí theo ngày do người nhận chịu
INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES
    ('STABLING_MAX_DAYS', '3', 'DAY', 'HANDOVER', 'Số ngày giữ ngựa tại hub sau khi giao thất bại', 1, 30),
    ('STABLING_DAILY_FEE', '1000000', 'VND', 'HANDOVER', 'Phí lưu chuồng và chăm sóc mỗi ngày khi giữ ngựa giao thất bại', 1, 100000000)
ON CONFLICT DO NOTHING;
