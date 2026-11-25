-- Dữ liệu MẪU cho máy dev và test. KHÔNG chạy trên môi trường thật.
-- Chỉ nạp khi FLYWAY_LOCATIONS có classpath:db/seed-dev (docker-compose và test đã bật sẵn).
-- Repeatable migration: Flyway chạy file này sau mọi migration có số, và chạy lại khi nội dung đổi,
-- nên mọi câu lệnh phải idempotent (ON CONFLICT DO NOTHING hoặc WHERE NOT EXISTS).
--
-- Tọa độ, khoảng cách, đơn giá, quy định ở đây là SỐ MẪU để phát triển và demo, chưa phải dữ liệu thật.
-- Dữ liệu nền đầy đủ sẽ bổ sung riêng (xem docs/NGU_CANH_THIET_KE_LAI.md, câu hỏi còn mở).
--
-- Tài khoản thử: mọi tài khoản dưới đây dùng chung mật khẩu thử DevSeed@2026 (chỉ dùng cho dev/test).
--   admin.dev, manager.dev, coordinator.dev, specialist.dev, driver1.dev … driver6.dev, escort1.dev … escort4.dev, customer1.dev

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

INSERT INTO hub_stall (location_id, code, stall_kind)
SELECT l.id, s.code, s.kind
FROM location l
CROSS JOIN (VALUES ('S-01', 'STANDARD'), ('S-02', 'STANDARD'), ('S-03', 'STANDARD'), ('I-01', 'ISOLATION')) s(code, kind)
WHERE l.location_type = 'HUB'
ON CONFLICT (location_id, code) DO NOTHING;

-- Công ty không vận hành nơi cách ly: ngừng trại mẫu đã nạp trước đây
UPDATE location SET status = 'INACTIVE' WHERE code = 'QF-CUCHI';

UPDATE location SET counterpart_country_code = 'KH'
WHERE code = 'GATE-MOCBAI' AND location_type = 'BORDER_GATE' AND counterpart_country_code IS NULL;
UPDATE location SET counterpart_country_code = 'LA'
WHERE code = 'GATE-LAOBAO' AND location_type = 'BORDER_GATE' AND counterpart_country_code IS NULL;

UPDATE location SET is_headquarters = true
WHERE code = 'HUB-HCM' AND NOT EXISTS (SELECT 1 FROM location WHERE is_headquarters);

-- Mỗi tỉnh thuộc đúng một hub, chọn hub gần hơn theo giờ chạy: Khánh Hòa, Đắk Lắk về trụ sở
INSERT INTO hub_service_area (hub_location_id, province_code)
SELECT l.id, p.code
FROM (VALUES
          ('HUB-HCM', 'VN-79'), ('HUB-HCM', 'VN-75'), ('HUB-HCM', 'VN-80'), ('HUB-HCM', 'VN-82'), ('HUB-HCM', 'VN-86'),
          ('HUB-HCM', 'VN-91'), ('HUB-HCM', 'VN-92'), ('HUB-HCM', 'VN-96'), ('HUB-HCM', 'VN-68'), ('HUB-HCM', 'VN-56'), ('HUB-HCM', 'VN-66'),
          ('HUB-DN', 'VN-48'), ('HUB-DN', 'VN-46'), ('HUB-DN', 'VN-51'), ('HUB-DN', 'VN-44'), ('HUB-DN', 'VN-52'),
          ('HUB-HN', 'VN-1'), ('HUB-HN', 'VN-31'), ('HUB-HN', 'VN-24'), ('HUB-HN', 'VN-4'), ('HUB-HN', 'VN-33'), ('HUB-HN', 'VN-12'), ('HUB-HN', 'VN-20'), ('HUB-HN', 'VN-15'), ('HUB-HN', 'VN-40'), ('HUB-HN', 'VN-37'), ('HUB-HN', 'VN-25'), ('HUB-HN', 'VN-22'), ('HUB-HN', 'VN-14'), ('HUB-HN', 'VN-19'), ('HUB-HN', 'VN-38'), ('HUB-HN', 'VN-8'), ('HUB-HN', 'VN-42')
     ) AS v(hub_code, province_code)
