-- Dữ liệu MẪU cho máy dev và test: chuyến trong 6 tuần tới và 3 đơn đã CONFIRMED để luồng vận hành (B1 trở đi) điều độ.
-- KHÔNG chạy trên môi trường thật. Chạy sau R__dev_seed_base.sql (Flyway chạy file R__ theo thứ tự tên).
-- Ngày tính theo ngày chạy seed lần đầu. Đơn giá chép từ "Bảng giá mẫu (dev)". Tổng và tiền cọc ở đây chỉ là dữ liệu mẫu;
-- trong ứng dụng, QuotationService và DepositPolicy mới là nơi tính.

-- 1. Bổ sung chuyến còn thiếu cho 6 tuần tới theo lịch đội xe mới. Giữ nguyên chuyến đã sinh.
DO $m2$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'departure_schedule' AND column_name = 'base_vehicle_class_id') THEN
        EXECUTE $q$
WITH inserted AS (
INSERT INTO departure (departure_schedule_id, route_id, departure_date, planned_departure_at, booking_cutoff_at, stall_capacity, booked_stall_count, status)
SELECT s.id,
       s.route_id,
       d::date,
       (d::date + s.departure_time) AT TIME ZONE 'Asia/Ho_Chi_Minh',
       ((d::date + s.departure_time) AT TIME ZONE 'Asia/Ho_Chi_Minh') - make_interval(hours => s.booking_cutoff_hours),
       s.base_vehicle_count * vc.max_stall_count,
       0,
       'OPEN'
FROM departure_schedule s
JOIN vehicle_class vc ON vc.id = s.base_vehicle_class_id
JOIN departure_schedule_day sd ON sd.departure_schedule_id = s.id
CROSS JOIN generate_series(current_date + 1, current_date + 42, interval '1 day') AS d
WHERE s.status = 'ACTIVE' AND d::date >= s.valid_from AND (s.valid_to IS NULL OR d::date <= s.valid_to)
  AND extract(isodow FROM d)::smallint = sd.day_of_week
ON CONFLICT ON CONSTRAINT uk_departure_departure_schedule_id_departure_date DO NOTHING
RETURNING id, departure_schedule_id
)
INSERT INTO departure_vehicle (departure_id, vehicle_class_id, vehicle_count, allotment_type, created_at)
SELECT i.id, s.base_vehicle_class_id, s.base_vehicle_count, 'BASE', CURRENT_TIMESTAMP
FROM inserted i JOIN departure_schedule s ON s.id = i.departure_schedule_id
        $q$;
    ELSE
        EXECUTE $q$
INSERT INTO departure (departure_schedule_id, route_id, departure_date, planned_departure_at, booking_cutoff_at, stall_capacity, booked_stall_count, status)
SELECT s.id,
       s.route_id,
       d::date,
       (d::date + s.departure_time) AT TIME ZONE 'Asia/Ho_Chi_Minh',
       ((d::date + s.departure_time) AT TIME ZONE 'Asia/Ho_Chi_Minh') - make_interval(hours => s.booking_cutoff_hours),
       s.stall_capacity,
       0,
       'OPEN'
FROM departure_schedule s
JOIN departure_schedule_day sd ON sd.departure_schedule_id = s.id
CROSS JOIN generate_series(current_date + 1, current_date + 42, interval '1 day') AS d
WHERE s.status = 'ACTIVE' AND d::date >= s.valid_from AND (s.valid_to IS NULL OR d::date <= s.valid_to)
  AND extract(isodow FROM d)::smallint = sd.day_of_week
ON CONFLICT ON CONSTRAINT uk_departure_departure_schedule_id_departure_date DO NOTHING
        $q$;
    END IF;
END $m2$;

-- 2. Ba đơn CONFIRMED của customer1.dev
DO $$
DECLARE
    v_user      bigint;
    v_specialist bigint;
    v_manager   bigint;
    v_pl        bigint;
    v_horse_type bigint;
    v_booking   bigint;
    v_quotation bigint;
    v_dep       bigint;
    v_horse     bigint;
    v_line      bigint;
    v_total     numeric(15, 2);
    v_pass      numeric(15, 2);
    v_deposit   numeric(15, 2);
    v_pickup    date;
