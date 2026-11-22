-- Bậc 8: booking_party, booking_address, booking_horse, booking_status_history, terms_acceptance, booking_rating,
--        vehicle_trip, quotation, notification, audit_log.

-- Các bên trong đơn: chụp lại thông tin tại thời điểm đặt đơn (snapshot)
CREATE TABLE booking_party (
    booking_id bigint       NOT NULL,
    role       varchar(30)  NOT NULL,
    party_id   bigint,
    full_name  varchar(200) NOT NULL,
    phone      varchar(20)  NOT NULL,
    id_type    varchar(30),
    id_number  varchar(50),
    id_country char(2),
    address    varchar(500) NOT NULL,
    CONSTRAINT pk_booking_party PRIMARY KEY (booking_id, role),
    CONSTRAINT fk_booking_party_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_party_party_id FOREIGN KEY (party_id) REFERENCES party (id),
    CONSTRAINT ck_booking_party_role CHECK (role IN ('PAYER', 'SHIPPER', 'CONSIGNEE', 'EMERGENCY_CONTACT'))
);
CREATE INDEX ix_booking_party_party_id ON booking_party (party_id);

-- Điểm đón và giao của đơn (snapshot). Là đích FK của trip_stop và horse_leg
CREATE TABLE booking_address (
    id                bigint GENERATED ALWAYS AS IDENTITY,
    booking_id        bigint        NOT NULL,
    role              varchar(30)   NOT NULL,
    address_line      varchar(500)  NOT NULL,
    province_code     varchar(10)   NOT NULL,
    country_code      char(2)       NOT NULL,
    latitude          numeric(9, 6) NOT NULL,
    longitude         numeric(9, 6) NOT NULL,
    contact_name      varchar(200)  NOT NULL,
    contact_phone     varchar(20)   NOT NULL,
    access_note       text,
    location_id       bigint,
    source_address_id bigint,
    CONSTRAINT pk_booking_address PRIMARY KEY (id),
    CONSTRAINT uk_booking_address_booking_role UNIQUE (booking_id, role),
    CONSTRAINT fk_booking_address_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_address_province_code FOREIGN KEY (province_code) REFERENCES province (code),
    CONSTRAINT fk_booking_address_country_code FOREIGN KEY (country_code) REFERENCES country (code),
    CONSTRAINT fk_booking_address_location_id FOREIGN KEY (location_id) REFERENCES location (id),
    CONSTRAINT fk_booking_address_source_address_id FOREIGN KEY (source_address_id) REFERENCES party_address (id) ON DELETE SET NULL,
    CONSTRAINT ck_booking_address_role CHECK (role IN ('PICKUP', 'DELIVERY')),
    CONSTRAINT ck_booking_address_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_booking_address_longitude CHECK (longitude BETWEEN -180 AND 180)
);
CREATE INDEX ix_booking_address_province_code ON booking_address (province_code);
CREATE INDEX ix_booking_address_country_code ON booking_address (country_code);
CREATE INDEX ix_booking_address_location_id ON booking_address (location_id);
CREATE INDEX ix_booking_address_source_address_id ON booking_address (source_address_id);

CREATE TABLE booking_horse (
    id                  bigint GENERATED ALWAYS AS IDENTITY,
    booking_id          bigint      NOT NULL,
    horse_id            bigint      NOT NULL,
    stall_type          varchar(30) NOT NULL,
    diet_note           text,
    feed_times_per_day  smallint,
    owner_supplies_feed boolean     NOT NULL DEFAULT false,
    status              varchar(30) NOT NULL,
    added_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_booking_horse PRIMARY KEY (id),
    CONSTRAINT uk_booking_horse_booking_horse UNIQUE (booking_id, horse_id),
    CONSTRAINT fk_booking_horse_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_horse_horse_id FOREIGN KEY (horse_id) REFERENCES horse (id),
    CONSTRAINT fk_booking_horse_stall_type FOREIGN KEY (stall_type) REFERENCES stall_type (code),
    CONSTRAINT ck_booking_horse_feed_times_per_day CHECK (feed_times_per_day BETWEEN 1 AND 10),
    CONSTRAINT ck_booking_horse_status CHECK (status IN ('ACTIVE', 'EXCLUDED', 'CANCELLED', 'DELIVERED', 'RETURNED'))
);
CREATE INDEX ix_booking_horse_horse_id ON booking_horse (horse_id);
CREATE INDEX ix_booking_horse_stall_type ON booking_horse (stall_type);

