-- Dữ liệu MẪU cho máy dev và test. KHÔNG chạy trên môi trường thật.
-- Chỉ nạp khi FLYWAY_LOCATIONS có classpath:db/seed-dev (docker-compose và test đã bật sẵn).
-- Repeatable migration: Flyway chạy file này sau mọi migration có số, và chạy lại khi nội dung đổi,
-- nên mọi câu lệnh phải idempotent (ON CONFLICT hoặc WHERE NOT EXISTS).
--
-- Tọa độ, khoảng cách, đơn giá, quy định ở đây là SỐ MẪU để phát triển và demo, chưa phải dữ liệu thật.
-- Dữ liệu nền đầy đủ sẽ bổ sung riêng (xem docs/NGU_CANH_THIET_KE_LAI.md, câu hỏi còn mở).
--
-- Tài khoản thử: mọi tài khoản dưới đây dùng chung mật khẩu thử DevSeed@2026 (chỉ dùng cho dev/test).
--   admin.dev, manager.dev, coordinator.dev, specialist.dev, driver1.dev … driver41.dev, escort1.dev … escort41.dev, customer1.dev

-- ---------------------------------------------------------------------------
-- 1. Điểm mạng lưới và vùng phục vụ
-- ---------------------------------------------------------------------------
INSERT INTO location (code, name, location_type, country_code, province_code, address, latitude, longitude, opening_time, closing_time, contact_phone, status) VALUES
    ('HUB-HCM', 'Trụ sở TP.HCM', 'HUB', 'VN', 'VN-79', 'Phường Long Trường, TP.HCM', 10.805000, 106.815000, NULL, NULL, '0900000100', 'ACTIVE'),
    ('HUB-DN', 'Hub vệ tinh Đà Nẵng', 'HUB', 'VN', 'VN-48', 'Phường Hòa Khánh, TP. Đà Nẵng', 16.075000, 108.150000, NULL, NULL, '0900000200', 'ACTIVE'),
    ('REST-TRANGBANG', 'Trạm nghỉ Trảng Bàng', 'REST_STOP', 'VN', 'VN-80', 'Phường Trảng Bàng, Tây Ninh', 11.033000, 106.360000, NULL, NULL, NULL, 'ACTIVE'),
    ('GATE-MOCBAI', 'Cửa khẩu quốc tế Mộc Bài - Bavet', 'BORDER_GATE', 'VN', 'VN-80', 'Xã Bến Cầu, Tây Ninh', 11.070000, 106.200000, TIME '07:00', TIME '22:00', NULL, 'ACTIVE'),
    ('REST-PHANTHIET', 'Trạm nghỉ Phan Thiết', 'REST_STOP', 'VN', 'VN-68', 'Phường Phan Thiết, Lâm Đồng', 10.933000, 108.100000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-NHATRANG', 'Trạm nghỉ Nha Trang', 'REST_STOP', 'VN', 'VN-56', 'Phường Nha Trang, Khánh Hòa', 12.238000, 109.196000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-QUYNHON', 'Trạm nghỉ Quy Nhơn', 'REST_STOP', 'VN', 'VN-52', 'Phường Quy Nhơn, Gia Lai', 13.776000, 109.224000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-PHANRANG', 'Trạm nghỉ Phan Rang', 'REST_STOP', 'VN', 'VN-56', 'Phường Phan Rang, Khánh Hòa', 11.560000, 108.990000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-TUYHOA', 'Trạm nghỉ Tuy Hòa', 'REST_STOP', 'VN', 'VN-66', 'Phường Tuy Hòa, Đắk Lắk', 13.100000, 109.300000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-QUANGNGAI', 'Trạm nghỉ Quảng Ngãi', 'REST_STOP', 'VN', 'VN-51', 'Phường Nghĩa Lộ, Quảng Ngãi', 15.120000, 108.800000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-HUE', 'Trạm nghỉ Huế', 'REST_STOP', 'VN', 'VN-46', 'Phường Thuận Hóa, TP. Huế', 16.460000, 107.600000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-DONGHA', 'Trạm nghỉ Đông Hà', 'REST_STOP', 'VN', 'VN-44', 'Phường Đông Hà, Quảng Trị', 16.820000, 107.100000, NULL, NULL, NULL, 'ACTIVE'),
    ('HUB-HN', 'Hub vệ tinh Hà Nội', 'HUB', 'VN', 'VN-1', 'Xã Gia Lâm, TP. Hà Nội', 21.020000, 105.950000, NULL, NULL, '0900000300', 'ACTIVE'),
    ('REST-DONGHOI', 'Trạm nghỉ Đồng Hới', 'REST_STOP', 'VN', 'VN-44', 'Phường Đồng Hới, Quảng Trị', 17.465000, 106.600000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-VINH', 'Trạm nghỉ Vinh', 'REST_STOP', 'VN', 'VN-40', 'Phường Thành Vinh, Nghệ An', 18.680000, 105.680000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-THANHHOA', 'Trạm nghỉ Thanh Hóa', 'REST_STOP', 'VN', 'VN-38', 'Phường Hạc Thành, Thanh Hóa', 19.800000, 105.770000, NULL, NULL, NULL, 'ACTIVE'),
    ('GATE-LAOBAO', 'Cửa khẩu quốc tế Lao Bảo - Densavanh', 'BORDER_GATE', 'VN', 'VN-44', 'Xã Lao Bảo, Quảng Trị', 16.620000, 106.600000, TIME '07:00', TIME '22:00', NULL, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- Trường đua và câu lạc bộ ngựa làm nơi đón, giao quen thuộc. Tên và tọa độ là SỐ MẪU, chưa kiểm chứng
INSERT INTO location (code, name, location_type, country_code, province_code, address, latitude, longitude, opening_time, closing_time, contact_phone, status) VALUES
    ('TRACK-DAINAM', 'Trường đua Đại Nam (mẫu)', 'RACETRACK', 'VN', 'VN-79', 'Phường Hiệp An, TP.HCM', 11.050000, 106.630000, TIME '06:00', TIME '18:00', '0900000401', 'ACTIVE'),
    ('TRACK-SAIGONRIDERS', 'CLB Cưỡi ngựa Sài Gòn (mẫu)', 'RACETRACK', 'VN', 'VN-79', 'Phường An Khánh, TP.HCM', 10.800000, 106.740000, TIME '06:00', TIME '18:00', '0900000402', 'ACTIVE'),
    ('TRACK-SOCSON', 'Trường đua Sóc Sơn (mẫu)', 'RACETRACK', 'VN', 'VN-1', 'Xã Sóc Sơn, TP. Hà Nội', 21.250000, 105.850000, TIME '06:00', TIME '18:00', '0900000403', 'ACTIVE'),
    ('TRACK-DALAT', 'CLB Ngựa Đà Lạt (mẫu)', 'RACETRACK', 'VN', 'VN-68', 'Phường Xuân Hương - Đà Lạt, Lâm Đồng', 11.940000, 108.440000, TIME '07:00', TIME '17:00', '0900000404', 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- Điểm mạng lưới bổ sung (mẫu)
INSERT INTO location (code, name, location_type, country_code, province_code, address, latitude, longitude,
                      opening_time, closing_time, counterpart_country_code, is_headquarters, status)
VALUES
    ('HUB-NT', 'Hub Nha Trang', 'HUB', 'VN', 'VN-56', 'Hub Nha Trang (mẫu)', 12.27, 109.13, NULL, NULL, NULL, false, 'ACTIVE'),
    ('HUB-VINH', 'Hub Vinh', 'HUB', 'VN', 'VN-40', 'Hub Vinh (mẫu)', 18.72, 105.64, NULL, NULL, NULL, false, 'ACTIVE'),
    ('HUB-PNH', 'Hub Phnom Penh', 'HUB', 'KH', 'KH-12', 'Hub Phnom Penh (mẫu)', 11.56, 104.86, NULL, NULL, NULL, false, 'ACTIVE'),
    ('HUB-VTE', 'Hub Viêng Chăn', 'HUB', 'LA', 'LA-VT', 'Hub Viêng Chăn (mẫu)', 17.99, 102.6, NULL, NULL, NULL, false, 'ACTIVE'),
    ('HUB-SVK', 'Hub Savannakhet', 'HUB', 'LA', 'LA-SV', 'Hub Savannakhet (mẫu)', 16.57, 104.77, NULL, NULL, NULL, false, 'ACTIVE'),
    ('GATE-CAUTREO', 'Cửa khẩu quốc tế Cầu Treo - Nam Phao', 'BORDER_GATE', 'VN', 'VN-42', 'Cửa khẩu quốc tế Cầu Treo - Nam Phao (mẫu)', 18.388, 105.161, '07:00', '22:00', 'LA', false, 'ACTIVE'),
    ('GATE-TINHBIEN', 'Cửa khẩu quốc tế Tịnh Biên - Phnom Den', 'BORDER_GATE', 'VN', 'VN-91', 'Cửa khẩu quốc tế Tịnh Biên - Phnom Den (mẫu)', 10.598, 104.945, '07:00', '22:00', 'KH', false, 'ACTIVE'),
    ('GATE-HATIEN', 'Cửa khẩu quốc tế Hà Tiên - Prek Chak', 'BORDER_GATE', 'VN', 'VN-91', 'Cửa khẩu quốc tế Hà Tiên - Prek Chak (mẫu)', 10.426, 104.457, '07:00', '22:00', 'KH', false, 'ACTIVE'),
    ('GATE-TAYTRANG', 'Cửa khẩu quốc tế Tây Trang - Sop Hun', 'BORDER_GATE', 'VN', 'VN-11', 'Cửa khẩu quốc tế Tây Trang - Sop Hun (mẫu)', 21.22, 102.92, '07:00', '22:00', 'LA', false, 'ACTIVE'),
    ('REST-SVAYRIENG', 'Trạm nghỉ Svay Rieng', 'REST_STOP', 'KH', 'KH-20', 'Trạm nghỉ Svay Rieng (mẫu)', 11.09, 105.8, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-XEPON', 'Trạm nghỉ Xepon', 'REST_STOP', 'LA', 'LA-SV', 'Trạm nghỉ Xepon (mẫu)', 16.69, 106.22, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-LAKSAO', 'Trạm nghỉ Lak Sao', 'REST_STOP', 'LA', 'LA-BL', 'Trạm nghỉ Lak Sao (mẫu)', 18.19, 104.97, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-PAKSAN', 'Trạm nghỉ Paksan', 'REST_STOP', 'LA', 'LA-BL', 'Trạm nghỉ Paksan (mẫu)', 18.39, 103.66, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-CANTHO', 'Trạm nghỉ Cần Thơ', 'REST_STOP', 'VN', 'VN-92', 'Trạm nghỉ Cần Thơ (mẫu)', 10.03, 105.77, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-BMT', 'Trạm nghỉ Buôn Ma Thuột', 'REST_STOP', 'VN', 'VN-66', 'Trạm nghỉ Buôn Ma Thuột (mẫu)', 12.68, 108.04, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-MOCCHAU', 'Trạm nghỉ Mộc Châu', 'REST_STOP', 'VN', 'VN-14', 'Trạm nghỉ Mộc Châu (mẫu)', 20.84, 104.64, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-SONLA', 'Trạm nghỉ Sơn La', 'REST_STOP', 'VN', 'VN-14', 'Trạm nghỉ Sơn La (mẫu)', 21.33, 103.91, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-BACKAN', 'Trạm nghỉ Bắc Kạn', 'REST_STOP', 'VN', 'VN-19', 'Trạm nghỉ Bắc Kạn (mẫu)', 22.15, 105.83, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-KAMPONGTHOM', 'Trạm nghỉ Kampong Thom', 'REST_STOP', 'KH', 'KH-6', 'Trạm nghỉ Kampong Thom (mẫu)', 12.71, 104.89, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-PURSAT', 'Trạm nghỉ Pursat', 'REST_STOP', 'KH', 'KH-15', 'Trạm nghỉ Pursat (mẫu)', 12.54, 103.92, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-KRATIE', 'Trạm nghỉ Kratie', 'REST_STOP', 'KH', 'KH-10', 'Trạm nghỉ Kratie (mẫu)', 12.49, 106.02, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-VANGVIENG', 'Trạm nghỉ Vang Vieng', 'REST_STOP', 'LA', 'LA-VI', 'Trạm nghỉ Vang Vieng (mẫu)', 18.92, 102.45, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-PHONSAVAN', 'Trạm nghỉ Phonsavan', 'REST_STOP', 'LA', 'LA-XI', 'Trạm nghỉ Phonsavan (mẫu)', 19.45, 103.19, NULL, NULL, NULL, false, 'ACTIVE'),
    ('REST-PAKSE', 'Trạm nghỉ Pakse', 'REST_STOP', 'LA', 'LA-CH', 'Trạm nghỉ Pakse (mẫu)', 15.12, 105.8, NULL, NULL, NULL, false, 'ACTIVE')
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, location_type = EXCLUDED.location_type,
    country_code = EXCLUDED.country_code, province_code = EXCLUDED.province_code, address = EXCLUDED.address,
    latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude, opening_time = EXCLUDED.opening_time,
    closing_time = EXCLUDED.closing_time, counterpart_country_code = EXCLUDED.counterpart_country_code,
    is_headquarters = EXCLUDED.is_headquarters, status = EXCLUDED.status;

-- Kiểm thử migration cũ nạp dữ liệu mẫu khi chưa có bảng này
DO $$
BEGIN
    IF to_regclass('hub_stall') IS NOT NULL THEN
        INSERT INTO hub_stall (location_id, code, stall_kind)
        SELECT l.id, s.code, s.kind
        FROM location l
        CROSS JOIN (VALUES ('S-01', 'STANDARD'), ('S-02', 'STANDARD'), ('S-03', 'STANDARD'), ('I-01', 'ISOLATION')) s(code, kind)
        WHERE l.location_type = 'HUB'
        ON CONFLICT (location_id, code) DO NOTHING;
    END IF;
END $$;

-- Công ty không vận hành nơi cách ly: ngừng trại mẫu đã nạp trước đây
UPDATE location SET status = 'INACTIVE' WHERE code = 'QF-CUCHI';

UPDATE location SET counterpart_country_code = 'KH'
WHERE code = 'GATE-MOCBAI' AND location_type = 'BORDER_GATE' AND counterpart_country_code IS NULL;
UPDATE location SET counterpart_country_code = 'LA'
WHERE code = 'GATE-LAOBAO' AND location_type = 'BORDER_GATE' AND counterpart_country_code IS NULL;

UPDATE location SET is_headquarters = true
WHERE code = 'HUB-HCM' AND NOT EXISTS (SELECT 1 FROM location WHERE is_headquarters);

-- Mỗi tỉnh được phục vụ thuộc đúng một hub.
INSERT INTO hub_service_area (hub_location_id, province_code)
SELECT l.id, v.province_code
FROM (VALUES
    ('HUB-HCM', 'VN-79'),
    ('HUB-HCM', 'VN-75'),
    ('HUB-HCM', 'VN-80'),
    ('HUB-HCM', 'VN-82'),
    ('HUB-HCM', 'VN-86'),
    ('HUB-HCM', 'VN-91'),
    ('HUB-HCM', 'VN-92'),
    ('HUB-HCM', 'VN-96'),
    ('HUB-NT', 'VN-56'),
    ('HUB-NT', 'VN-68'),
    ('HUB-NT', 'VN-66'),
    ('HUB-NT', 'VN-52'),
    ('HUB-DN', 'VN-48'),
    ('HUB-DN', 'VN-46'),
    ('HUB-DN', 'VN-51'),
    ('HUB-DN', 'VN-44'),
    ('HUB-VINH', 'VN-40'),
    ('HUB-VINH', 'VN-42'),
    ('HUB-VINH', 'VN-38'),
    ('HUB-HN', 'VN-1'),
    ('HUB-HN', 'VN-31'),
    ('HUB-HN', 'VN-24'),
    ('HUB-HN', 'VN-4'),
    ('HUB-HN', 'VN-33'),
    ('HUB-HN', 'VN-12'),
    ('HUB-HN', 'VN-20'),
    ('HUB-HN', 'VN-15'),
    ('HUB-HN', 'VN-37'),
    ('HUB-HN', 'VN-25'),
    ('HUB-HN', 'VN-22'),
    ('HUB-HN', 'VN-14'),
    ('HUB-HN', 'VN-19'),
    ('HUB-HN', 'VN-8'),
    ('HUB-HN', 'VN-11'),
    ('HUB-VTE', 'LA-VT'),
    ('HUB-VTE', 'LA-VI'),
    ('HUB-VTE', 'LA-BL'),
    ('HUB-VTE', 'LA-XN'),
    ('HUB-VTE', 'LA-XI'),
    ('HUB-VTE', 'LA-LP'),
    ('HUB-VTE', 'LA-HO'),
    ('HUB-VTE', 'LA-OU'),
    ('HUB-VTE', 'LA-XA'),
    ('HUB-SVK', 'LA-SV'),
    ('HUB-SVK', 'LA-KH'),
    ('HUB-SVK', 'LA-SL'),
    ('HUB-SVK', 'LA-CH'),
    ('HUB-SVK', 'LA-XE'),
    ('HUB-SVK', 'LA-AT')
) AS v(hub_code, province_code)
JOIN location l ON l.code = v.hub_code
UNION ALL
SELECT l.id, p.code FROM province p
JOIN location l ON l.code = 'HUB-PNH'
WHERE p.country_code = 'KH' AND p.code NOT IN ('KH-1', 'KH-16')
ON CONFLICT (province_code) DO UPDATE SET hub_location_id = EXCLUDED.hub_location_id;

DELETE FROM hub_service_area WHERE province_code IN ('KH-1', 'KH-16', 'LA-BK', 'LA-LM', 'LA-PH');

-- ---------------------------------------------------------------------------
-- 2. Tài khoản, nhân viên, khách hàng
-- ---------------------------------------------------------------------------
INSERT INTO app_user (username, email, password_hash, is_email_verified, preferred_language, status, role_code) VALUES
    ('admin.dev', 'admin.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'ADMIN'),
    ('manager.dev', 'manager.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'MANAGER'),
    ('coordinator.dev', 'coordinator.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'COORDINATOR'),
    ('specialist.dev', 'specialist.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'SPECIALIST'),
    ('driver1.dev', 'driver1.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'DRIVER'),
    ('driver2.dev', 'driver2.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'DRIVER'),
    ('driver3.dev', 'driver3.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'DRIVER'),
    ('driver4.dev', 'driver4.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort1.dev', 'escort1.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'ESCORT'),
    ('escort2.dev', 'escort2.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'ESCORT'),
    ('escort3.dev', 'escort3.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver5.dev', 'driver5.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'DRIVER'),
    ('driver6.dev', 'driver6.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort4.dev', 'escort4.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver7.dev', 'driver7.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort5.dev', 'escort5.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver8.dev', 'driver8.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort6.dev', 'escort6.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver9.dev', 'driver9.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort7.dev', 'escort7.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver10.dev', 'driver10.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort8.dev', 'escort8.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver11.dev', 'driver11.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort9.dev', 'escort9.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver12.dev', 'driver12.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort10.dev', 'escort10.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver13.dev', 'driver13.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort11.dev', 'escort11.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver14.dev', 'driver14.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort12.dev', 'escort12.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver15.dev', 'driver15.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort13.dev', 'escort13.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('driver16.dev', 'driver16.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'DRIVER'),
    ('escort14.dev', 'escort14.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', True, 'vi', 'ACTIVE', 'ESCORT'),
    ('customer1.dev', 'customer1.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', 'CUSTOMER')
ON CONFLICT (username) DO NOTHING;

INSERT INTO staff (user_id, staff_code, full_name, phone, hire_date, employment_status, home_hub_location_id, driver_license_class, driver_license_expiry_date)
SELECT u.id, v.staff_code, v.full_name, v.phone, DATE '2026-01-01', 'ACTIVE', hub.id, v.license_class, v.license_expiry
FROM (VALUES
          ('admin.dev', 'ADM-001', 'Quản trị viên (mẫu)', '0900000001', NULL, NULL::date),
          ('manager.dev', 'MGR-001', 'Trần Quản Lý (mẫu)', '0900000002', NULL, NULL::date),
          ('coordinator.dev', 'CRD-001', 'Lê Điều Phối (mẫu)', '0900000003', NULL, NULL::date),
          ('specialist.dev', 'SPC-001', 'Phạm Chuyên Viên (mẫu)', '0900000004', NULL, NULL::date),
          ('driver1.dev', 'DRV-001', 'Võ Tài Xế Một (mẫu)', '0900000005', 'C', DATE '2030-12-31'),
          ('driver2.dev', 'DRV-002', 'Đỗ Tài Xế Hai (mẫu)', '0900000006', 'C', DATE '2030-12-31'),
          ('escort1.dev', 'ESC-001', 'Huỳnh Chăm Ngựa (mẫu)', '0900000007', NULL, NULL::date),
          ('driver3.dev', 'DRV-003', 'Bùi Tài Xế Ba (mẫu)', '0900000008', 'C', DATE '2030-12-31'),
          ('driver4.dev', 'DRV-004', 'Ngô Tài Xế Bốn (mẫu)', '0900000009', 'C', DATE '2030-12-31'),
          ('escort2.dev', 'ESC-002', 'Đinh Chăm Ngựa (mẫu)', '0900000010', NULL, NULL::date),
          ('escort3.dev', 'ESC-003', 'Mai Chăm Ngựa (mẫu)', '0900000011', NULL, NULL::date),
          ('driver5.dev', 'DRV-005', 'Hồ Tài Xế Năm (mẫu)', '0900000012', 'C', DATE '2030-12-31'),
          ('driver6.dev', 'DRV-006', 'Lý Tài Xế Sáu (mẫu)', '0900000013', 'C', DATE '2030-12-31'),
          ('escort4.dev', 'ESC-004', 'Tạ Chăm Ngựa (mẫu)', '0900000014', NULL, NULL::date)
     ) AS v(username, staff_code, full_name, phone, license_class, license_expiry)
JOIN app_user u ON u.username = v.username
JOIN location hub ON hub.is_headquarters
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO staff (user_id, staff_code, full_name, phone, hire_date, employment_status, home_hub_location_id, driver_license_class, driver_license_expiry_date)
SELECT u.id, v.staff_code, v.full_name, v.phone, DATE '2026-01-01', 'ACTIVE', hub.id, v.license_class,
       CASE WHEN v.license_class IS NOT NULL THEN DATE '2030-12-31' END
FROM (VALUES
    ('driver7.dev', 'DRV-007', 'Tài xế NT 1 (mẫu)', '0900000120', 'HUB-NT', 'C'),
    ('escort5.dev', 'ESC-005', 'Hộ tống NT 1 (mẫu)', '0900000121', 'HUB-NT', NULL),
    ('driver8.dev', 'DRV-008', 'Tài xế NT 2 (mẫu)', '0900000122', 'HUB-NT', 'C'),
    ('escort6.dev', 'ESC-006', 'Hộ tống NT 2 (mẫu)', '0900000123', 'HUB-NT', NULL),
    ('driver9.dev', 'DRV-009', 'Tài xế VINH 1 (mẫu)', '0900000124', 'HUB-VINH', 'C'),
    ('escort7.dev', 'ESC-007', 'Hộ tống VINH 1 (mẫu)', '0900000125', 'HUB-VINH', NULL),
    ('driver10.dev', 'DRV-010', 'Tài xế VINH 2 (mẫu)', '0900000126', 'HUB-VINH', 'C'),
    ('escort8.dev', 'ESC-008', 'Hộ tống VINH 2 (mẫu)', '0900000127', 'HUB-VINH', NULL),
    ('driver11.dev', 'DRV-011', 'Tài xế PNH 1 (mẫu)', '0900000128', 'HUB-PNH', 'C'),
    ('escort9.dev', 'ESC-009', 'Hộ tống PNH 1 (mẫu)', '0900000129', 'HUB-PNH', NULL),
    ('driver12.dev', 'DRV-012', 'Tài xế PNH 2 (mẫu)', '0900000130', 'HUB-PNH', 'C'),
    ('escort10.dev', 'ESC-010', 'Hộ tống PNH 2 (mẫu)', '0900000131', 'HUB-PNH', NULL),
    ('driver13.dev', 'DRV-013', 'Tài xế VTE 1 (mẫu)', '0900000132', 'HUB-VTE', 'C'),
    ('escort11.dev', 'ESC-011', 'Hộ tống VTE 1 (mẫu)', '0900000133', 'HUB-VTE', NULL),
    ('driver14.dev', 'DRV-014', 'Tài xế VTE 2 (mẫu)', '0900000134', 'HUB-VTE', 'C'),
    ('escort12.dev', 'ESC-012', 'Hộ tống VTE 2 (mẫu)', '0900000135', 'HUB-VTE', NULL),
    ('driver15.dev', 'DRV-015', 'Tài xế SVK 1 (mẫu)', '0900000136', 'HUB-SVK', 'C'),
    ('escort13.dev', 'ESC-013', 'Hộ tống SVK 1 (mẫu)', '0900000137', 'HUB-SVK', NULL),
    ('driver16.dev', 'DRV-016', 'Tài xế SVK 2 (mẫu)', '0900000138', 'HUB-SVK', 'C'),
    ('escort14.dev', 'ESC-014', 'Hộ tống SVK 2 (mẫu)', '0900000139', 'HUB-SVK', NULL)
) AS v(username, staff_code, full_name, phone, hub_code, license_class)
JOIN app_user u ON u.username = v.username
JOIN location hub ON hub.code = v.hub_code
ON CONFLICT (user_id) DO UPDATE SET home_hub_location_id = EXCLUDED.home_hub_location_id;

-- Bổ sung số người còn thiếu ở từng hub theo quy chế đội xe.
WITH staffing(hub_code, first_driver, last_driver, first_escort, last_escort) AS (
    VALUES
    ('HUB-HCM', 17, 21, 15, 21),
    ('HUB-NT', 22, 23, 22, 23),
    ('HUB-DN', 24, 31, 24, 31),
    ('HUB-VINH', 32, 34, 32, 34),
    ('HUB-HN', 35, 38, 35, 38),
    ('HUB-PNH', 39, 39, 39, 39),
    ('HUB-VTE', 40, 40, 40, 40),
    ('HUB-SVK', 41, 41, 41, 41)
), personnel AS (
    SELECT hub_code, number, 'driver' AS prefix, 'DRIVER' AS role_code, 'DRV-' AS code_prefix,
           'Tài xế' AS display_role, 'C' AS license_class
    FROM staffing CROSS JOIN LATERAL generate_series(first_driver, last_driver) AS number
    UNION ALL
    SELECT hub_code, number, 'escort', 'ESCORT', 'ESC-', 'Hộ tống', NULL
    FROM staffing CROSS JOIN LATERAL generate_series(first_escort, last_escort) AS number
), accounts AS (
    SELECT *, prefix || number || '.dev' AS username FROM personnel
), inserted AS (
    INSERT INTO app_user (username, email, password_hash, is_email_verified, preferred_language, status, role_code)
    SELECT a.username, a.username || '@racehorse.test',
           '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE', a.role_code
    FROM accounts a
    ON CONFLICT (username) DO NOTHING
    RETURNING id, username
), users AS (
    SELECT id, username FROM inserted
    UNION ALL
    SELECT u.id, u.username FROM app_user u JOIN accounts a ON a.username = u.username
)
INSERT INTO staff (user_id, staff_code, full_name, phone, hire_date, employment_status, home_hub_location_id,
                   driver_license_class, driver_license_expiry_date)
SELECT u.id, a.code_prefix || lpad(a.number::text, 3, '0'),
       a.display_role || ' ' || a.number || ' (mẫu)',
       '090000' || ((CASE WHEN a.role_code = 'DRIVER' THEN 2000 ELSE 3000 END) + a.number),
       DATE '2026-01-01', 'ACTIVE', hub.id, a.license_class,
       CASE WHEN a.license_class IS NOT NULL THEN DATE '2030-12-31' END
FROM accounts a JOIN users u ON u.username = a.username
JOIN location hub ON hub.code = a.hub_code
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO customer (user_id, customer_type, full_name, phone, address, country_code)
SELECT u.id, 'PERSON', 'Nguyễn Văn Khách (mẫu)', '0909000001', 'Phường Thủ Dầu Một, TP.HCM', 'VN'
FROM app_user u
WHERE u.username = 'customer1.dev'
ON CONFLICT (user_id) DO NOTHING;

-- Đối tác: không có tài khoản đăng nhập
INSERT INTO party (party_type, display_name, phone, email, address, country_code, status)
SELECT 'ORGANIZATION', v.display_name, v.phone, NULL, v.address, v.country_code, 'ACTIVE'
FROM (VALUES
          ('Đại lý vận tải Campuchia (mẫu)', '0855000001', 'Phnom Penh, Campuchia', 'KH'),
          ('Đại lý vận tải Lào (mẫu)', '0856000001', 'Savannakhet, Lào', 'LA'),
          ('Phòng khám thú y ngựa (mẫu)', '0900000400', 'TP.HCM', 'VN'),
          ('Đội cứu hộ xe tải (mẫu)', '0900000500', 'TP.HCM', 'VN'),
          ('Công ty bảo hiểm (mẫu)', '0900000600', 'TP.HCM', 'VN'),
          ('Chuồng gửi ngựa Đà Nẵng (mẫu)', '0900000700', 'TP. Đà Nẵng', 'VN'),
          ('Chuồng gửi ngựa Tuy Hòa (mẫu)', '0900000701', 'Phường Tuy Hòa, Đắk Lắk', 'VN'),
          ('Chuồng gửi ngựa Huế (mẫu)', '0900000702', 'TP. Huế', 'VN'),
          ('Chuồng gửi ngựa Vinh (mẫu)', '0900000703', 'Nghệ An', 'VN'),
          ('Chuồng gửi ngựa Đồng Hới (mẫu)', '0900000704', 'Quảng Trị', 'VN'),
          ('Chuồng gửi ngựa Thanh Hóa (mẫu)', '0900000705', 'Thanh Hóa', 'VN'),
          ('Chuồng gửi ngựa Quảng Ngãi (mẫu)', '0900000706', 'Quảng Ngãi', 'VN'),
          ('Chuồng gửi ngựa Phan Rang (mẫu)', '0900000707', 'Khánh Hòa', 'VN')
     ) AS v(display_name, phone, address, country_code)
WHERE NOT EXISTS (SELECT 1 FROM party p WHERE p.display_name = v.display_name);

INSERT INTO party_role (party_id, role_type, valid_from)
SELECT p.id, v.role_type, DATE '2026-01-01'
FROM (VALUES
          ('Đại lý vận tải Campuchia (mẫu)', 'DEST_AGENT'),
          ('Đại lý vận tải Lào (mẫu)', 'DEST_AGENT'),
          ('Phòng khám thú y ngựa (mẫu)', 'VET'),
          ('Đội cứu hộ xe tải (mẫu)', 'RESCUE'),
          ('Công ty bảo hiểm (mẫu)', 'INSURER'),
          ('Chuồng gửi ngựa Đà Nẵng (mẫu)', 'STABLE'),
          ('Chuồng gửi ngựa Tuy Hòa (mẫu)', 'STABLE'),
          ('Chuồng gửi ngựa Huế (mẫu)', 'STABLE'),
          ('Chuồng gửi ngựa Vinh (mẫu)', 'STABLE'),
          ('Chuồng gửi ngựa Đồng Hới (mẫu)', 'STABLE'),
          ('Chuồng gửi ngựa Thanh Hóa (mẫu)', 'STABLE'),
          ('Chuồng gửi ngựa Quảng Ngãi (mẫu)', 'STABLE'),
          ('Chuồng gửi ngựa Phan Rang (mẫu)', 'STABLE')
     ) AS v(display_name, role_type)
JOIN party p ON p.display_name = v.display_name
ON CONFLICT DO NOTHING;

UPDATE party SET status = 'INACTIVE' WHERE display_name = 'Trại cách ly Củ Chi (mẫu)';

-- ---------------------------------------------------------------------------
-- 3. Xe tại hub gốc
-- ---------------------------------------------------------------------------
-- Theo docs/QUY_CHE_DOI_XE.md
INSERT INTO vehicle (license_plate, vehicle_class_id, standard_stall_count, wide_stall_count, home_hub_location_id, status)
SELECT v.plate, vc.id, v.std, v.wide, hub.id, 'ACTIVE'
FROM (VALUES
          ('51C-100.01', 'LIGHT', 2, 0),
          ('51C-100.02', 'MEDIUM', 2, 2),
          ('51C-100.03', 'HEAVY', 5, 2),
          ('51C-100.04', 'HEAVY', 9, 0),
          ('51C-100.05', 'HEAVY', 9, 0),
          ('51C-100.06', 'HEAVY', 9, 0)
     ) AS v(plate, class_code, std, wide)
JOIN vehicle_class vc ON vc.code = v.class_code
JOIN location hub ON hub.code = 'HUB-HCM'
ON CONFLICT (license_plate) DO NOTHING;

UPDATE vehicle
SET standard_stall_count = adjustment.new_standard
FROM (VALUES ('51C-100.02', 'MEDIUM', 4, 2), ('51C-100.03', 'HEAVY', 7, 5))
    AS adjustment(plate, class_code, old_standard, new_standard)
JOIN vehicle_class vc ON vc.code = adjustment.class_code
WHERE vehicle.license_plate = adjustment.plate AND vehicle.vehicle_class_id = vc.id
  AND vehicle.standard_stall_count = adjustment.old_standard AND vehicle.wide_stall_count = 2;

-- Dự phòng 20%, làm tròn lên theo hub. HUB-HCM đã có ba xe HEAVY 9 khoang.
INSERT INTO vehicle (license_plate, vehicle_class_id, standard_stall_count, wide_stall_count, home_hub_location_id, status)
SELECT v.plate, vc.id, v.std, v.wide, hub.id, 'ACTIVE'
FROM (VALUES
    ('M1-1H-01', 'HEAVY', 9, 0, 'HUB-DN'),
    ('M1-1H-02', 'HEAVY', 9, 0, 'HUB-DN'),
    ('M1-1H-03', 'HEAVY', 9, 0, 'HUB-DN'),
    ('M1-4L-01', 'LIGHT', 2, 0, 'HUB-NT'),
    ('M1-4M-01', 'MEDIUM', 2, 2, 'HUB-NT'),
    ('M1-5L-01', 'LIGHT', 2, 0, 'HUB-PNH'),
    ('M1-5M-01', 'MEDIUM', 2, 2, 'HUB-PNH'),
    ('M1-6L-01', 'LIGHT', 2, 0, 'HUB-SVK'),
    ('M1-6M-01', 'MEDIUM', 2, 2, 'HUB-SVK'),
    ('M1-7H-01', 'HEAVY', 9, 0, 'HUB-VINH'),
    ('M1-7H-02', 'HEAVY', 9, 0, 'HUB-VINH'),
    ('M1-7L-01', 'LIGHT', 2, 0, 'HUB-VINH'),
    ('M1-7M-01', 'MEDIUM', 2, 2, 'HUB-VINH'),
    ('M1-8L-01', 'LIGHT', 2, 0, 'HUB-VTE'),
    ('M1-8M-01', 'MEDIUM', 2, 2, 'HUB-VTE'),
    ('M1-3H-01', 'HEAVY', 9, 0, 'HUB-HCM'),
    ('M1-3H-02', 'HEAVY', 9, 0, 'HUB-HCM'),
    ('M1-3M-01', 'MEDIUM', 2, 2, 'HUB-HCM'),
    ('M1-3M-02', 'MEDIUM', 2, 2, 'HUB-HCM'),
    ('M1-3M-03', 'MEDIUM', 2, 2, 'HUB-HCM'),
    ('M1-4H-01', 'HEAVY', 9, 0, 'HUB-NT'),
    ('M1-4M-02', 'MEDIUM', 2, 2, 'HUB-NT'),
    ('M1-1H-04', 'HEAVY', 9, 0, 'HUB-DN'),
    ('M1-1M-01', 'MEDIUM', 2, 2, 'HUB-DN'),
    ('M1-1M-02', 'MEDIUM', 2, 2, 'HUB-DN'),
    ('M1-1M-03', 'MEDIUM', 2, 2, 'HUB-DN'),
    ('M1-1L-01', 'LIGHT', 2, 0, 'HUB-DN'),
    ('M1-7H-03', 'HEAVY', 9, 0, 'HUB-VINH'),
    ('M1-7M-02', 'MEDIUM', 2, 2, 'HUB-VINH'),
    ('M1-2M-01', 'MEDIUM', 2, 2, 'HUB-HN'),
    ('M1-2M-02', 'MEDIUM', 2, 2, 'HUB-HN'),
    ('M1-2M-03', 'MEDIUM', 2, 2, 'HUB-HN'),
    ('M1-2L-01', 'LIGHT', 2, 0, 'HUB-HN'),
    ('M1-5M-02', 'MEDIUM', 2, 2, 'HUB-PNH'),
    ('M1-8M-02', 'MEDIUM', 2, 2, 'HUB-VTE'),
    ('M1-6M-02', 'MEDIUM', 2, 2, 'HUB-SVK')
) AS v(plate, class_code, std, wide, hub_code)
JOIN vehicle_class vc ON vc.code = v.class_code
JOIN location hub ON hub.code = v.hub_code
ON CONFLICT (license_plate) DO UPDATE SET home_hub_location_id = EXCLUDED.home_hub_location_id;

-- ---------------------------------------------------------------------------
-- 4. Tuyến trục chính, điểm dừng, lịch chạy
-- ---------------------------------------------------------------------------
-- Giờ chạy và km theo Valhalla xe tải 10 tấn ngày 26/11/2026 (mẫu).
INSERT INTO route (code, name, origin_location_id, destination_location_id, distance_km, estimated_duration_minutes, status)
SELECT v.code, v.name, o.id, d.id, v.distance_km, v.minutes,
       CASE WHEN v.code IN ('MOCBAI-HCM', 'DN-LAOBAO', 'LAOBAO-DN') THEN 'INACTIVE' ELSE 'ACTIVE' END
FROM (VALUES
    ('HCM-NT', 'TP.HCM – Nha Trang', 'HUB-HCM', 'HUB-NT', 418, 478),
    ('NT-HCM', 'Nha Trang – TP.HCM', 'HUB-NT', 'HUB-HCM', 424, 497),
    ('NT-DN', 'Nha Trang – Đà Nẵng', 'HUB-NT', 'HUB-DN', 555, 699),
    ('DN-NT', 'Đà Nẵng – Nha Trang', 'HUB-DN', 'HUB-NT', 566, 694),
    ('HCM-DN', 'TP.HCM – Đà Nẵng', 'HUB-HCM', 'HUB-DN', 972, 1747),
    ('DN-HCM', 'Đà Nẵng – TP.HCM', 'HUB-DN', 'HUB-HCM', 990, 1761),
    ('DN-VINH', 'Đà Nẵng – Vinh', 'HUB-DN', 'HUB-VINH', 477, 616),
    ('VINH-DN', 'Vinh – Đà Nẵng', 'HUB-VINH', 'HUB-DN', 478, 616),
    ('VINH-HN', 'Vinh – Hà Nội', 'HUB-VINH', 'HUB-HN', 317, 399),
    ('HN-VINH', 'Hà Nội – Vinh', 'HUB-HN', 'HUB-VINH', 316, 397),
    ('DN-HN', 'Đà Nẵng – Hà Nội', 'HUB-DN', 'HUB-HN', 795, 1585),
    ('HN-DN', 'Hà Nội – Đà Nẵng', 'HUB-HN', 'HUB-DN', 793, 1583),
    ('HCM-HN', 'TP.HCM – Hà Nội', 'HUB-HCM', 'HUB-HN', 1767, 3902),
    ('HN-HCM', 'Hà Nội – TP.HCM', 'HUB-HN', 'HUB-HCM', 1783, 3914),
    ('HCM-PNH', 'TP.HCM – Phnom Penh (qua Mộc Bài)', 'HUB-HCM', 'HUB-PNH', 268, 620),
    ('PNH-HCM', 'Phnom Penh – TP.HCM (qua Mộc Bài)', 'HUB-PNH', 'HUB-HCM', 264, 617),
    ('DN-SVK', 'Đà Nẵng – Savannakhet (qua Lao Bảo)', 'HUB-DN', 'HUB-SVK', 503, 1412),
    ('SVK-DN', 'Savannakhet – Đà Nẵng (qua Lao Bảo)', 'HUB-SVK', 'HUB-DN', 504, 1414),
    ('VINH-VTE', 'Vinh – Viêng Chăn (qua Cầu Treo)', 'HUB-VINH', 'HUB-VTE', 466, 715),
    ('VTE-VINH', 'Viêng Chăn – Vinh (qua Cầu Treo)', 'HUB-VTE', 'HUB-VINH', 463, 695),
    ('MOCBAI-HCM', 'Cửa khẩu Mộc Bài – TP.HCM (từ Campuchia về)', 'GATE-MOCBAI', 'HUB-HCM', 103.00, 291),
    ('DN-LAOBAO', 'Đà Nẵng – Cửa khẩu Lao Bảo (sang Lào)', 'HUB-DN', 'GATE-LAOBAO', 294.00, 383),
    ('LAOBAO-DN', 'Cửa khẩu Lao Bảo – Đà Nẵng (từ Lào về)', 'GATE-LAOBAO', 'HUB-DN', 291.00, 411)
) AS v(code, name, origin_code, destination_code, distance_km, minutes)
JOIN location o ON o.code = v.origin_code
JOIN location d ON d.code = v.destination_code
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, origin_location_id = EXCLUDED.origin_location_id,
    destination_location_id = EXCLUDED.destination_location_id, distance_km = EXCLUDED.distance_km,
    estimated_duration_minutes = EXCLUDED.estimated_duration_minutes, status = EXCLUDED.status;

-- Cập nhật tại chỗ vì trip_stop tham chiếu khóa của route_stop.
INSERT INTO route_stop (route_id, sequence_number, location_id, stop_type, arrival_offset_minutes, dwell_minutes)
SELECT r.id, v.seq, l.id, v.stop_type, v.arrival_offset, v.dwell
FROM (VALUES
    ('HCM-NT', 1, 'HUB-HCM', 'HUB', 0, 30),
    ('HCM-NT', 2, 'REST-PHANTHIET', 'REST', 177, 30),
    ('HCM-NT', 3, 'REST-PHANRANG', 'REST', 343, 30),
    ('HCM-NT', 4, 'HUB-NT', 'HUB', 478, 0),
    ('NT-HCM', 1, 'HUB-NT', 'HUB', 0, 30),
    ('NT-HCM', 2, 'REST-PHANRANG', 'REST', 140, 30),
    ('NT-HCM', 3, 'REST-PHANTHIET', 'REST', 309, 30),
    ('NT-HCM', 4, 'HUB-HCM', 'HUB', 497, 0),
    ('NT-DN', 1, 'HUB-NT', 'HUB', 0, 30),
    ('NT-DN', 2, 'REST-TUYHOA', 'REST', 171, 30),
    ('NT-DN', 3, 'REST-QUYNHON', 'REST', 310, 30),
    ('NT-DN', 4, 'REST-QUANGNGAI', 'REST', 535, 30),
    ('NT-DN', 5, 'HUB-DN', 'HUB', 699, 0),
    ('DN-NT', 1, 'HUB-DN', 'HUB', 0, 30),
    ('DN-NT', 2, 'REST-QUANGNGAI', 'REST', 166, 30),
    ('DN-NT', 3, 'REST-QUYNHON', 'REST', 385, 30),
    ('DN-NT', 4, 'REST-TUYHOA', 'REST', 525, 30),
    ('DN-NT', 5, 'HUB-NT', 'HUB', 694, 0),
    ('HCM-DN', 1, 'HUB-HCM', 'HUB', 0, 30),
    ('HCM-DN', 2, 'REST-PHANTHIET', 'REST', 177, 30),
    ('HCM-DN', 3, 'REST-PHANRANG', 'REST', 343, 30),
    ('HCM-DN', 4, 'HUB-NT', 'REST', 478, 30),
    ('HCM-DN', 5, 'REST-TUYHOA', 'REST', 649, 600),
    ('HCM-DN', 6, 'REST-QUYNHON', 'REST', 1358, 30),
    ('HCM-DN', 7, 'REST-QUANGNGAI', 'REST', 1583, 30),
    ('HCM-DN', 8, 'HUB-DN', 'HUB', 1747, 0),
    ('DN-HCM', 1, 'HUB-DN', 'HUB', 0, 30),
    ('DN-HCM', 2, 'REST-QUANGNGAI', 'REST', 166, 30),
    ('DN-HCM', 3, 'REST-QUYNHON', 'REST', 385, 30),
    ('DN-HCM', 4, 'REST-TUYHOA', 'REST', 525, 30),
    ('DN-HCM', 5, 'HUB-NT', 'REST', 694, 600),
    ('DN-HCM', 6, 'REST-PHANRANG', 'REST', 1404, 30),
    ('DN-HCM', 7, 'REST-PHANTHIET', 'REST', 1573, 30),
    ('DN-HCM', 8, 'HUB-HCM', 'HUB', 1761, 0),
    ('DN-VINH', 1, 'HUB-DN', 'HUB', 0, 30),
    ('DN-VINH', 2, 'REST-HUE', 'REST', 134, 30),
    ('DN-VINH', 3, 'REST-DONGHA', 'REST', 259, 30),
    ('DN-VINH', 4, 'REST-DONGHOI', 'REST', 398, 30),
    ('DN-VINH', 5, 'HUB-VINH', 'HUB', 616, 0),
    ('VINH-DN', 1, 'HUB-VINH', 'HUB', 0, 30),
    ('VINH-DN', 2, 'REST-DONGHOI', 'REST', 216, 30),
    ('VINH-DN', 3, 'REST-DONGHA', 'REST', 355, 30),
    ('VINH-DN', 4, 'REST-HUE', 'REST', 480, 30),
    ('VINH-DN', 5, 'HUB-DN', 'HUB', 616, 0),
    ('VINH-HN', 1, 'HUB-VINH', 'HUB', 0, 30),
    ('VINH-HN', 2, 'REST-THANHHOA', 'REST', 181, 30),
    ('VINH-HN', 3, 'HUB-HN', 'HUB', 399, 0),
    ('HN-VINH', 1, 'HUB-HN', 'HUB', 0, 30),
    ('HN-VINH', 2, 'REST-THANHHOA', 'REST', 217, 30),
    ('HN-VINH', 3, 'HUB-VINH', 'HUB', 397, 0),
    ('DN-HN', 1, 'HUB-DN', 'HUB', 0, 30),
    ('DN-HN', 2, 'REST-HUE', 'REST', 134, 30),
    ('DN-HN', 3, 'REST-DONGHA', 'REST', 259, 30),
    ('DN-HN', 4, 'REST-DONGHOI', 'REST', 398, 30),
    ('DN-HN', 5, 'HUB-VINH', 'REST', 616, 600),
    ('DN-HN', 6, 'REST-THANHHOA', 'REST', 1367, 30),
    ('DN-HN', 7, 'HUB-HN', 'HUB', 1585, 0),
    ('HN-DN', 1, 'HUB-HN', 'HUB', 0, 30),
    ('HN-DN', 2, 'REST-THANHHOA', 'REST', 217, 30),
    ('HN-DN', 3, 'HUB-VINH', 'REST', 397, 30),
    ('HN-DN', 4, 'REST-DONGHOI', 'REST', 613, 600),
    ('HN-DN', 5, 'REST-DONGHA', 'REST', 1322, 30),
    ('HN-DN', 6, 'REST-HUE', 'REST', 1447, 30),
    ('HN-DN', 7, 'HUB-DN', 'HUB', 1583, 0),
    ('HCM-HN', 1, 'HUB-HCM', 'HUB', 0, 30),
    ('HCM-HN', 2, 'REST-PHANTHIET', 'REST', 177, 30),
    ('HCM-HN', 3, 'REST-PHANRANG', 'REST', 343, 30),
    ('HCM-HN', 4, 'HUB-NT', 'REST', 478, 30),
    ('HCM-HN', 5, 'REST-TUYHOA', 'REST', 649, 600),
    ('HCM-HN', 6, 'REST-QUYNHON', 'REST', 1358, 30),
    ('HCM-HN', 7, 'REST-QUANGNGAI', 'REST', 1583, 30),
    ('HCM-HN', 8, 'HUB-DN', 'REST', 1747, 30),
    ('HCM-HN', 9, 'REST-HUE', 'REST', 1881, 600),
    ('HCM-HN', 10, 'REST-DONGHA', 'REST', 2576, 30),
    ('HCM-HN', 11, 'REST-DONGHOI', 'REST', 2715, 30),
    ('HCM-HN', 12, 'HUB-VINH', 'REST', 2933, 30),
    ('HCM-HN', 13, 'REST-THANHHOA', 'REST', 3114, 600),
    ('HCM-HN', 14, 'HUB-HN', 'HUB', 3902, 0),
    ('HN-HCM', 1, 'HUB-HN', 'HUB', 0, 30),
    ('HN-HCM', 2, 'REST-THANHHOA', 'REST', 217, 30),
    ('HN-HCM', 3, 'HUB-VINH', 'REST', 397, 30),
    ('HN-HCM', 4, 'REST-DONGHOI', 'REST', 613, 600),
    ('HN-HCM', 5, 'REST-DONGHA', 'REST', 1322, 30),
    ('HN-HCM', 6, 'REST-HUE', 'REST', 1447, 30),
    ('HN-HCM', 7, 'HUB-DN', 'REST', 1583, 30),
    ('HN-HCM', 8, 'REST-QUANGNGAI', 'REST', 1749, 600),
    ('HN-HCM', 9, 'REST-QUYNHON', 'REST', 2538, 30),
    ('HN-HCM', 10, 'REST-TUYHOA', 'REST', 2678, 30),
    ('HN-HCM', 11, 'HUB-NT', 'REST', 2847, 30),
    ('HN-HCM', 12, 'REST-PHANRANG', 'REST', 2987, 600),
    ('HN-HCM', 13, 'REST-PHANTHIET', 'REST', 3726, 30),
    ('HN-HCM', 14, 'HUB-HCM', 'HUB', 3914, 0),
    ('HCM-PNH', 1, 'HUB-HCM', 'HUB', 0, 30),
    ('HCM-PNH', 2, 'REST-TRANGBANG', 'REST', 160, 30),
    ('HCM-PNH', 3, 'GATE-MOCBAI', 'BORDER', 226, 120),
    ('HCM-PNH', 4, 'REST-SVAYRIENG', 'REST', 409, 30),
    ('HCM-PNH', 5, 'HUB-PNH', 'HUB', 620, 0),
    ('PNH-HCM', 1, 'HUB-PNH', 'HUB', 0, 30),
    ('PNH-HCM', 2, 'REST-SVAYRIENG', 'REST', 211, 30),
    ('PNH-HCM', 3, 'GATE-MOCBAI', 'BORDER', 305, 120),
    ('PNH-HCM', 4, 'REST-TRANGBANG', 'REST', 460, 30),
    ('PNH-HCM', 5, 'HUB-HCM', 'HUB', 617, 0),
    ('DN-SVK', 1, 'HUB-DN', 'HUB', 0, 30),
    ('DN-SVK', 2, 'REST-HUE', 'REST', 134, 30),
    ('DN-SVK', 3, 'REST-DONGHA', 'REST', 259, 30),
    ('DN-SVK', 4, 'GATE-LAOBAO', 'BORDER', 380, 120),
    ('DN-SVK', 5, 'REST-XEPON', 'REST', 582, 600),
    ('DN-SVK', 6, 'HUB-SVK', 'HUB', 1412, 0),
    ('SVK-DN', 1, 'HUB-SVK', 'HUB', 0, 30),
    ('SVK-DN', 2, 'REST-XEPON', 'REST', 259, 30),
    ('SVK-DN', 3, 'GATE-LAOBAO', 'BORDER', 371, 120),
    ('SVK-DN', 4, 'REST-DONGHA', 'REST', 583, 30),
    ('SVK-DN', 5, 'REST-HUE', 'REST', 708, 600),
    ('SVK-DN', 6, 'HUB-DN', 'HUB', 1414, 0),
    ('VINH-VTE', 1, 'HUB-VINH', 'HUB', 0, 30),
    ('VINH-VTE', 2, 'GATE-CAUTREO', 'BORDER', 151, 120),
    ('VINH-VTE', 3, 'REST-LAKSAO', 'REST', 332, 30),
    ('VINH-VTE', 4, 'REST-PAKSAN', 'REST', 522, 30),
    ('VINH-VTE', 5, 'HUB-VTE', 'HUB', 715, 0),
    ('VTE-VINH', 1, 'HUB-VTE', 'HUB', 0, 30),
    ('VTE-VINH', 2, 'REST-PAKSAN', 'REST', 195, 30),
    ('VTE-VINH', 3, 'REST-LAKSAO', 'REST', 384, 30),
    ('VTE-VINH', 4, 'GATE-CAUTREO', 'BORDER', 454, 120),
    ('VTE-VINH', 5, 'HUB-VINH', 'HUB', 695, 0),
    ('MOCBAI-HCM', 1, 'GATE-MOCBAI', 'BORDER', 0, 60),
    ('MOCBAI-HCM', 2, 'REST-TRANGBANG', 'REST', 95, 30),
    ('MOCBAI-HCM', 3, 'HUB-HCM', 'HUB', 291, 0),
    ('DN-LAOBAO', 1, 'HUB-DN', 'HUB', 0, 30),
    ('DN-LAOBAO', 2, 'REST-HUE', 'REST', 129, 30),
    ('DN-LAOBAO', 3, 'REST-DONGHA', 'REST', 262, 30),
    ('DN-LAOBAO', 4, 'GATE-LAOBAO', 'BORDER', 383, 120),
    ('LAOBAO-DN', 1, 'GATE-LAOBAO', 'BORDER', 0, 60),
    ('LAOBAO-DN', 2, 'REST-DONGHA', 'REST', 152, 30),
    ('LAOBAO-DN', 3, 'REST-HUE', 'REST', 281, 30),
    ('LAOBAO-DN', 4, 'HUB-DN', 'HUB', 411, 0)
) AS v(route_code, seq, location_code, stop_type, arrival_offset, dwell)
JOIN route r ON r.code = v.route_code
JOIN location l ON l.code = v.location_code
ON CONFLICT (route_id, sequence_number) DO UPDATE SET location_id = EXCLUDED.location_id, stop_type = EXCLUDED.stop_type,
    arrival_offset_minutes = EXCLUDED.arrival_offset_minutes, dwell_minutes = EXCLUDED.dwell_minutes;

-- Tuyến mẫu rút ngắn thì bỏ điểm thừa chưa lượt xe nào dùng.
DELETE FROM route_stop rs USING route r
WHERE rs.route_id = r.id
  AND rs.sequence_number > CASE r.code
      WHEN 'HCM-DN' THEN 8
      WHEN 'DN-HCM' THEN 8
      WHEN 'DN-HN' THEN 7
      WHEN 'HN-DN' THEN 7
      WHEN 'HCM-HN' THEN 14
      WHEN 'HN-HCM' THEN 14
      WHEN 'HCM-PNH' THEN 5
      ELSE 2147483647 END
  AND NOT EXISTS (SELECT 1 FROM trip_stop ts WHERE ts.route_id = rs.route_id AND ts.route_stop_sequence_number = rs.sequence_number);

-- Lịch mẫu theo docs/QUY_CHE_DOI_XE.md, dùng số thứ tự ISO (thứ Hai = 1).
-- Đổi ngày trên lịch hiện có để giữ liên kết của các chuyến đã sinh.
WITH timetable(route_code, departure_time, day_of_week, schedule_status) AS (
    VALUES
    ('HCM-NT', TIME '05:00', 6, 'ACTIVE'),
    ('NT-HCM', TIME '05:00', 1, 'ACTIVE'),
    ('NT-DN', TIME '05:00', 3, 'ACTIVE'),
    ('DN-NT', TIME '05:00', 4, 'ACTIVE'),
    ('HCM-DN', TIME '05:00', 6, 'ACTIVE'),
    ('DN-HCM', TIME '05:00', 1, 'ACTIVE'),
    ('DN-VINH', TIME '05:00', 6, 'ACTIVE'),
    ('VINH-DN', TIME '05:00', 1, 'ACTIVE'),
    ('VINH-HN', TIME '05:00', 3, 'ACTIVE'),
    ('HN-VINH', TIME '05:00', 4, 'ACTIVE'),
    ('DN-HN', TIME '05:00', 5, 'ACTIVE'),
    ('HN-DN', TIME '05:00', 1, 'ACTIVE'),
    ('HCM-HN', TIME '05:00', 3, 'ACTIVE'),
    ('HN-HCM', TIME '05:00', 1, 'ACTIVE'),
    ('HCM-PNH', TIME '06:00', 6, 'ACTIVE'),
    ('PNH-HCM', TIME '06:00', 1, 'ACTIVE'),
    ('DN-SVK', TIME '06:00', 5, 'ACTIVE'),
    ('SVK-DN', TIME '06:00', 1, 'ACTIVE'),
    ('VINH-VTE', TIME '06:00', 6, 'ACTIVE'),
    ('VTE-VINH', TIME '06:00', 1, 'ACTIVE'),
    ('MOCBAI-HCM', TIME '14:00', NULL, 'ENDED'),
    ('DN-LAOBAO', TIME '05:00', NULL, 'ENDED'),
    ('LAOBAO-DN', TIME '07:00', NULL, 'ENDED')
), updated AS (
    UPDATE departure_schedule s SET booking_cutoff_hours = 72, status = t.schedule_status
    FROM route r JOIN timetable t ON t.route_code = r.code
    WHERE s.route_id = r.id AND (t.schedule_status = 'ENDED'
        OR (s.departure_time = t.departure_time AND s.valid_from = DATE '2026-11-01'))
    RETURNING s.id, s.route_id, t.day_of_week
), inserted AS (
    INSERT INTO departure_schedule (route_id, departure_time, booking_cutoff_hours, stall_capacity, valid_from, status)
    SELECT r.id, t.departure_time, 72, 9, DATE '2026-11-01', t.schedule_status
    FROM timetable t JOIN route r ON r.code = t.route_code
    WHERE NOT EXISTS (SELECT 1 FROM updated u WHERE u.route_id = r.id)
    RETURNING id, route_id
), removed AS (
    DELETE FROM departure_schedule_day dd USING updated u
    WHERE dd.departure_schedule_id = u.id AND dd.day_of_week IS DISTINCT FROM u.day_of_week
)
INSERT INTO departure_schedule_day (departure_schedule_id, day_of_week)
SELECT u.id, u.day_of_week FROM updated u WHERE u.day_of_week IS NOT NULL
UNION ALL
SELECT i.id, t.day_of_week FROM inserted i JOIN route r ON r.id = i.route_id JOIN timetable t ON t.route_code = r.code
WHERE t.day_of_week IS NOT NULL
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Bảng giá mẫu
-- ---------------------------------------------------------------------------
INSERT INTO price_list (name, currency_code, margin_rate, fuel_buffer_rate, deposit_rate, valid_from, status)
SELECT 'Bảng giá mẫu (dev)', 'VND', 0.0500, 0.0500, 0.3000, DATE '2026-01-01', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM price_list WHERE name = 'Bảng giá mẫu (dev)');

-- Cước trục mẫu: 12.000 đ/km khoang thường, khoang rộng ×1,3; làm tròn 100.000 đ.
INSERT INTO price_item (price_list_id, billable_service_id, route_id, stall_type_code, unit_price)
SELECT pl.id, s.id, r.id, v.stall_type, v.unit_price
FROM (VALUES
    ('HCM-NT', 'STANDARD', 5000000),
    ('HCM-NT', 'WIDE', 6500000),
    ('NT-HCM', 'STANDARD', 5100000),
    ('NT-HCM', 'WIDE', 6600000),
    ('NT-DN', 'STANDARD', 6700000),
    ('NT-DN', 'WIDE', 8700000),
    ('DN-NT', 'STANDARD', 6800000),
    ('DN-NT', 'WIDE', 8800000),
    ('HCM-DN', 'STANDARD', 11700000),
    ('HCM-DN', 'WIDE', 15200000),
    ('DN-HCM', 'STANDARD', 11900000),
    ('DN-HCM', 'WIDE', 15400000),
    ('DN-VINH', 'STANDARD', 5700000),
    ('DN-VINH', 'WIDE', 7400000),
    ('VINH-DN', 'STANDARD', 5700000),
    ('VINH-DN', 'WIDE', 7500000),
    ('VINH-HN', 'STANDARD', 3800000),
    ('VINH-HN', 'WIDE', 4900000),
    ('HN-VINH', 'STANDARD', 3800000),
    ('HN-VINH', 'WIDE', 4900000),
    ('DN-HN', 'STANDARD', 9500000),
    ('DN-HN', 'WIDE', 12400000),
    ('HN-DN', 'STANDARD', 9500000),
    ('HN-DN', 'WIDE', 12400000),
    ('HCM-HN', 'STANDARD', 21200000),
    ('HCM-HN', 'WIDE', 27600000),
    ('HN-HCM', 'STANDARD', 21400000),
    ('HN-HCM', 'WIDE', 27800000),
    ('HCM-PNH', 'STANDARD', 3200000),
    ('HCM-PNH', 'WIDE', 4200000),
    ('PNH-HCM', 'STANDARD', 3200000),
    ('PNH-HCM', 'WIDE', 4100000),
    ('DN-SVK', 'STANDARD', 6000000),
    ('DN-SVK', 'WIDE', 7800000),
    ('SVK-DN', 'STANDARD', 6000000),
    ('SVK-DN', 'WIDE', 7900000),
    ('VINH-VTE', 'STANDARD', 5600000),
    ('VINH-VTE', 'WIDE', 7300000),
    ('VTE-VINH', 'STANDARD', 5600000),
    ('VTE-VINH', 'WIDE', 7200000),
    ('DN-LAOBAO', 'STANDARD', 6000000),
    ('DN-LAOBAO', 'WIDE', 8000000),
    ('LAOBAO-DN', 'STANDARD', 6000000),
    ('LAOBAO-DN', 'WIDE', 8000000),
    ('MOCBAI-HCM', 'STANDARD', 6000000),
    ('MOCBAI-HCM', 'WIDE', 8000000)
) AS v(route_code, stall_type, unit_price)
JOIN price_list pl ON pl.name = 'Bảng giá mẫu (dev)'
JOIN billable_service s ON s.code = 'LINEHAUL_STALL'
JOIN route r ON r.code = v.route_code
ON CONFLICT ON CONSTRAINT uk_price_item DO UPDATE SET unit_price = EXCLUDED.unit_price
WHERE EXCLUDED.route_id IN (SELECT id FROM route WHERE status = 'ACTIVE');

-- Cước đón, giao, chạy thẳng: theo hạng xe và bậc km
INSERT INTO price_item (price_list_id, billable_service_id, vehicle_class_id, km_from, km_to, unit_price)
SELECT pl.id, s.id, vc.id, t.km_from, t.km_to, t.base * f.factor
FROM (VALUES ('PICKUP_KM'), ('DELIVERY_KM'), ('DIRECT_KM')) AS sv(service_code)
CROSS JOIN (VALUES (0.00, 50.00, 30000), (50.00, 200.00, 25000), (200.00, 3000.00, 20000)) AS t(km_from, km_to, base)
CROSS JOIN (VALUES ('LIGHT', 1.00), ('MEDIUM', 1.35), ('HEAVY', 1.90)) AS f(class_code, factor)
JOIN price_list pl ON pl.name = 'Bảng giá mẫu (dev)'
JOIN billable_service s ON s.code = sv.service_code
JOIN vehicle_class vc ON vc.code = f.class_code
ON CONFLICT ON CONSTRAINT uk_price_item DO NOTHING;

-- Các dịch vụ không phụ thuộc tuyến hay hạng xe
INSERT INTO price_item (price_list_id, billable_service_id, unit_price)
SELECT pl.id, s.id, v.unit_price
FROM (VALUES
          ('FUEL_TOLL', 9000),
          ('STALL_WIDE', 1500000),
          ('QUARANTINE_STAY', 800000),
          ('VET_CHECK', 500000),
          ('QUARANTINE_DOCS', 300000),
          ('GOV_FEE', 200000)
     ) AS v(service_code, unit_price)
JOIN price_list pl ON pl.name = 'Bảng giá mẫu (dev)'
JOIN billable_service s ON s.code = v.service_code
ON CONFLICT ON CONSTRAINT uk_price_item DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. Quy định và giấy tờ yêu cầu (mẫu)
-- ---------------------------------------------------------------------------
INSERT INTO country_rule (country_code, direction, permit_lead_days, certificate_lead_days, valid_from)
VALUES
    ('VN', 'INTERPROVINCE', 0, 5, DATE '2026-01-01'),
    ('KH', 'EXPORT', 14, 7, DATE '2026-01-01'),
    ('KH', 'IMPORT', 30, 7, DATE '2026-01-01'),
    ('LA', 'EXPORT', 14, 7, DATE '2026-01-01'),
    ('LA', 'IMPORT', 30, 7, DATE '2026-01-01')
ON CONFLICT DO NOTHING;

INSERT INTO document_requirement (country_rule_id, document_type_id, submitter_type, is_mandatory)
SELECT cr.id, dt.id, v.submitter_type, v.is_mandatory
FROM (VALUES
          ('VN', 'INTERPROVINCE', 'HEALTH_CERT', 'COMPANY', true),
          ('VN', 'INTERPROVINCE', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('VN', 'INTERPROVINCE', 'VACCINATION_RECORD', 'CUSTOMER', true),
          ('KH', 'EXPORT', 'EXPORT_QUARANTINE_CERT', 'COMPANY', true),
          ('KH', 'EXPORT', 'IMPORT_PERMIT', 'CONSIGNEE', true),
          ('KH', 'EXPORT', 'CUSTOMS_DECLARATION', 'COMPANY', true),
          ('KH', 'EXPORT', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('KH', 'EXPORT', 'VACCINATION_RECORD', 'CUSTOMER', true),
          ('KH', 'EXPORT', 'LAB_TEST', 'COMPANY', true),
          ('KH', 'EXPORT', 'QUARANTINE_CLEARANCE', 'COMPANY', true),
          ('KH', 'IMPORT', 'IMPORT_APPROVAL', 'COMPANY', true),
          ('KH', 'IMPORT', 'ORIGIN_QUARANTINE_CERT', 'COMPANY', true),
          ('KH', 'IMPORT', 'IMPORT_TRANSPORT_CERT', 'COMPANY', true),
          ('KH', 'IMPORT', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('KH', 'IMPORT', 'LAB_TEST', 'COMPANY', true),
          ('LA', 'EXPORT', 'EXPORT_QUARANTINE_CERT', 'COMPANY', true),
          ('LA', 'EXPORT', 'IMPORT_PERMIT', 'CONSIGNEE', true),
          ('LA', 'EXPORT', 'CUSTOMS_DECLARATION', 'COMPANY', true),
          ('LA', 'EXPORT', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('LA', 'EXPORT', 'LAB_TEST', 'COMPANY', true),
          ('LA', 'EXPORT', 'QUARANTINE_CLEARANCE', 'COMPANY', true),
          ('LA', 'EXPORT', 'ATA_CARNET', 'CUSTOMER', false),
          ('LA', 'IMPORT', 'IMPORT_APPROVAL', 'COMPANY', true),
          ('LA', 'IMPORT', 'ORIGIN_QUARANTINE_CERT', 'COMPANY', true),
          ('LA', 'IMPORT', 'IMPORT_TRANSPORT_CERT', 'COMPANY', true),
          ('LA', 'IMPORT', 'HORSE_PASSPORT', 'CUSTOMER', true)
     ) AS v(country_code, direction, doc_code, submitter_type, is_mandatory)
JOIN country_rule cr ON cr.country_code = v.country_code AND cr.direction = v.direction AND cr.valid_from = DATE '2026-01-01'
JOIN document_type dt ON dt.code = v.doc_code
ON CONFLICT ON CONSTRAINT uk_document_requirement DO NOTHING;

-- Dữ liệu mẫu đã nạp trước đây: xét nghiệm do công ty lo, đơn nhập không dùng giấy phép nhập của nước đến
UPDATE document_requirement dr SET submitter_type = 'COMPANY'
FROM document_type dt, country_rule cr
WHERE dt.id = dr.document_type_id AND cr.id = dr.country_rule_id AND dt.code = 'LAB_TEST'
  AND cr.valid_from = DATE '2026-01-01' AND dr.submitter_type = 'CUSTOMER';
UPDATE document_requirement dr SET is_mandatory = false
FROM document_type dt, country_rule cr
WHERE dt.id = dr.document_type_id AND cr.id = dr.country_rule_id AND dt.code = 'IMPORT_PERMIT'
  AND cr.direction = 'IMPORT' AND cr.valid_from = DATE '2026-01-01';


-- Hợp nhất chủ thể nộp của các yêu cầu mẫu đã được nạp trước đây.
UPDATE document_requirement dr SET submitter_type = 'COMPANY'
FROM country_rule cr
WHERE cr.id = dr.country_rule_id AND cr.valid_from = DATE '2026-01-01'
  AND cr.country_code IN ('KH', 'LA') AND dr.submitter_type = 'AGENT';

-- Giấy riêng (mẫu) theo nhóm hồ sơ tại ReferenceDataSeeder trên nhánh main.
-- RegulationPolicy chọn giấy tại cửa khẩu thay dòng chung cùng loại.
INSERT INTO document_requirement (country_rule_id, document_type_id, gate_location_id, submitter_type, is_mandatory)
SELECT cr.id, dt.id, g.id, v.submitter_type, v.is_mandatory
FROM (VALUES
    ('GATE-MOCBAI', 'CUSTOMS_DECLARATION', 'COMPANY', true),
    ('GATE-MOCBAI', 'IMPORT_PERMIT', 'CONSIGNEE', true),
    ('GATE-MOCBAI', 'QUARANTINE_CLEARANCE', 'COMPANY', true),
    ('GATE-LAOBAO', 'CUSTOMS_DECLARATION', 'COMPANY', true),
    ('GATE-LAOBAO', 'IMPORT_PERMIT', 'CONSIGNEE', true),
    ('GATE-LAOBAO', 'ATA_CARNET', 'CUSTOMER', false),
    ('GATE-CAUTREO', 'CUSTOMS_DECLARATION', 'COMPANY', true),
    ('GATE-CAUTREO', 'IMPORT_PERMIT', 'CONSIGNEE', true)
) AS v(gate_code, doc_code, submitter_type, is_mandatory)
JOIN location g ON g.code = v.gate_code
JOIN country_rule cr ON cr.country_code = g.counterpart_country_code
    AND cr.direction = 'EXPORT' AND cr.valid_from = DATE '2026-01-01'
JOIN document_type dt ON dt.code = v.doc_code
ON CONFLICT ON CONSTRAINT uk_document_requirement DO UPDATE
SET submitter_type = EXCLUDED.submitter_type, is_mandatory = EXCLUDED.is_mandatory;
