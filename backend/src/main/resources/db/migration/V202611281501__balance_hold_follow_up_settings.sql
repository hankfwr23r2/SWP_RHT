INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES
    ('STABLING_REMINDER_HOUR', '8', 'HOUR', 'HANDOVER', 'Giờ nhắc chăm sóc và phí giữ hằng ngày tại hub', 0, 23),
    ('BALANCE_HOLD_MIN_CONTACTS', '3', 'COUNT', 'HANDOVER', 'Số lần điều phối liên hệ tối thiểu trước khi tự hủy đơn giữ ngựa', 3, 10);
