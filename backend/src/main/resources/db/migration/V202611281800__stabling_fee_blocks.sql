-- Phí giữ theo số lần đủ 24 giờ (PHUONG_AN_6_LUONG mục 13.2)
ALTER TABLE horse_stay ADD COLUMN stabling_unit_price numeric(15,2);
ALTER TABLE horse_stay ADD CONSTRAINT ck_horse_stay_stabling_unit_price
    CHECK (stabling_unit_price IS NULL OR (stabling_unit_price > 0 AND stay_type = 'STABLING'));

UPDATE system_config SET description = 'Phí giữ ngựa cho mỗi 24 giờ trọn vẹn' WHERE config_key = 'STABLING_DAILY_FEE';

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('STABLING_OPEN_HOUR', '6', 'HOUR', 'HANDOVER', 'Giờ hub mở cửa nhận, trả ngựa', 0, 12),
       ('STABLING_CLOSE_HOUR', '18', 'HOUR', 'HANDOVER', 'Giờ hub đóng cửa nhận, trả ngựa', 13, 23),
       ('STABLING_MORNING_HOUR', '8', 'HOUR', 'HANDOVER', 'Mốc tính phí khi đủ 24 giờ ngoài giờ mở cửa', 0, 12),
       ('STABLING_MAX_BLOCKS', '30', 'COUNT', 'HANDOVER', 'Số lần 24 giờ tối đa tính phí giữ', 1, 365),
       ('STABLING_WARNING_HOURS', '3', 'HOUR', 'HANDOVER', 'Báo trước mốc tính phí giữ kế tiếp', 1, 23),
       ('DELIVERY_NOTICE_WINDOW_MINUTES', '60', 'MINUTE', 'HANDOVER', 'Khung giờ giao đã báo tính từ giờ tới dự kiến', 15, 240);
