-- Dữ liệu MẪU cho máy dev và test. KHÔNG chạy trên môi trường thật.
-- Chỉ nạp khi FLYWAY_LOCATIONS có classpath:db/seed-dev (docker-compose và test đã bật sẵn).
-- Repeatable migration: Flyway chạy file này sau mọi migration có số, và chạy lại khi nội dung đổi,
-- nên mọi câu lệnh phải idempotent (ON CONFLICT DO NOTHING hoặc WHERE NOT EXISTS).
--
-- Tọa độ, khoảng cách, đơn giá, quy định ở đây là SỐ MẪU để phát triển và demo, chưa phải dữ liệu thật.
-- Dữ liệu nền đầy đủ sẽ bổ sung riêng (xem docs/NGU_CANH_THIET_KE_LAI.md, câu hỏi còn mở).
--
-- Tài khoản thử: mọi tài khoản dưới đây dùng chung mật khẩu thử DevSeed@2026 (chỉ dùng cho dev/test).
--   admin.dev, manager.dev, coordinator.dev, specialist.dev, driver1.dev, driver2.dev, escort1.dev, customer1.dev

-- ---------------------------------------------------------------------------
-- 1. Điểm mạng lưới và vùng phục vụ
-- ---------------------------------------------------------------------------
INSERT INTO location (code, name, type, country_code, province_code, address, latitude, longitude, opening_minutes, closing_minutes, contact_phone, status) VALUES
    ('HUB-HCM', 'Trụ sở TP.HCM', 'HUB', 'VN', 'VN-79', 'Phường Long Trường, TP.HCM', 10.805000, 106.815000, NULL, NULL, '0900000100', 'ACTIVE'),
    ('HUB-DN', 'Hub vệ tinh Đà Nẵng', 'HUB', 'VN', 'VN-48', 'Phường Hòa Khánh, TP. Đà Nẵng', 16.075000, 108.150000, NULL, NULL, '0900000200', 'ACTIVE'),
    ('QF-CUCHI', 'Trại cách ly Củ Chi', 'QUARANTINE_FARM', 'VN', 'VN-79', 'Xã Củ Chi, TP.HCM', 10.973000, 106.493000, 420, 1080, '0900000300', 'ACTIVE'),
    ('REST-TRANGBANG', 'Trạm nghỉ Trảng Bàng', 'REST_STOP', 'VN', 'VN-80', 'Phường Trảng Bàng, Tây Ninh', 11.033000, 106.360000, NULL, NULL, NULL, 'ACTIVE'),
    ('GATE-MOCBAI', 'Cửa khẩu quốc tế Mộc Bài - Bavet', 'BORDER_GATE', 'VN', 'VN-80', 'Xã Bến Cầu, Tây Ninh', 11.070000, 106.200000, 420, 1320, NULL, 'ACTIVE'),
    ('REST-PHANTHIET', 'Trạm nghỉ Phan Thiết', 'REST_STOP', 'VN', 'VN-68', 'Phường Phan Thiết, Lâm Đồng', 10.933000, 108.100000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-NHATRANG', 'Trạm nghỉ Nha Trang', 'REST_STOP', 'VN', 'VN-56', 'Phường Nha Trang, Khánh Hòa', 12.238000, 109.196000, NULL, NULL, NULL, 'ACTIVE'),
    ('REST-QUYNHON', 'Trạm nghỉ Quy Nhơn', 'REST_STOP', 'VN', 'VN-52', 'Phường Quy Nhơn, Gia Lai', 13.776000, 109.224000, NULL, NULL, NULL, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;

-- Mỗi tỉnh thuộc đúng một hub. Miền Nam về trụ sở, miền Trung về hub vệ tinh Đà Nẵng
INSERT INTO hub_service_area (hub_location_id, province_code)
SELECT l.id, p.code
FROM (VALUES
          ('HUB-HCM', 'VN-79'), ('HUB-HCM', 'VN-75'), ('HUB-HCM', 'VN-80'), ('HUB-HCM', 'VN-82'), ('HUB-HCM', 'VN-86'),
          ('HUB-HCM', 'VN-91'), ('HUB-HCM', 'VN-92'), ('HUB-HCM', 'VN-96'), ('HUB-HCM', 'VN-68'),
          ('HUB-DN', 'VN-48'), ('HUB-DN', 'VN-46'), ('HUB-DN', 'VN-51'), ('HUB-DN', 'VN-44'), ('HUB-DN', 'VN-52'),
          ('HUB-DN', 'VN-56'), ('HUB-DN', 'VN-66')
     ) AS v(hub_code, province_code)
JOIN location l ON l.code = v.hub_code
JOIN province p ON p.code = v.province_code
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Tài khoản, nhân viên, khách hàng
-- ---------------------------------------------------------------------------
INSERT INTO app_user (username, email, password_hash, email_verified, preferred_language, status) VALUES
    ('admin.dev', 'admin.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE'),
    ('manager.dev', 'manager.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE'),
    ('coordinator.dev', 'coordinator.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE'),
    ('specialist.dev', 'specialist.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE'),
    ('driver1.dev', 'driver1.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE'),
    ('driver2.dev', 'driver2.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE'),
    ('escort1.dev', 'escort1.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE'),
    ('customer1.dev', 'customer1.dev@racehorse.test', '$2a$10$BKYE5Xy.Gp4gx5ML9MmX9OZyFK5RbMdiFRLnurHc5nnSpjcgSwOPS', true, 'vi', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_role (user_id, role_code)
SELECT u.id, v.role_code
FROM (VALUES
          ('admin.dev', 'ADMIN'), ('manager.dev', 'MANAGER'), ('coordinator.dev', 'COORDINATOR'),
          ('specialist.dev', 'SPECIALIST'), ('driver1.dev', 'DRIVER'), ('driver2.dev', 'DRIVER'),
          ('escort1.dev', 'ESCORT'), ('customer1.dev', 'CUSTOMER')
     ) AS v(username, role_code)
JOIN app_user u ON u.username = v.username
ON CONFLICT DO NOTHING;

INSERT INTO staff (user_id, staff_code, full_name, phone, hire_date, employment_status, home_hub_id, driver_license_class, driver_license_expiry)
SELECT u.id, v.staff_code, v.full_name, v.phone, DATE '2026-01-01', 'ACTIVE', hub.id, v.license_class, v.license_expiry
FROM (VALUES
          ('admin.dev', 'ADM-001', 'Quản trị viên (mẫu)', '0900000001', NULL, NULL::date),
          ('manager.dev', 'MGR-001', 'Trần Quản Lý (mẫu)', '0900000002', NULL, NULL::date),
          ('coordinator.dev', 'CRD-001', 'Lê Điều Phối (mẫu)', '0900000003', NULL, NULL::date),
          ('specialist.dev', 'SPC-001', 'Phạm Chuyên Viên (mẫu)', '0900000004', NULL, NULL::date),
          ('driver1.dev', 'DRV-001', 'Võ Tài Xế Một (mẫu)', '0900000005', 'C', DATE '2030-12-31'),
          ('driver2.dev', 'DRV-002', 'Đỗ Tài Xế Hai (mẫu)', '0900000006', 'C', DATE '2030-12-31'),
          ('escort1.dev', 'ESC-001', 'Huỳnh Chăm Ngựa (mẫu)', '0900000007', NULL, NULL::date)
     ) AS v(username, staff_code, full_name, phone, license_class, license_expiry)
JOIN app_user u ON u.username = v.username
JOIN location hub ON hub.code = 'HUB-HCM'
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO party (kind, display_name, phone, email, address, country_code, user_id, status)
SELECT 'PERSON', 'Nguyễn Văn Khách (mẫu)', '0909000001', u.email, 'Phường Thủ Dầu Một, TP.HCM', 'VN', u.id, 'ACTIVE'
FROM app_user u
WHERE u.username = 'customer1.dev'
ON CONFLICT (user_id) DO NOTHING;

-- Đối tác: không có tài khoản đăng nhập
INSERT INTO party (kind, display_name, phone, email, address, country_code, status)
SELECT 'ORGANIZATION', v.display_name, v.phone, NULL, v.address, v.country_code, 'ACTIVE'
FROM (VALUES
          ('Đại lý vận tải Campuchia (mẫu)', '0855000001', 'Phnom Penh, Campuchia', 'KH'),
          ('Trại cách ly Củ Chi (mẫu)', '0900000300', 'Xã Củ Chi, TP.HCM', 'VN'),
          ('Phòng khám thú y ngựa (mẫu)', '0900000400', 'TP.HCM', 'VN'),
          ('Đội cứu hộ xe tải (mẫu)', '0900000500', 'TP.HCM', 'VN'),
          ('Công ty bảo hiểm (mẫu)', '0900000600', 'TP.HCM', 'VN'),
          ('Chuồng gửi ngựa Đà Nẵng (mẫu)', '0900000700', 'TP. Đà Nẵng', 'VN')
     ) AS v(display_name, phone, address, country_code)
WHERE NOT EXISTS (SELECT 1 FROM party p WHERE p.display_name = v.display_name);

INSERT INTO party_role (party_id, role_type, valid_from)
SELECT p.id, v.role_type, DATE '2026-01-01'
FROM (VALUES
          ('Nguyễn Văn Khách (mẫu)', 'CUSTOMER'),
          ('Đại lý vận tải Campuchia (mẫu)', 'DEST_AGENT'),
          ('Trại cách ly Củ Chi (mẫu)', 'QUARANTINE_FARM'),
          ('Phòng khám thú y ngựa (mẫu)', 'VET'),
          ('Đội cứu hộ xe tải (mẫu)', 'RESCUE'),
          ('Công ty bảo hiểm (mẫu)', 'INSURER'),
          ('Chuồng gửi ngựa Đà Nẵng (mẫu)', 'STABLE')
     ) AS v(display_name, role_type)
JOIN party p ON p.display_name = v.display_name
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Xe (mọi xe thuộc trụ sở)
-- ---------------------------------------------------------------------------
INSERT INTO vehicle (license_plate, vehicle_class_id, standard_stalls, wide_stalls, home_hub_id, registration_expiry, status)
SELECT v.plate, vc.id, v.std, v.wide, hub.id, DATE '2027-12-31', 'ACTIVE'
FROM (VALUES
          ('51C-100.01', 'LIGHT', 2, 0),
          ('51C-100.02', 'MEDIUM', 4, 2),
          ('51C-100.03', 'HEAVY', 7, 2),
          ('51C-100.04', 'HEAVY', 9, 0)
     ) AS v(plate, class_code, std, wide)
JOIN vehicle_class vc ON vc.code = v.class_code
JOIN location hub ON hub.code = 'HUB-HCM'
ON CONFLICT (license_plate) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Tuyến trục chính, điểm dừng, lịch chạy
-- ---------------------------------------------------------------------------
INSERT INTO route (code, name, origin_hub_id, destination_location_id, distance_km, est_duration_minutes, status)
SELECT v.code, v.name, o.id, d.id, v.distance_km, v.minutes, 'ACTIVE'
FROM (VALUES
          ('HCM-PNH', 'TP.HCM – Cửa khẩu Mộc Bài (đi Phnom Penh)', 'HUB-HCM', 'GATE-MOCBAI', 75.00, 120),
          ('HCM-DN', 'TP.HCM – Đà Nẵng', 'HUB-HCM', 'HUB-DN', 960.00, 1260),
          ('DN-HCM', 'Đà Nẵng – TP.HCM', 'HUB-DN', 'HUB-HCM', 960.00, 1260)
     ) AS v(code, name, origin_code, destination_code, distance_km, minutes)
JOIN location o ON o.code = v.origin_code
JOIN location d ON d.code = v.destination_code
ON CONFLICT (code) DO NOTHING;

INSERT INTO route_stop (route_id, seq, location_id, stop_type, offset_minutes, dwell_minutes)
SELECT r.id, v.seq, l.id, v.stop_type, v.offset_minutes, v.dwell_minutes
FROM (VALUES
          ('HCM-PNH', 1, 'HUB-HCM', 'HUB', 0, 30),
          ('HCM-PNH', 2, 'REST-TRANGBANG', 'REST', 70, 20),
          ('HCM-PNH', 3, 'GATE-MOCBAI', 'BORDER', 120, 120),
          ('HCM-DN', 1, 'HUB-HCM', 'HUB', 0, 30),
          ('HCM-DN', 2, 'REST-PHANTHIET', 'REST', 240, 30),
          ('HCM-DN', 3, 'REST-NHATRANG', 'VITAL_CHECK', 540, 45),
          ('HCM-DN', 4, 'REST-QUYNHON', 'REST', 900, 30),
          ('HCM-DN', 5, 'HUB-DN', 'HUB', 1260, 0),
          ('DN-HCM', 1, 'HUB-DN', 'HUB', 0, 30),
          ('DN-HCM', 2, 'REST-QUYNHON', 'REST', 330, 30),
          ('DN-HCM', 3, 'REST-NHATRANG', 'VITAL_CHECK', 690, 45),
          ('DN-HCM', 4, 'REST-PHANTHIET', 'REST', 1005, 30),
          ('DN-HCM', 5, 'HUB-HCM', 'HUB', 1260, 0)
     ) AS v(route_code, seq, location_code, stop_type, offset_minutes, dwell_minutes)
JOIN route r ON r.code = v.route_code
JOIN location l ON l.code = v.location_code
ON CONFLICT DO NOTHING;

-- Lịch chạy: HCM-PNH thứ Hai và thứ Năm; HCM-DN thứ Ba; DN-HCM thứ Năm để xe quay về trụ sở
INSERT INTO departure_schedule (route_id, departure_time, booking_cutoff_hours, capacity_stalls, valid_from, status)
SELECT r.id, v.departure_time, v.cutoff_hours, 9, DATE '2026-11-01', 'ACTIVE'
FROM (VALUES
          ('HCM-PNH', TIME '06:00', 72),
          ('HCM-DN', TIME '05:00', 72),
          ('DN-HCM', TIME '05:00', 48)
     ) AS v(route_code, departure_time, cutoff_hours)
JOIN route r ON r.code = v.route_code
WHERE NOT EXISTS (SELECT 1 FROM departure_schedule s WHERE s.route_id = r.id AND s.departure_time = v.departure_time);

INSERT INTO departure_schedule_day (schedule_id, weekday)
SELECT s.id, v.weekday
FROM (VALUES ('HCM-PNH', 1), ('HCM-PNH', 4), ('HCM-DN', 2), ('DN-HCM', 4)) AS v(route_code, weekday)
JOIN route r ON r.code = v.route_code
JOIN departure_schedule s ON s.route_id = r.id
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. Bảng giá mẫu
-- ---------------------------------------------------------------------------
INSERT INTO price_list (name, currency, margin_rate, fuel_buffer_rate, deposit_rate, valid_from, status)
SELECT 'Bảng giá mẫu (dev)', 'VND', 0.0500, 0.0500, 0.3000, DATE '2026-01-01', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM price_list WHERE name = 'Bảng giá mẫu (dev)');

-- Cước trục chính theo tuyến và loại khoang
INSERT INTO price_item (price_list_id, service_id, route_id, stall_type, unit_price)
SELECT pl.id, s.id, r.id, v.stall_type, v.unit_price
FROM (VALUES
          ('HCM-PNH', 'STANDARD', 6000000), ('HCM-PNH', 'WIDE', 8000000),
          ('HCM-DN', 'STANDARD', 12000000), ('HCM-DN', 'WIDE', 15000000),
          ('DN-HCM', 'STANDARD', 12000000), ('DN-HCM', 'WIDE', 15000000)
     ) AS v(route_code, stall_type, unit_price)
JOIN price_list pl ON pl.name = 'Bảng giá mẫu (dev)'
JOIN service s ON s.code = 'LINEHAUL_STALL'
JOIN route r ON r.code = v.route_code
ON CONFLICT ON CONSTRAINT uk_price_item DO NOTHING;

-- Cước đón, giao, chạy thẳng: theo hạng xe và bậc km
INSERT INTO price_item (price_list_id, service_id, vehicle_class_id, km_from, km_to, unit_price)
SELECT pl.id, s.id, vc.id, t.km_from, t.km_to, t.base * f.factor
FROM (VALUES ('PICKUP_KM'), ('DELIVERY_KM'), ('DIRECT_KM')) AS sv(service_code)
CROSS JOIN (VALUES (0.00, 50.00, 30000), (50.00, 200.00, 25000), (200.00, 3000.00, 20000)) AS t(km_from, km_to, base)
CROSS JOIN (VALUES ('LIGHT', 1.00), ('MEDIUM', 1.35), ('HEAVY', 1.90)) AS f(class_code, factor)
JOIN price_list pl ON pl.name = 'Bảng giá mẫu (dev)'
JOIN service s ON s.code = sv.service_code
JOIN vehicle_class vc ON vc.code = f.class_code
ON CONFLICT ON CONSTRAINT uk_price_item DO NOTHING;

-- Các dịch vụ không phụ thuộc tuyến hay hạng xe
INSERT INTO price_item (price_list_id, service_id, unit_price)
SELECT pl.id, s.id, v.unit_price
FROM (VALUES
          ('FUEL_TOLL', 9000),
          ('STALL_WIDE', 1500000),
          ('QUARANTINE_STAY', 800000),
          ('FEEDING', 150000),
          ('VET_CHECK', 500000),
          ('QUARANTINE_DOCS', 300000),
          ('GOV_FEE', 200000)
     ) AS v(service_code, unit_price)
JOIN price_list pl ON pl.name = 'Bảng giá mẫu (dev)'
JOIN service s ON s.code = v.service_code
ON CONFLICT ON CONSTRAINT uk_price_item DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. Quy định và giấy tờ yêu cầu (mẫu)
-- ---------------------------------------------------------------------------
INSERT INTO country_rule (country_code, direction, permit_lead_days, cert_lead_days, quarantine_days, valid_from)
VALUES
    ('VN', 'INTERPROVINCE', 0, 5, 0, DATE '2026-01-01'),
    ('KH', 'EXPORT', 14, 7, 7, DATE '2026-01-01'),
    ('KH', 'IMPORT', 30, 7, 30, DATE '2026-01-01'),
    ('LA', 'EXPORT', 14, 7, 7, DATE '2026-01-01'),
    ('LA', 'IMPORT', 30, 7, 30, DATE '2026-01-01')
ON CONFLICT DO NOTHING;

INSERT INTO document_requirement (rule_id, document_type_id, submitted_by, is_mandatory)
SELECT cr.id, dt.id, v.submitted_by, v.is_mandatory
FROM (VALUES
          ('VN', 'INTERPROVINCE', 'HEALTH_CERT', 'COMPANY', true),
          ('VN', 'INTERPROVINCE', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('VN', 'INTERPROVINCE', 'VACCINATION_RECORD', 'CUSTOMER', true),
          ('KH', 'EXPORT', 'EXPORT_QUARANTINE_CERT', 'COMPANY', true),
          ('KH', 'EXPORT', 'IMPORT_PERMIT', 'CONSIGNEE', true),
          ('KH', 'EXPORT', 'CUSTOMS_DECLARATION', 'COMPANY', true),
          ('KH', 'EXPORT', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('KH', 'EXPORT', 'VACCINATION_RECORD', 'CUSTOMER', true),
          ('KH', 'EXPORT', 'LAB_TEST', 'CUSTOMER', true),
          ('KH', 'IMPORT', 'IMPORT_PERMIT', 'COMPANY', true),
          ('KH', 'IMPORT', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('KH', 'IMPORT', 'LAB_TEST', 'CUSTOMER', true),
          ('LA', 'EXPORT', 'EXPORT_QUARANTINE_CERT', 'COMPANY', true),
          ('LA', 'EXPORT', 'IMPORT_PERMIT', 'CONSIGNEE', true),
          ('LA', 'EXPORT', 'CUSTOMS_DECLARATION', 'COMPANY', true),
          ('LA', 'EXPORT', 'HORSE_PASSPORT', 'CUSTOMER', true),
          ('LA', 'EXPORT', 'LAB_TEST', 'CUSTOMER', true),
          ('LA', 'EXPORT', 'ATA_CARNET', 'CUSTOMER', false),
          ('LA', 'IMPORT', 'IMPORT_PERMIT', 'COMPANY', true),
          ('LA', 'IMPORT', 'HORSE_PASSPORT', 'CUSTOMER', true)
     ) AS v(country_code, direction, doc_code, submitted_by, is_mandatory)
JOIN country_rule cr ON cr.country_code = v.country_code AND cr.direction = v.direction AND cr.valid_from = DATE '2026-01-01'
JOIN document_type dt ON dt.code = v.doc_code
ON CONFLICT ON CONSTRAINT uk_document_requirement DO NOTHING;