JOIN location l ON l.code = v.hub_code
JOIN province p ON p.code = v.province_code
ON CONFLICT DO NOTHING;

UPDATE hub_service_area SET hub_location_id = (SELECT id FROM location WHERE code = 'HUB-HCM')
WHERE province_code IN ('VN-56', 'VN-66') AND hub_location_id = (SELECT id FROM location WHERE code = 'HUB-DN');

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
-- 3. Xe (mọi xe thuộc trụ sở)
-- ---------------------------------------------------------------------------
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
JOIN location hub ON hub.is_headquarters
ON CONFLICT (license_plate) DO NOTHING;

UPDATE vehicle
SET standard_stall_count = adjustment.new_standard
FROM (VALUES ('51C-100.02', 'MEDIUM', 4, 2), ('51C-100.03', 'HEAVY', 7, 5))
    AS adjustment(plate, class_code, old_standard, new_standard)
JOIN vehicle_class vc ON vc.code = adjustment.class_code
WHERE vehicle.license_plate = adjustment.plate AND vehicle.vehicle_class_id = vc.id
  AND vehicle.standard_stall_count = adjustment.old_standard AND vehicle.wide_stall_count = 2;

-- ---------------------------------------------------------------------------
-- 4. Tuyến trục chính, điểm dừng, lịch chạy
-- ---------------------------------------------------------------------------
-- Giờ chạy và km tính bằng Valhalla (xe tải 10 tấn, 80 km/h, 23/11/2026), vẫn là số mẫu. Mỗi đoạn chạy tối đa 4 giờ,
-- nghỉ ít nhất 30 phút, tối đa 10 giờ lái giữa hai lần nghỉ đêm (DB_SCHEMA quy tắc #31).
INSERT INTO route (code, name, origin_location_id, destination_location_id, distance_km, estimated_duration_minutes, status)
SELECT v.code, v.name, o.id, d.id, v.distance_km, v.minutes, 'ACTIVE'
FROM (VALUES
          ('HCM-PNH', 'TP.HCM – Cửa khẩu Mộc Bài (đi Phnom Penh)', 'HUB-HCM', 'GATE-MOCBAI', 89.00, 226),
          ('MOCBAI-HCM', 'Cửa khẩu Mộc Bài – TP.HCM (từ Phnom Penh về)', 'GATE-MOCBAI', 'HUB-HCM', 103.00, 291),
          ('HCM-DN', 'TP.HCM – Đà Nẵng', 'HUB-HCM', 'HUB-DN', 975.00, 1781),
          ('DN-HCM', 'Đà Nẵng – TP.HCM', 'HUB-DN', 'HUB-HCM', 982.00, 1782),
          ('DN-LAOBAO', 'Đà Nẵng – Cửa khẩu Lao Bảo (đi Lào)', 'HUB-DN', 'GATE-LAOBAO', 294.00, 383),
          ('LAOBAO-DN', 'Cửa khẩu Lao Bảo – Đà Nẵng (từ Lào về)', 'GATE-LAOBAO', 'HUB-DN', 291.00, 411),
          ('DN-HN', 'Đà Nẵng – Hà Nội', 'HUB-DN', 'HUB-HN', 797.00, 1602),
          ('HN-DN', 'Hà Nội – Đà Nẵng', 'HUB-HN', 'HUB-DN', 798.00, 1603),
          ('HCM-HN', 'TP.HCM – Hà Nội', 'HUB-HCM', 'HUB-HN', 1772.00, 3953),
          ('HN-HCM', 'Hà Nội – TP.HCM', 'HUB-HN', 'HUB-HCM', 1780.00, 3955)
     ) AS v(code, name, origin_code, destination_code, distance_km, minutes)
JOIN location o ON o.code = v.origin_code
JOIN location d ON d.code = v.destination_code
ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, distance_km = EXCLUDED.distance_km,
    estimated_duration_minutes = EXCLUDED.estimated_duration_minutes;

-- Điểm dừng của tuyến mẫu theo bảng dưới. Lượt xe đã lập tham chiếu điểm dừng của tuyến (fk_trip_stop_route_stop)
-- nên cập nhật tại chỗ, không xóa rồi nạp lại: xóa thì backend không khởi động được trên máy đã có lượt trục.
INSERT INTO route_stop (route_id, sequence_number, location_id, stop_type, arrival_offset_minutes, dwell_minutes)
SELECT r.id, v.sequence_number, l.id, v.stop_type, v.arrival_offset_minutes, v.dwell_minutes
FROM (VALUES
          ('HCM-PNH', 1, 'HUB-HCM', 'HUB', 0, 30),
          ('HCM-PNH', 2, 'REST-TRANGBANG', 'REST', 160, 30),
          ('HCM-PNH', 3, 'GATE-MOCBAI', 'BORDER', 226, 120),
          ('MOCBAI-HCM', 1, 'GATE-MOCBAI', 'BORDER', 0, 60),
          ('MOCBAI-HCM', 2, 'REST-TRANGBANG', 'REST', 95, 30),
          ('MOCBAI-HCM', 3, 'HUB-HCM', 'HUB', 291, 0),
          ('HCM-DN', 1, 'HUB-HCM', 'HUB', 0, 30),
          ('HCM-DN', 2, 'REST-PHANTHIET', 'REST', 177, 30),
          ('HCM-DN', 3, 'REST-PHANRANG', 'REST', 343, 30),
          ('HCM-DN', 4, 'REST-NHATRANG', 'VITAL_CHECK', 492, 45),
          ('HCM-DN', 5, 'REST-TUYHOA', 'REST', 683, 600),
          ('HCM-DN', 6, 'REST-QUYNHON', 'REST', 1392, 30),
          ('HCM-DN', 7, 'REST-QUANGNGAI', 'REST', 1617, 30),
          ('HCM-DN', 8, 'HUB-DN', 'HUB', 1781, 0),
          ('DN-HCM', 1, 'HUB-DN', 'HUB', 0, 30),
          ('DN-HCM', 2, 'REST-QUANGNGAI', 'REST', 166, 30),
          ('DN-HCM', 3, 'REST-QUYNHON', 'REST', 385, 30),
          ('DN-HCM', 4, 'REST-TUYHOA', 'REST', 525, 30),
          ('DN-HCM', 5, 'REST-NHATRANG', 'REST', 698, 600),
          ('DN-HCM', 6, 'REST-PHANRANG', 'REST', 1420, 30),
          ('DN-HCM', 7, 'REST-PHANTHIET', 'REST', 1589, 30),
          ('DN-HCM', 8, 'HUB-HCM', 'HUB', 1782, 0),
          ('DN-LAOBAO', 1, 'HUB-DN', 'HUB', 0, 30),
          ('DN-LAOBAO', 2, 'REST-HUE', 'REST', 129, 30),
          ('DN-LAOBAO', 3, 'REST-DONGHA', 'REST', 262, 30),
          ('DN-LAOBAO', 4, 'GATE-LAOBAO', 'BORDER', 383, 120),
          ('LAOBAO-DN', 1, 'GATE-LAOBAO', 'BORDER', 0, 60),
          ('LAOBAO-DN', 2, 'REST-DONGHA', 'REST', 152, 30),
          ('LAOBAO-DN', 3, 'REST-HUE', 'REST', 281, 30),
          ('LAOBAO-DN', 4, 'HUB-DN', 'HUB', 411, 0),
          ('DN-HN', 1, 'HUB-DN', 'HUB', 0, 30),
          ('DN-HN', 2, 'REST-HUE', 'REST', 134, 30),
          ('DN-HN', 3, 'REST-DONGHA', 'REST', 259, 30),
          ('DN-HN', 4, 'REST-DONGHOI', 'REST', 398, 30),
          ('DN-HN', 5, 'REST-VINH', 'REST', 624, 600),
          ('DN-HN', 6, 'REST-THANHHOA', 'REST', 1384, 30),
          ('DN-HN', 7, 'HUB-HN', 'HUB', 1602, 0),
          ('HN-DN', 1, 'HUB-HN', 'HUB', 0, 30),
          ('HN-DN', 2, 'REST-THANHHOA', 'REST', 217, 30),
          ('HN-DN', 3, 'REST-VINH', 'REST', 408, 30),
          ('HN-DN', 4, 'REST-DONGHOI', 'REST', 633, 600),
          ('HN-DN', 5, 'REST-DONGHA', 'REST', 1342, 30),
          ('HN-DN', 6, 'REST-HUE', 'REST', 1467, 30),
          ('HN-DN', 7, 'HUB-DN', 'HUB', 1603, 0),
          ('HCM-HN', 1, 'HUB-HCM', 'HUB', 0, 30),
          ('HCM-HN', 2, 'REST-PHANTHIET', 'REST', 177, 30),
          ('HCM-HN', 3, 'REST-PHANRANG', 'REST', 343, 30),
          ('HCM-HN', 4, 'REST-NHATRANG', 'VITAL_CHECK', 492, 45),
          ('HCM-HN', 5, 'REST-TUYHOA', 'REST', 683, 600),
          ('HCM-HN', 6, 'REST-QUYNHON', 'REST', 1392, 30),
          ('HCM-HN', 7, 'REST-QUANGNGAI', 'REST', 1617, 30),
          ('HCM-HN', 8, 'HUB-DN', 'REST', 1781, 30),
          ('HCM-HN', 9, 'REST-HUE', 'REST', 1915, 600),
          ('HCM-HN', 10, 'REST-DONGHA', 'REST', 2610, 30),
          ('HCM-HN', 11, 'REST-DONGHOI', 'REST', 2749, 30),
          ('HCM-HN', 12, 'REST-VINH', 'REST', 2975, 30),
          ('HCM-HN', 13, 'REST-THANHHOA', 'REST', 3165, 600),
          ('HCM-HN', 14, 'HUB-HN', 'HUB', 3953, 0),
          ('HN-HCM', 1, 'HUB-HN', 'HUB', 0, 30),
          ('HN-HCM', 2, 'REST-THANHHOA', 'REST', 217, 30),
          ('HN-HCM', 3, 'REST-VINH', 'REST', 408, 30),
          ('HN-HCM', 4, 'REST-DONGHOI', 'REST', 633, 600),
          ('HN-HCM', 5, 'REST-DONGHA', 'REST', 1342, 30),
          ('HN-HCM', 6, 'REST-HUE', 'REST', 1467, 30),
          ('HN-HCM', 7, 'HUB-DN', 'REST', 1603, 30),
          ('HN-HCM', 8, 'REST-QUANGNGAI', 'REST', 1769, 600),
          ('HN-HCM', 9, 'REST-QUYNHON', 'REST', 2558, 30),
          ('HN-HCM', 10, 'REST-TUYHOA', 'REST', 2698, 30),
          ('HN-HCM', 11, 'REST-NHATRANG', 'VITAL_CHECK', 2871, 30),
          ('HN-HCM', 12, 'REST-PHANRANG', 'REST', 3023, 600),
          ('HN-HCM', 13, 'REST-PHANTHIET', 'REST', 3762, 30),
          ('HN-HCM', 14, 'HUB-HCM', 'HUB', 3955, 0)
     ) AS v(route_code, sequence_number, location_code, stop_type, arrival_offset_minutes, dwell_minutes)
JOIN route r ON r.code = v.route_code
JOIN location l ON l.code = v.location_code
ON CONFLICT (route_id, sequence_number) DO UPDATE SET location_id = EXCLUDED.location_id, stop_type = EXCLUDED.stop_type,
    arrival_offset_minutes = EXCLUDED.arrival_offset_minutes, dwell_minutes = EXCLUDED.dwell_minutes;

-- Tuyến mẫu rút ngắn thì bỏ điểm thừa chưa lượt xe nào dùng
DELETE FROM route_stop rs USING route r
WHERE rs.route_id = r.id
  AND rs.sequence_number > CASE r.code WHEN 'HCM-PNH' THEN 3 WHEN 'MOCBAI-HCM' THEN 3 WHEN 'HCM-DN' THEN 8
                                       WHEN 'DN-HCM' THEN 8 WHEN 'DN-LAOBAO' THEN 4 WHEN 'LAOBAO-DN' THEN 4
                                       WHEN 'DN-HN' THEN 7 WHEN 'HN-DN' THEN 7 WHEN 'HCM-HN' THEN 14 WHEN 'HN-HCM' THEN 14 ELSE 32767 END
  AND NOT EXISTS (SELECT 1 FROM trip_stop ts WHERE ts.route_id = rs.route_id AND ts.route_stop_sequence_number = rs.sequence_number);

-- Mỗi tuyến một chuyến mỗi tuần: thứ Hai HCM và Mộc Bài, thứ Ba đi Đà Nẵng, thứ Sáu đi Lao Bảo, thứ Bảy về Đà Nẵng,
-- Chủ nhật về TP.HCM
INSERT INTO departure_schedule (route_id, departure_time, booking_cutoff_hours, stall_capacity, valid_from, status)
SELECT r.id, v.departure_time, 72, 9, DATE '2026-11-01', 'ACTIVE'
FROM (VALUES
          ('HCM-PNH', TIME '06:00'),
          ('MOCBAI-HCM', TIME '14:00'),
          ('HCM-DN', TIME '05:00'),
          ('DN-LAOBAO', TIME '05:00'),
          ('LAOBAO-DN', TIME '07:00'),
          ('DN-HCM', TIME '05:00'),
          ('DN-HN', TIME '05:00'),
          ('HN-DN', TIME '05:00'),
          ('HCM-HN', TIME '05:00'),
          ('HN-HCM', TIME '05:00')
     ) AS v(route_code, departure_time)
JOIN route r ON r.code = v.route_code
WHERE NOT EXISTS (SELECT 1 FROM departure_schedule s WHERE s.route_id = r.id AND s.departure_time = v.departure_time);

UPDATE departure_schedule s SET booking_cutoff_hours = 72
FROM route r WHERE r.id = s.route_id AND r.code = 'DN-HCM' AND s.departure_time = TIME '05:00' AND s.booking_cutoff_hours = 48;

-- Bỏ các ngày chạy thứ hai trong tuần của lịch mẫu cũ. Chuyến đã sinh không bị xóa
DELETE FROM departure_schedule_day dd USING departure_schedule s, route r
WHERE dd.departure_schedule_id = s.id AND s.route_id = r.id
  AND ((r.code IN ('HCM-PNH', 'MOCBAI-HCM') AND dd.day_of_week = 4) OR (r.code = 'DN-HCM' AND dd.day_of_week = 4))
  AND s.departure_time IN (TIME '05:00', TIME '06:00', TIME '14:00') AND s.valid_from = DATE '2026-11-01';

INSERT INTO departure_schedule_day (departure_schedule_id, day_of_week)
SELECT s.id, v.day_of_week
FROM (VALUES ('HCM-PNH', TIME '06:00', 1), ('MOCBAI-HCM', TIME '14:00', 1), ('HCM-DN', TIME '05:00', 2), ('DN-LAOBAO', TIME '05:00', 5), ('LAOBAO-DN', TIME '07:00', 6), ('DN-HCM', TIME '05:00', 7), ('DN-HN', TIME '05:00', 4), ('HN-DN', TIME '05:00', 6), ('HCM-HN', TIME '05:00', 3), ('HN-HCM', TIME '05:00', 1)) AS v(route_code, departure_time, day_of_week)
JOIN route r ON r.code = v.route_code
JOIN departure_schedule s ON s.route_id = r.id AND s.departure_time = v.departure_time
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Bảng giá mẫu
-- ---------------------------------------------------------------------------
INSERT INTO price_list (name, currency_code, margin_rate, fuel_buffer_rate, deposit_rate, valid_from, status)
SELECT 'Bảng giá mẫu (dev)', 'VND', 0.0500, 0.0500, 0.3000, DATE '2026-01-01', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM price_list WHERE name = 'Bảng giá mẫu (dev)');

