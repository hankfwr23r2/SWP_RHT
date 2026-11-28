-- Thông tin liên hệ hiện cho khách, quản trị viên sửa trên màn tham số (người dùng chốt 24/11/2026).
-- unit TEXT: giá trị dạng chữ, không có cận số.
INSERT INTO system_config (config_key, config_value, unit, config_group, description) VALUES
    ('HOTLINE', '1900 6868', 'TEXT', 'CONTACT', 'Số hotline hiện cho khách'),
    ('OFFICE_ADDRESS', 'Văn phòng Vận chuyển Ngựa, 120 Xô Viết Nghệ Tĩnh, TP.HCM', 'TEXT', 'CONTACT', 'Địa chỉ văn phòng hiện cho khách')
ON CONFLICT (config_key) DO NOTHING;