BEGIN
    IF EXISTS (SELECT 1 FROM booking WHERE code = 'DEV-DIRECT-001') THEN
        RETURN;
    END IF;

    SELECT c.user_id INTO v_user
    FROM customer c JOIN app_user u ON u.id = c.user_id
    WHERE u.username = 'customer1.dev';
    SELECT id INTO v_specialist FROM app_user WHERE username = 'specialist.dev';
    SELECT id INTO v_manager FROM app_user WHERE username = 'manager.dev';
    SELECT id INTO v_pl FROM price_list WHERE name = 'Bảng giá mẫu (dev)';
    SELECT min(id) INTO v_horse_type FROM horse_type;
    IF v_user IS NULL OR v_pl IS NULL THEN
        RAISE EXCEPTION 'Thiếu dữ liệu nền: cần chạy R__dev_seed_base.sql trước';
    END IF;

    -- Ngựa của khách
    INSERT INTO horse (owner_customer_id, name, microchip, passport_number, sex, birth_year, color, breed, horse_type_id, weight_kg, status)
    VALUES (v_user, 'Sao Mai', '985000000100001', 'VN-PP-0001', 'MARE', 2019, 'Nâu', 'Thoroughbred', v_horse_type, 480.0, 'ACTIVE'),
           (v_user, 'Bạch Long', '985000000100002', 'VN-PP-0002', 'STALLION', 2018, 'Trắng', 'Arabian', v_horse_type, 450.0, 'ACTIVE'),
           (v_user, 'Gió Nam', '985000000100003', 'VN-PP-0003', 'GELDING', 2020, 'Hồng', 'Thoroughbred', v_horse_type, 470.0, 'ACTIVE'),
           (v_user, 'Hắc Mã', '985000000100004', 'VN-PP-0004', 'STALLION', 2017, 'Đen', 'Friesian', v_horse_type, 520.0, 'ACTIVE');

    -- ===================== Đơn 1: chạy thẳng Đồng Nai -> TP.HCM (cùng hub HUB-HCM) =====================
    v_pickup := current_date + 10;
    INSERT INTO booking (code, customer_id, booking_type, departure_id, requested_pickup_date, waybill_number, status, created_by)
    VALUES ('DEV-DIRECT-001', v_user, 'INTERPROVINCE', NULL, v_pickup, 'WB-DEV-0001', 'CONFIRMED', v_user)
    RETURNING id INTO v_booking;

    INSERT INTO booking_party (booking_id, booking_role, full_name, phone, address)
    VALUES (v_booking, 'PAYER', 'Nguyễn Văn Khách (mẫu)', '0909000001', 'Phường Thủ Dầu Một, TP.HCM'),
           (v_booking, 'SHIPPER', 'Nguyễn Văn Khách (mẫu)', '0909000001', 'Phường Thủ Dầu Một, TP.HCM'),
           (v_booking, 'CONSIGNEE', 'Trần Người Nhận (mẫu)', '0909000011', 'Phường Bình Thạnh, TP.HCM');

    INSERT INTO booking_address (booking_id, address_type, address_line, province_code, country_code, latitude, longitude, contact_name, contact_phone)
    VALUES (v_booking, 'PICKUP', 'Trang trại Long Thành, Đồng Nai', 'VN-75', 'VN', 10.780000, 106.950000, 'Anh Ba', '0909000021'),
           (v_booking, 'DELIVERY', 'CLB Cưỡi ngựa Sài Gòn, TP.HCM', 'VN-79', 'VN', 10.800000, 106.740000, 'Chị Tư', '0909000022');

    SELECT id INTO v_horse FROM horse WHERE microchip = '985000000100001';
    INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status) VALUES (v_booking, v_horse, 'STANDARD', 'ACTIVE') RETURNING id INTO v_line;
    INSERT INTO horse_insurance (booking_horse_id, coverage_option, coverage_amount, currency_code, policy_number, valid_from, valid_to, status, confirmed_by, confirmed_at)
    VALUES (v_line, 'PERSONAL', 500000000, 'VND', 'POL-DEV-0001', v_pickup - 1, v_pickup + 30, 'CONFIRMED', v_specialist, now());

    INSERT INTO quotation (booking_id, version, price_list_id, currency_code, deposit_rate, expires_at, confirmed_pickup_date, confirmed_departure_date,
                           estimated_delivery_date, total_amount, pass_through_amount, deposit_amount, status, sent_by, sent_at, accepted_at)
    VALUES (v_booking, 1, v_pl, 'VND', 0.3000, now() + interval '1 day', v_pickup, v_pickup, v_pickup, 0, 0, 0, 'DRAFT', NULL, NULL, NULL)
    RETURNING id INTO v_quotation;
    INSERT INTO quotation_line (quotation_id, line_number, line_type, billable_service_id, price_item_id, booking_horse_id, description, quantity, unit, unit_price, is_pass_through)
    SELECT v_quotation, l.line_number, l.line_type, s.id, pi.id, NULL, l.description, l.qty, l.unit, pi.unit_price, s.is_pass_through
    FROM (VALUES (1, 'DIRECT', 'DIRECT_KM', 'MEDIUM'::text, 0.00::numeric, 'Cước chạy thẳng 45 km, xe hạng trung', 45::numeric, 'KM'),
                 (2, 'FUEL', 'FUEL_TOLL', NULL, NULL, 'Nhiên liệu và phí cầu đường 45 km', 45, 'KM'),
                 (3, 'DOCS', 'QUARANTINE_DOCS', NULL, NULL, 'Phí làm thủ tục kiểm dịch', 1, 'BOOKING')
         ) AS l(line_number, line_type, service_code, class_code, km_from, description, qty, unit)
    JOIN billable_service s ON s.code = l.service_code
    JOIN price_item pi ON pi.price_list_id = v_pl AND pi.billable_service_id = s.id AND pi.route_id IS NULL AND pi.stall_type_code IS NULL
                      AND pi.vehicle_class_id IS NOT DISTINCT FROM (SELECT id FROM vehicle_class WHERE code = l.class_code)
                      AND pi.km_from IS NOT DISTINCT FROM l.km_from;

    -- ===================== Đơn 2: trục nội địa Đồng Nai -> Huế (HUB-HCM -> HUB-DN) =====================
    SELECT d.id INTO v_dep FROM departure d JOIN route r ON r.id = d.route_id
    WHERE r.code = 'HCM-DN' AND d.status = 'OPEN' AND d.departure_date >= current_date + 10
    ORDER BY d.departure_date LIMIT 1;
    v_pickup := (SELECT departure_date - 1 FROM departure WHERE id = v_dep);
    INSERT INTO booking (code, customer_id, booking_type, departure_id, requested_pickup_date, waybill_number, status, created_by)
    VALUES ('DEV-DOMESTIC-001', v_user, 'INTERPROVINCE', v_dep, v_pickup, 'WB-DEV-0002', 'CONFIRMED', v_user)
    RETURNING id INTO v_booking;

    INSERT INTO booking_party (booking_id, booking_role, full_name, phone, address)
    VALUES (v_booking, 'PAYER', 'Nguyễn Văn Khách (mẫu)', '0909000001', 'Phường Thủ Dầu Một, TP.HCM'),
           (v_booking, 'SHIPPER', 'Nguyễn Văn Khách (mẫu)', '0909000001', 'Phường Thủ Dầu Một, TP.HCM'),
           (v_booking, 'CONSIGNEE', 'Lê Chủ Trại Huế (mẫu)', '0909000012', 'Phường Thuận Hóa, TP. Huế');

    INSERT INTO booking_address (booking_id, address_type, address_line, province_code, country_code, latitude, longitude, contact_name, contact_phone)
    VALUES (v_booking, 'PICKUP', 'Trang trại Long Thành, Đồng Nai', 'VN-75', 'VN', 10.780000, 106.950000, 'Anh Ba', '0909000021'),
           (v_booking, 'DELIVERY', 'Trại ngựa Thuận An, TP. Huế', 'VN-46', 'VN', 16.560000, 107.640000, 'Anh Năm', '0909000023');

    FOR v_horse IN SELECT id FROM horse WHERE microchip IN ('985000000100002', '985000000100003') ORDER BY id LOOP
        INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status) VALUES (v_booking, v_horse, 'STANDARD', 'ACTIVE') RETURNING id INTO v_line;
        INSERT INTO horse_insurance (booking_horse_id, coverage_option, coverage_amount, currency_code, policy_number, valid_from, valid_to, status, confirmed_by, confirmed_at)
        VALUES (v_line, 'PERSONAL', 500000000, 'VND', 'POL-DEV-' || v_horse, v_pickup - 1, v_pickup + 30, 'CONFIRMED', v_specialist, now());
    END LOOP;
    UPDATE departure SET booked_stall_count = booked_stall_count + 2 WHERE id = v_dep;

    INSERT INTO quotation (booking_id, version, price_list_id, currency_code, deposit_rate, expires_at, confirmed_pickup_date, confirmed_departure_date,
                           estimated_delivery_date, total_amount, pass_through_amount, deposit_amount, status, sent_by, sent_at, accepted_at)
    SELECT v_booking, 1, v_pl, 'VND', 0.3000, now() + interval '1 day', v_pickup, departure_date, departure_date + 2, 0, 0, 0, 'DRAFT', NULL, NULL, NULL
    FROM departure WHERE id = v_dep
    RETURNING id INTO v_quotation;
    INSERT INTO quotation_line (quotation_id, line_number, line_type, billable_service_id, price_item_id, description, quantity, unit, unit_price, is_pass_through)
    SELECT v_quotation, l.line_number, l.line_type, s.id, pi.id, l.description, l.qty, l.unit, pi.unit_price, s.is_pass_through
    FROM (VALUES (1, 'LINEHAUL', 'LINEHAUL_STALL', NULL::text, 'HCM-DN'::text, 'STANDARD'::text, NULL::numeric, 'Cước trục HCM-DN, 2 khoang tiêu chuẩn', 2::numeric, 'STALL'),
                 (2, 'PICKUP', 'PICKUP_KM', 'MEDIUM', NULL, NULL, 0.00, 'Cước chặng đón 40 km, xe hạng trung', 40, 'KM'),
                 (3, 'DELIVERY', 'DELIVERY_KM', 'MEDIUM', NULL, NULL, 0.00, 'Cước chặng giao 15 km, xe hạng trung', 15, 'KM'),
                 (4, 'FUEL', 'FUEL_TOLL', NULL, NULL, NULL, NULL, 'Nhiên liệu và phí cầu đường chặng đón, giao 55 km', 55, 'KM'),
                 (5, 'DOCS', 'QUARANTINE_DOCS', NULL, NULL, NULL, NULL, 'Phí làm thủ tục kiểm dịch', 1, 'BOOKING')
         ) AS l(line_number, line_type, service_code, class_code, route_code, stall, km_from, description, qty, unit)
    JOIN billable_service s ON s.code = l.service_code
    JOIN price_item pi ON pi.price_list_id = v_pl AND pi.billable_service_id = s.id
                      AND pi.vehicle_class_id IS NOT DISTINCT FROM (SELECT id FROM vehicle_class WHERE code = l.class_code)
                      AND pi.route_id IS NOT DISTINCT FROM (SELECT id FROM route WHERE code = l.route_code)
                      AND pi.stall_type_code IS NOT DISTINCT FROM l.stall
                      AND pi.km_from IS NOT DISTINCT FROM l.km_from;

    -- ===================== Đơn 3: quốc tế TP.HCM -> Phnom Penh (trục HUB-HCM -> HUB-PNH qua Mộc Bài) =====================
    SELECT d.id INTO v_dep FROM departure d JOIN route r ON r.id = d.route_id
    WHERE r.code = 'HCM-PNH' AND d.status = 'OPEN' AND d.departure_date >= current_date + 14
    ORDER BY d.departure_date LIMIT 1;
    -- Đón trước chuyến 1 ngày; ngựa đã đủ điều kiện cách ly trước khi đón
    v_pickup := (SELECT departure_date - 1 FROM departure WHERE id = v_dep);
    INSERT INTO booking (code, customer_id, booking_type, departure_id, requested_pickup_date, waybill_number, status, created_by)
    VALUES ('DEV-EXPORT-001', v_user, 'EXPORT', v_dep, v_pickup, 'WB-DEV-0003', 'CONFIRMED', v_user)
    RETURNING id INTO v_booking;

    INSERT INTO booking_party (booking_id, booking_role, full_name, phone, identity_document_type, identity_document_number, identity_document_country_code, address)
    VALUES (v_booking, 'PAYER', 'Nguyễn Văn Khách (mẫu)', '0909000001', NULL, NULL, NULL, 'Phường Thủ Dầu Một, TP.HCM'),
           (v_booking, 'SHIPPER', 'Nguyễn Văn Khách (mẫu)', '0909000001', NULL, NULL, NULL, 'Phường Thủ Dầu Một, TP.HCM'),
           (v_booking, 'CONSIGNEE', 'Sok Dara (mẫu)', '+85512000001', 'PASSPORT', 'N0000001', 'KH', 'Phnom Penh, Campuchia');

    INSERT INTO booking_address (booking_id, address_type, address_line, province_code, country_code, latitude, longitude, contact_name, contact_phone)
    VALUES (v_booking, 'PICKUP', 'CLB Cưỡi ngựa Sài Gòn, TP.HCM', 'VN-79', 'VN', 10.800000, 106.740000, 'Chị Tư', '0909000022'),
           (v_booking, 'DELIVERY', 'Genius Resort Equestrian Centre, Phnom Penh (mẫu)', 'KH-12', 'KH', 11.556400, 104.928200, 'Sok Dara', '+85512000001');

    SELECT id INTO v_horse FROM horse WHERE microchip = '985000000100004';
    INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status) VALUES (v_booking, v_horse, 'STANDARD', 'ACTIVE') RETURNING id INTO v_line;
    INSERT INTO horse_insurance (booking_horse_id, coverage_option, coverage_amount, currency_code, policy_number, valid_from, valid_to, status, confirmed_by, confirmed_at)
    VALUES (v_line, 'PERSONAL', 800000000, 'VND', 'POL-DEV-0004', v_pickup - 1, v_pickup + 45, 'CONFIRMED', v_specialist, now());
    UPDATE departure SET booked_stall_count = booked_stall_count + 1 WHERE id = v_dep;

    INSERT INTO quotation (booking_id, version, price_list_id, currency_code, deposit_rate, expires_at, confirmed_pickup_date, confirmed_departure_date,
                           estimated_delivery_date, total_amount, pass_through_amount, deposit_amount, status, sent_by, sent_at, accepted_at)
    SELECT v_booking, 1, v_pl, 'VND', 0.3000, now() + interval '1 day', v_pickup, departure_date, departure_date + 5, 0, 0, 0, 'DRAFT', NULL, NULL, NULL
    FROM departure WHERE id = v_dep
    RETURNING id INTO v_quotation;
    INSERT INTO quotation_line (quotation_id, line_number, line_type, billable_service_id, price_item_id, description, quantity, unit, unit_price, is_pass_through)
    SELECT v_quotation, l.line_number, l.line_type, s.id, pi.id, l.description, l.qty, l.unit, pi.unit_price, s.is_pass_through
    FROM (VALUES (1, 'LINEHAUL', 'LINEHAUL_STALL', NULL::text, 'HCM-PNH'::text, 'STANDARD'::text, NULL::numeric, 'Cước trục TP.HCM - Phnom Penh, 1 khoang tiêu chuẩn', 1::numeric, 'STALL'),
                 (2, 'PICKUP', 'PICKUP_KM', 'MEDIUM', NULL, NULL, 0.00, 'Cước chặng đón 20 km, xe hạng trung', 20, 'KM'),
                 (3, 'FUEL', 'FUEL_TOLL', NULL, NULL, NULL, NULL, 'Nhiên liệu và phí cầu đường chặng đón 20 km', 20, 'KM'),
                 (4, 'SERVICE', 'VET_CHECK', NULL, NULL, NULL, NULL, 'Thú y tại chuồng: khám và lấy mẫu', 1, 'HORSE'),
                 (5, 'DOCS', 'QUARANTINE_DOCS', NULL, NULL, NULL, NULL, 'Phí làm thủ tục kiểm dịch', 1, 'BOOKING'),
                 (6, 'DOCS', 'GOV_FEE', NULL, NULL, NULL, NULL, 'Lệ phí nhà nước (thu hộ)', 1, 'BOOKING'),
                 (7, 'DELIVERY', 'DELIVERY_KM', 'MEDIUM', NULL, NULL, 0.00, 'Cước chặng giao Phnom Penh 10 km (mẫu)', 10, 'KM'),
                 (8, 'FUEL', 'FUEL_TOLL', NULL, NULL, NULL, NULL, 'Nhiên liệu và phí cầu đường chặng giao 10 km', 10, 'KM')
         ) AS l(line_number, line_type, service_code, class_code, route_code, stall, km_from, description, qty, unit)
    JOIN billable_service s ON s.code = l.service_code
    JOIN price_item pi ON pi.price_list_id = v_pl AND pi.billable_service_id = s.id
                      AND pi.vehicle_class_id IS NOT DISTINCT FROM (SELECT id FROM vehicle_class WHERE code = l.class_code)
                      AND pi.route_id IS NOT DISTINCT FROM (SELECT id FROM route WHERE code = l.route_code)
                      AND pi.stall_type_code IS NOT DISTINCT FROM l.stall
                      AND pi.km_from IS NOT DISTINCT FROM l.km_from;

    -- ===================== Chung cho 3 đơn: chốt báo giá, thanh toán, lịch sử trạng thái =====================
    FOR v_booking, v_quotation IN
        SELECT b.id, q.id FROM booking b JOIN quotation q ON q.booking_id = b.id
        WHERE b.code IN ('DEV-DIRECT-001', 'DEV-DOMESTIC-001', 'DEV-EXPORT-001')
    LOOP
        SELECT coalesce(sum(amount), 0), coalesce(sum(amount) FILTER (WHERE is_pass_through), 0)
        INTO v_total, v_pass
        FROM quotation_line WHERE quotation_id = v_quotation;
        -- Cọc gồm toàn bộ phần thu hộ, làm tròn lên nghìn đồng (công thức của DepositPolicy)
        v_deposit := least(ceil((0.3000 * (v_total - v_pass) + v_pass) / 1000) * 1000, v_total);
        UPDATE quotation
        SET total_amount = v_total, pass_through_amount = v_pass, deposit_amount = v_deposit,
            status = 'ACCEPTED', sent_by = v_manager, sent_at = now() - interval '3 days', accepted_at = now() - interval '2 days'
        WHERE id = v_quotation;

        INSERT INTO payment (booking_id, quotation_id, payment_type, amount, currency_code, payment_method, status, paid_at)
        VALUES (v_booking, v_quotation, 'DEPOSIT', v_deposit, 'VND', 'VNPAY', 'PAID', now() - interval '2 days'),
               (v_booking, v_quotation, 'BALANCE', v_total - v_deposit, 'VND', 'VNPAY', 'PAID', now() - interval '1 day');

        -- Kiểm thử migration cũ nạp dữ liệu mẫu khi chưa có bảng này
        IF to_regclass('booking_specialist_history') IS NOT NULL THEN
            UPDATE booking SET specialist_id = v_specialist WHERE id = v_booking;
            INSERT INTO booking_specialist_history (booking_id, to_staff_id, changed_by, changed_at)
            VALUES (v_booking, v_specialist, v_manager, now() - interval '4 days');
        END IF;

        INSERT INTO booking_status_history (booking_id, from_status, to_status, changed_by, changed_at, reason)
        VALUES (v_booking, NULL, 'PENDING_INTAKE', v_user, now() - interval '5 days', NULL),
               (v_booking, 'PENDING_INTAKE', 'UNDER_REVIEW', v_manager, now() - interval '4 days', NULL),
               (v_booking, 'UNDER_REVIEW', 'QUOTING', v_specialist, now() - interval '3 days 12 hours', NULL),
               (v_booking, 'QUOTING', 'AWAITING_DEPOSIT', v_manager, now() - interval '3 days', NULL),
               (v_booking, 'AWAITING_DEPOSIT', 'CONFIRMED', v_user, now() - interval '2 days', 'Dữ liệu mẫu');
    END LOOP;
END $$;

-- Giao một chuyến mẫu cho điều phối, giữ các chuyến còn lại để xem nhắc chưa phân công.
DO $$
BEGIN
    IF to_regclass('coordinator_assignment_history') IS NOT NULL THEN
        WITH assigned AS (
            UPDATE departure d SET coordinator_id = coordinator.user_id
            FROM staff coordinator, app_user account
            WHERE d.id = (SELECT max(id) FROM departure WHERE departure_schedule_id IS NOT NULL)
              AND d.coordinator_id IS NULL
              AND account.username = 'coordinator.dev' AND coordinator.user_id = account.id
            RETURNING d.id, d.coordinator_id
        )
        INSERT INTO coordinator_assignment_history (departure_id, to_staff_id, changed_by, reason, changed_at)
        SELECT assigned.id, assigned.coordinator_id, manager.id, 'Phân công chuyến mẫu', now()
        FROM assigned CROSS JOIN app_user manager WHERE manager.username = 'manager.dev';
    END IF;
END $$;