-- Cước trục chính theo tuyến và loại khoang
INSERT INTO price_item (price_list_id, billable_service_id, route_id, stall_type_code, unit_price)
SELECT pl.id, s.id, r.id, v.stall_type, v.unit_price
FROM (VALUES
          ('HCM-PNH', 'STANDARD', 6000000), ('HCM-PNH', 'WIDE', 8000000),
          ('DN-LAOBAO', 'STANDARD', 6000000), ('DN-LAOBAO', 'WIDE', 8000000),
          ('LAOBAO-DN', 'STANDARD', 6000000), ('LAOBAO-DN', 'WIDE', 8000000),
          ('HCM-DN', 'STANDARD', 12000000), ('HCM-DN', 'WIDE', 15000000),
          ('DN-HCM', 'STANDARD', 12000000), ('DN-HCM', 'WIDE', 15000000),
          ('MOCBAI-HCM', 'STANDARD', 6000000), ('MOCBAI-HCM', 'WIDE', 8000000),
          ('DN-HN', 'STANDARD', 10000000), ('DN-HN', 'WIDE', 13000000),
          ('HN-DN', 'STANDARD', 10000000), ('HN-DN', 'WIDE', 13000000),
          ('HCM-HN', 'STANDARD', 20000000), ('HCM-HN', 'WIDE', 26000000),
          ('HN-HCM', 'STANDARD', 20000000), ('HN-HCM', 'WIDE', 26000000)
     ) AS v(route_code, stall_type, unit_price)