CREATE TABLE booking_status_history (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    booking_id  bigint      NOT NULL,
    from_status varchar(30),
    to_status   varchar(30) NOT NULL,
    changed_by  bigint      NOT NULL,
    changed_at  timestamptz NOT NULL DEFAULT now(),
    reason      text,
    CONSTRAINT pk_booking_status_history PRIMARY KEY (id),
    CONSTRAINT fk_booking_status_history_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_status_history_changed_by FOREIGN KEY (changed_by) REFERENCES app_user (id)
);
CREATE INDEX ix_booking_status_history_booking_changed_at ON booking_status_history (booking_id, changed_at);
CREATE INDEX ix_booking_status_history_changed_by ON booking_status_history (changed_by);

CREATE TABLE terms_acceptance (
    id            bigint GENERATED ALWAYS AS IDENTITY,
    terms_kind    varchar(30) NOT NULL,
    terms_version varchar(20) NOT NULL,
    user_id       bigint      NOT NULL,
    booking_id    bigint,
    accepted_at   timestamptz NOT NULL DEFAULT now(),
    ip_address    varchar(45),
    CONSTRAINT pk_terms_acceptance PRIMARY KEY (id),
    CONSTRAINT fk_terms_acceptance_user_id FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_terms_acceptance_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT ck_terms_acceptance_terms_kind CHECK (terms_kind IN ('SERVICE_TERMS', 'LIABILITY_WAIVER', 'PRIVACY'))
);
CREATE INDEX ix_terms_acceptance_user_id ON terms_acceptance (user_id);
CREATE INDEX ix_terms_acceptance_booking_id ON terms_acceptance (booking_id);

CREATE TABLE booking_rating (
    booking_id bigint      NOT NULL,
    score      smallint    NOT NULL,
    comment    text,
    rated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_booking_rating PRIMARY KEY (booking_id),
    CONSTRAINT fk_booking_rating_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE,
    CONSTRAINT ck_booking_rating_score CHECK (score BETWEEN 1 AND 5)
);

