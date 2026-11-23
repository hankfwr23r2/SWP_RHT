ALTER TABLE country_rule DROP COLUMN quarantine_days;
ALTER TABLE vehicle DROP COLUMN registration_expiry_date;

ALTER TABLE location
    ADD COLUMN counterpart_country_code char(2) REFERENCES country(code),
    ADD CONSTRAINT ck_location_counterpart_country CHECK (
        counterpart_country_code IS NULL OR location_type = 'BORDER_GATE'
    );
CREATE INDEX ix_location_counterpart_country_code ON location (counterpart_country_code);
UPDATE location SET counterpart_country_code = 'KH'
WHERE code = 'GATE-MOCBAI' AND location_type = 'BORDER_GATE';

-- Cách đưa ngựa tới hub và cách nhận ngựa ở hub: công ty chở hoặc khách tự chở
ALTER TABLE booking
    ADD COLUMN pickup_method varchar(30) NOT NULL DEFAULT 'COMPANY',
    ADD COLUMN delivery_method varchar(30) NOT NULL DEFAULT 'COMPANY',
    ADD CONSTRAINT ck_booking_pickup_method CHECK (pickup_method IN ('COMPANY', 'SELF')),
    ADD CONSTRAINT ck_booking_delivery_method CHECK (delivery_method IN ('COMPANY', 'SELF'));

INSERT INTO document_type (code, name, owner_type)
VALUES
    ('QUARANTINE_CLEARANCE', 'Xác nhận ngựa đạt điều kiện cách ly trước khi vận chuyển', 'BOOKING_HORSE'),
    ('ORIGIN_QUARANTINE_CERT', 'Giấy kiểm dịch của nước xuất', 'BOOKING_HORSE'),
    ('IMPORT_APPROVAL', 'Văn bản đồng ý và hướng dẫn kiểm dịch', 'BOOKING_HORSE'),
    ('IMPORT_TRANSPORT_CERT', 'Giấy vận chuyển động vật nhập khẩu', 'BOOKING_HORSE')
ON CONFLICT DO NOTHING;

-- booked_stall_count đổi từ số ngựa sang số chỗ: mỗi ngựa khoang rộng đang giữ chỗ cộng thêm 1.
-- Tới phiên bản này chỉ đơn REJECTED đã nhả chỗ. Vượt sức chứa thì CHECK của departure chặn, phải đổi chuyến trước khi nâng cấp.
UPDATE departure d
SET booked_stall_count = d.booked_stall_count + w.wide_horses
FROM (SELECT b.departure_id, count(*) AS wide_horses
      FROM booking b
      JOIN booking_horse bh ON bh.booking_id = b.id
      WHERE b.departure_id IS NOT NULL AND b.status <> 'REJECTED'
        AND bh.status = 'ACTIVE' AND bh.stall_type_code = 'WIDE'
      GROUP BY b.departure_id) w
WHERE d.id = w.departure_id;

UPDATE billable_service SET name = 'Thú y tại chuồng: khám và lấy mẫu' WHERE code = 'VET_CHECK';
UPDATE billable_service SET name = 'Lưu chuồng theo ngày' WHERE code = 'QUARANTINE_STAY';

DELETE FROM system_config WHERE config_key = 'BALANCE_DUE_DAYS_BEFORE_PICKUP';

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES
    ('DEPARTURE_DECISION_HOURS', '48', 'HOUR', 'DEPARTURE', 'Mốc quyết định chạy trước giờ xuất phát', 1, 168),
    ('DEPARTURE_MIN_FILL_PERCENT', '30', 'COUNT', 'DEPARTURE', 'Ngưỡng phần trăm số chỗ đã cọc', 1, 100),
    ('HUB_CHECKIN_HOURS', '3', 'HOUR', 'DEPARTURE', 'Thời gian ngựa phải tới hub trước giờ xuất phát', 1, 24),
    ('BALANCE_WAIT_MINUTES', '30', 'MINUTE', 'PAYMENT', 'Thời gian chờ trả số dư sau khi nhận ngựa', 5, 240),
    ('MAX_CONTINUOUS_HOURS', '4', 'HOUR', 'ROUTING', 'Thời gian chạy liên tục tối đa', 1, 24),
    ('MIN_REST_MINUTES', '30', 'MINUTE', 'ROUTING', 'Thời gian nghỉ tối thiểu ở trạm', 1, 1440),
    ('MAX_DRIVE_HOURS_PER_DAY', '10', 'HOUR', 'ROUTING', 'Thời gian lái tối đa mỗi ngày', 1, 24),
    ('OVERNIGHT_REST_HOURS', '10', 'HOUR', 'ROUTING', 'Thời gian nghỉ đêm tối thiểu', 1, 24),
    ('DOCUMENT_REMINDER_DAYS', '5', 'DAY', 'REGULATION', 'Nhắc thiếu giấy trước chuyến', 1, 30),
    ('PRICE_HOLD_DAYS', '30', 'DAY', 'PRICING', 'Thời gian giữ giá khi đổi chuyến', 1, 90),
    ('SETTLEMENT_AUTO_CLOSE_DAYS', '7', 'DAY', 'PAYMENT', 'Thời gian tự đóng đơn không phát sinh', 1, 30)
ON CONFLICT DO NOTHING;