JOIN price_list pl ON pl.name = 'Bảng giá mẫu (dev)'
JOIN billable_service s ON s.code = 'LINEHAUL_STALL'
JOIN route r ON r.code = v.route_code
ON CONFLICT ON CONSTRAINT uk_price_item DO NOTHING;

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
          ('KH', 'IMPORT', 'IMPORT_APPROVAL', 'AGENT', true),
          ('KH', 'IMPORT', 'ORIGIN_QUARANTINE_CERT', 'AGENT', true),
          ('KH', 'IMPORT', 'IMPORT_TRANSPORT_CERT', 'AGENT', true),
          ('KH', 'IMPORT', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('KH', 'IMPORT', 'LAB_TEST', 'COMPANY', true),
          ('LA', 'EXPORT', 'EXPORT_QUARANTINE_CERT', 'COMPANY', true),
          ('LA', 'EXPORT', 'IMPORT_PERMIT', 'CONSIGNEE', true),
          ('LA', 'EXPORT', 'CUSTOMS_DECLARATION', 'COMPANY', true),
          ('LA', 'EXPORT', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('LA', 'EXPORT', 'LAB_TEST', 'COMPANY', true),
          ('LA', 'EXPORT', 'QUARANTINE_CLEARANCE', 'COMPANY', true),
          ('LA', 'EXPORT', 'ATA_CARNET', 'CUSTOMER', false),
          ('LA', 'IMPORT', 'IMPORT_APPROVAL', 'AGENT', true),
          ('LA', 'IMPORT', 'ORIGIN_QUARANTINE_CERT', 'AGENT', true),
          ('LA', 'IMPORT', 'IMPORT_TRANSPORT_CERT', 'AGENT', true),
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