-- Một lượt chạy thật của một xe
CREATE TABLE vehicle_trip (
    id                bigint GENERATED ALWAYS AS IDENTITY,
    code              varchar(30) NOT NULL,
    kind              varchar(30) NOT NULL,
    departure_id      bigint,
    vehicle_id        bigint      NOT NULL,
    start_location_id bigint      NOT NULL,
    end_location_id   bigint      NOT NULL,
    planned_start     timestamptz NOT NULL,
    planned_end       timestamptz NOT NULL,
    actual_start      timestamptz,
    actual_end        timestamptz,
    sealed_at         timestamptz,
    disinfected_at    timestamptz,
    status            varchar(30) NOT NULL,
    CONSTRAINT pk_vehicle_trip PRIMARY KEY (id),
    CONSTRAINT uk_vehicle_trip_code UNIQUE (code),
    CONSTRAINT fk_vehicle_trip_departure_id FOREIGN KEY (departure_id) REFERENCES departure (id),
    CONSTRAINT fk_vehicle_trip_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_vehicle_trip_start_location_id FOREIGN KEY (start_location_id) REFERENCES location (id),
    CONSTRAINT fk_vehicle_trip_end_location_id FOREIGN KEY (end_location_id) REFERENCES location (id),
    CONSTRAINT ck_vehicle_trip_kind CHECK (kind IN ('PICKUP', 'LINEHAUL', 'DELIVERY', 'TRANSFER', 'DIRECT')),
    CONSTRAINT ck_vehicle_trip_linehaul_departure CHECK (kind <> 'LINEHAUL' OR departure_id IS NOT NULL),
    CONSTRAINT ck_vehicle_trip_planned CHECK (planned_end > planned_start),
    CONSTRAINT ck_vehicle_trip_actual CHECK (actual_end IS NULL OR actual_start IS NOT NULL),
    CONSTRAINT ck_vehicle_trip_status CHECK (status IN ('PLANNED', 'DISPATCHED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX ix_vehicle_trip_departure_id ON vehicle_trip (departure_id);
CREATE INDEX ix_vehicle_trip_vehicle_id ON vehicle_trip (vehicle_id);
CREATE INDEX ix_vehicle_trip_start_location_id ON vehicle_trip (start_location_id);
CREATE INDEX ix_vehicle_trip_end_location_id ON vehicle_trip (end_location_id);
CREATE INDEX ix_vehicle_trip_status_planned_start ON vehicle_trip (status, planned_start);

CREATE TABLE quotation (
    id                       bigint GENERATED ALWAYS AS IDENTITY,
    booking_id               bigint         NOT NULL,
    version                  int            NOT NULL,
    price_list_id            bigint         NOT NULL,
    currency                 char(3)        NOT NULL,
    deposit_rate             numeric(5, 4)  NOT NULL,   -- snapshot: chép từ price_list lúc lập báo giá
    valid_until              timestamptz    NOT NULL,
    confirmed_pickup_date    date           NOT NULL,
    confirmed_departure_date date           NOT NULL,
    estimated_delivery_date  date           NOT NULL,
    -- Dẫn xuất, chỉ QuotationService ghi khi còn DRAFT; bất biến sau khi SENT
    total_amount             numeric(15, 2) NOT NULL,
    pass_through_amount      numeric(15, 2) NOT NULL,
    deposit_amount           numeric(15, 2) NOT NULL,
    status                   varchar(30)    NOT NULL,
    sent_by                  bigint,
    sent_at                  timestamptz,
    accepted_at              timestamptz,
    CONSTRAINT pk_quotation PRIMARY KEY (id),
    CONSTRAINT uk_quotation_booking_version UNIQUE (booking_id, version),
    CONSTRAINT fk_quotation_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_quotation_price_list_id FOREIGN KEY (price_list_id) REFERENCES price_list (id),
    CONSTRAINT fk_quotation_sent_by FOREIGN KEY (sent_by) REFERENCES staff (user_id),
    CONSTRAINT ck_quotation_version CHECK (version >= 1),
    CONSTRAINT ck_quotation_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_quotation_deposit_rate CHECK (deposit_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_quotation_delivery_date CHECK (estimated_delivery_date >= confirmed_departure_date),
    CONSTRAINT ck_quotation_total_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_quotation_pass_through_amount CHECK (pass_through_amount BETWEEN 0 AND total_amount),
    -- Tiền cọc gồm toàn bộ phần thu hộ và không vượt tổng
    CONSTRAINT ck_quotation_deposit_amount CHECK (deposit_amount BETWEEN pass_through_amount AND total_amount),
    CONSTRAINT ck_quotation_status CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'SUPERSEDED')),
    CONSTRAINT ck_quotation_sent CHECK (status = 'DRAFT' OR (sent_by IS NOT NULL AND sent_at IS NOT NULL))
);
-- Mỗi đơn chỉ có một báo giá được chấp nhận
CREATE UNIQUE INDEX uk_quotation_accepted ON quotation (booking_id) WHERE status = 'ACCEPTED';
CREATE INDEX ix_quotation_price_list_id ON quotation (price_list_id);
CREATE INDEX ix_quotation_sent_by ON quotation (sent_by);

CREATE TABLE notification (
    id         bigint GENERATED ALWAYS AS IDENTITY,
    booking_id bigint,
    title      varchar(200) NOT NULL,
    message    text         NOT NULL,
    link       varchar(500),
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT fk_notification_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id)
);
CREATE INDEX ix_notification_booking_id ON notification (booking_id);

CREATE TABLE audit_log (
    id            bigint GENERATED ALWAYS AS IDENTITY,
    actor_user_id bigint,                -- NULL = hệ thống tự chạy (scheduler)
    action        varchar(50) NOT NULL,
    target_table  varchar(50) NOT NULL,
    target_id     bigint      NOT NULL,  -- không đặt FK vì trỏ tới nhiều bảng khác nhau
    old_data      jsonb,                 -- ngoại lệ được phép dùng JSON: bản chụp kiểm toán
    new_data      jsonb,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_audit_log_actor_user_id FOREIGN KEY (actor_user_id) REFERENCES app_user (id)
);
CREATE INDEX ix_audit_log_target ON audit_log (target_table, target_id);
CREATE INDEX ix_audit_log_actor_user_id ON audit_log (actor_user_id);
CREATE INDEX ix_audit_log_created_at ON audit_log (created_at);
