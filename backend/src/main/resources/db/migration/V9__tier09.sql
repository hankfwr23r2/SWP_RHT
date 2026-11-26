-- Bậc 9: booking_horse_service, trip_stop, crew_assignment, horse_stay, horse_insurance, payment, notification_recipient.

CREATE TABLE booking_horse_service (
    booking_horse_id bigint NOT NULL,
    service_id       bigint NOT NULL,   -- chỉ chọn được dịch vụ có layer = ADD_ON (kiểm ở service)
    quantity         int    NOT NULL,
    CONSTRAINT pk_booking_horse_service PRIMARY KEY (booking_horse_id, service_id),
    CONSTRAINT fk_booking_horse_service_booking_horse_id FOREIGN KEY (booking_horse_id) REFERENCES booking_horse (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_horse_service_service_id FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT ck_booking_horse_service_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_booking_horse_service_service_id ON booking_horse_service (service_id);

-- Điểm dừng của một lượt chạy: vừa là lộ trình kế hoạch vừa ghi giờ thực tế
CREATE TABLE trip_stop (
    id                 bigint GENERATED ALWAYS AS IDENTITY,
    vehicle_trip_id    bigint      NOT NULL,
    seq                int         NOT NULL,
    location_id        bigint,
    booking_address_id bigint,
    stop_type          varchar(30) NOT NULL,
    route_id           bigint,
    route_seq          int,
    planned_arrive_at  timestamptz NOT NULL,   -- snapshot: chép từ route_stop.offset_minutes
    planned_depart_at  timestamptz NOT NULL,   -- snapshot
    actual_arrive_at   timestamptz,
    actual_depart_at   timestamptz,
    CONSTRAINT pk_trip_stop PRIMARY KEY (id),
    CONSTRAINT uk_trip_stop_trip_seq UNIQUE (vehicle_trip_id, seq),
    CONSTRAINT fk_trip_stop_vehicle_trip_id FOREIGN KEY (vehicle_trip_id) REFERENCES vehicle_trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_stop_location_id FOREIGN KEY (location_id) REFERENCES location (id),
    CONSTRAINT fk_trip_stop_booking_address_id FOREIGN KEY (booking_address_id) REFERENCES booking_address (id),
    CONSTRAINT fk_trip_stop_route_stop FOREIGN KEY (route_id, route_seq) REFERENCES route_stop (route_id, seq),
    CONSTRAINT ck_trip_stop_seq CHECK (seq >= 1),
    CONSTRAINT ck_trip_stop_stop_type CHECK (stop_type IN ('PICKUP', 'DELIVERY', 'HUB', 'REST', 'VITAL_CHECK', 'BORDER', 'QUARANTINE')),
    -- Điểm dừng là location của mạng lưới hoặc địa chỉ của khách, chỉ một trong hai
    CONSTRAINT ck_trip_stop_place CHECK (num_nonnulls(location_id, booking_address_id) = 1),
    CONSTRAINT ck_trip_stop_route_pair CHECK ((route_id IS NULL) = (route_seq IS NULL)),
    CONSTRAINT ck_trip_stop_planned CHECK (planned_depart_at >= planned_arrive_at)
);
CREATE INDEX ix_trip_stop_location_id ON trip_stop (location_id);
CREATE INDEX ix_trip_stop_booking_address_id ON trip_stop (booking_address_id);
CREATE INDEX ix_trip_stop_route_stop ON trip_stop (route_id, route_seq);

CREATE TABLE crew_assignment (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    vehicle_trip_id bigint      NOT NULL,
    staff_id        bigint      NOT NULL,
    role            varchar(30) NOT NULL,
    status          varchar(30) NOT NULL,
    assigned_at     timestamptz NOT NULL DEFAULT now(),
    note            text,
    CONSTRAINT pk_crew_assignment PRIMARY KEY (id),
    CONSTRAINT uk_crew_assignment_trip_staff UNIQUE (vehicle_trip_id, staff_id),
    CONSTRAINT fk_crew_assignment_vehicle_trip_id FOREIGN KEY (vehicle_trip_id) REFERENCES vehicle_trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_crew_assignment_staff_id FOREIGN KEY (staff_id) REFERENCES staff (user_id),
    CONSTRAINT ck_crew_assignment_role CHECK (role IN ('DRIVER', 'ESCORT', 'VET')),
    CONSTRAINT ck_crew_assignment_status CHECK (status IN ('ASSIGNED', 'ACKNOWLEDGED', 'RELEASED'))
);
CREATE INDEX ix_crew_assignment_staff_id ON crew_assignment (staff_id);

-- Thời gian ngựa ở một nơi, không trên xe (tập kết, cách ly, qua đêm, gửi chuồng)
CREATE TABLE horse_stay (
    id                bigint GENERATED ALWAYS AS IDENTITY,
    booking_horse_id  bigint      NOT NULL,
    kind              varchar(30) NOT NULL,
    location_id       bigint      NOT NULL,
    operator_party_id bigint,
    planned_from      timestamptz NOT NULL,
    planned_to        timestamptz NOT NULL,
    actual_from       timestamptz,
    actual_to         timestamptz,
    result            varchar(30),
    CONSTRAINT pk_horse_stay PRIMARY KEY (id),
    CONSTRAINT fk_horse_stay_booking_horse_id FOREIGN KEY (booking_horse_id) REFERENCES booking_horse (id),
    CONSTRAINT fk_horse_stay_location_id FOREIGN KEY (location_id) REFERENCES location (id),
    CONSTRAINT fk_horse_stay_operator_party_id FOREIGN KEY (operator_party_id) REFERENCES party (id),
    CONSTRAINT ck_horse_stay_kind CHECK (kind IN ('ASSEMBLY', 'QUARANTINE', 'OVERNIGHT', 'STABLING')),
    CONSTRAINT ck_horse_stay_result_value CHECK (result IN ('PASSED', 'FAILED')),
    CONSTRAINT ck_horse_stay_planned CHECK (planned_to > planned_from),
    CONSTRAINT ck_horse_stay_result_kind CHECK (result IS NULL OR kind = 'QUARANTINE'),
    CONSTRAINT ck_horse_stay_actual CHECK (actual_to IS NULL OR actual_from IS NOT NULL)
);
CREATE INDEX ix_horse_stay_booking_horse_id ON horse_stay (booking_horse_id);
CREATE INDEX ix_horse_stay_location_id ON horse_stay (location_id);
CREATE INDEX ix_horse_stay_operator_party_id ON horse_stay (operator_party_id);

-- Bảo hiểm cho từng con trong đơn (1–1 với booking_horse)
CREATE TABLE horse_insurance (
    id                    bigint GENERATED ALWAYS AS IDENTITY,
    booking_horse_id      bigint         NOT NULL,
    option                varchar(30)    NOT NULL,
    insurer_party_id      bigint,
    coverage_amount       numeric(15, 2),
    premium               numeric(15, 2),
    currency              char(3),
    quote_expires_at      timestamptz,
    policy_no             varchar(50),
    valid_from            date,
    valid_to              date,
    waiver_acceptance_id  bigint,
    status                varchar(30)    NOT NULL,
    revision_reason       text,
    revision_requested_by bigint,
    revision_requested_at timestamptz,
    confirmed_by          bigint,
    confirmed_at          timestamptz,
    CONSTRAINT pk_horse_insurance PRIMARY KEY (id),
    CONSTRAINT uk_horse_insurance_booking_horse_id UNIQUE (booking_horse_id),
    CONSTRAINT fk_horse_insurance_booking_horse_id FOREIGN KEY (booking_horse_id) REFERENCES booking_horse (id) ON DELETE CASCADE,
    CONSTRAINT fk_horse_insurance_insurer_party_id FOREIGN KEY (insurer_party_id) REFERENCES party (id),
    CONSTRAINT fk_horse_insurance_waiver_acceptance_id FOREIGN KEY (waiver_acceptance_id) REFERENCES terms_acceptance (id),
    CONSTRAINT fk_horse_insurance_revision_requested_by FOREIGN KEY (revision_requested_by) REFERENCES staff (user_id),
    CONSTRAINT fk_horse_insurance_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES staff (user_id),
    CONSTRAINT ck_horse_insurance_option CHECK (option IN ('PERSONAL', 'ARRANGED', 'NONE')),
    CONSTRAINT ck_horse_insurance_coverage_amount CHECK (coverage_amount > 0),
    CONSTRAINT ck_horse_insurance_premium CHECK (premium >= 0),
    CONSTRAINT ck_horse_insurance_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_horse_insurance_valid_to CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_horse_insurance_status CHECK (status IN ('DRAFT', 'REVISION_REQUESTED', 'CONFIRMED')),
    CONSTRAINT ck_horse_insurance_arranged CHECK (option <> 'ARRANGED' OR insurer_party_id IS NOT NULL),
    CONSTRAINT ck_horse_insurance_waiver CHECK (option <> 'NONE' OR waiver_acceptance_id IS NOT NULL),
    CONSTRAINT ck_horse_insurance_coverage CHECK (option = 'NONE' OR (coverage_amount IS NOT NULL AND currency IS NOT NULL)),
    CONSTRAINT ck_horse_insurance_revision CHECK (status <> 'REVISION_REQUESTED' OR (revision_reason IS NOT NULL AND revision_requested_at IS NOT NULL)),
    CONSTRAINT ck_horse_insurance_confirmed CHECK (status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))
);
CREATE INDEX ix_horse_insurance_insurer_party_id ON horse_insurance (insurer_party_id);
CREATE INDEX ix_horse_insurance_waiver_acceptance_id ON horse_insurance (waiver_acceptance_id);
CREATE INDEX ix_horse_insurance_revision_requested_by ON horse_insurance (revision_requested_by);
CREATE INDEX ix_horse_insurance_confirmed_by ON horse_insurance (confirmed_by);

-- Khoản phải thu hoặc phải hoàn. amount luôn dương, chiều tiền xác định bằng kind
CREATE TABLE payment (
    id                   bigint GENERATED ALWAYS AS IDENTITY,
    booking_id           bigint         NOT NULL,
    quotation_id         bigint,
    kind                 varchar(30)    NOT NULL,
    amount               numeric(15, 2) NOT NULL,
    currency             char(3)        NOT NULL,
    method               varchar(30)    NOT NULL,
    status               varchar(30)    NOT NULL,
    due_at               timestamptz,
    paid_at              timestamptz,
    refund_of_payment_id bigint,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT fk_payment_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_payment_quotation_id FOREIGN KEY (quotation_id) REFERENCES quotation (id),
    CONSTRAINT fk_payment_refund_of_payment_id FOREIGN KEY (refund_of_payment_id) REFERENCES payment (id),
    CONSTRAINT ck_payment_kind CHECK (kind IN ('DEPOSIT', 'BALANCE', 'EXTRA_CHARGE', 'REFUND')),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_method CHECK (method IN ('VNPAY', 'BANK_TRANSFER', 'CASH')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_payment_refund CHECK ((kind = 'REFUND') = (refund_of_payment_id IS NOT NULL)),
    CONSTRAINT ck_payment_quotation CHECK (kind NOT IN ('DEPOSIT', 'BALANCE') OR quotation_id IS NOT NULL),
    CONSTRAINT ck_payment_paid_at CHECK (status <> 'PAID' OR paid_at IS NOT NULL)
);
CREATE INDEX ix_payment_booking_id ON payment (booking_id);
CREATE INDEX ix_payment_quotation_id ON payment (quotation_id);
CREATE INDEX ix_payment_refund_of_payment_id ON payment (refund_of_payment_id);

CREATE TABLE notification_recipient (
    notification_id bigint NOT NULL,
    user_id         bigint NOT NULL,
    read_at         timestamptz,   -- NULL = chưa đọc
    CONSTRAINT pk_notification_recipient PRIMARY KEY (notification_id, user_id),
    CONSTRAINT fk_notification_recipient_notification_id FOREIGN KEY (notification_id) REFERENCES notification (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_recipient_user_id FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);
-- Dùng để đếm số thông báo chưa đọc
CREATE INDEX ix_notification_recipient_user_read ON notification_recipient (user_id, read_at);
