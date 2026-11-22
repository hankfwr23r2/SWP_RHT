-- Bậc 11: horse_health_log, handover_horse, quotation_line, incident.

CREATE TABLE horse_health_log (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    booking_horse_id bigint        NOT NULL,
    recorded_by      bigint        NOT NULL,
    recorded_at      timestamptz   NOT NULL,
    vehicle_trip_id  bigint,          -- đo trên đường, không ở điểm dừng
    trip_stop_id     bigint,          -- đo tại một điểm dừng
    horse_stay_id    bigint,          -- đo trong thời gian lưu trú hoặc cách ly
    health_status    varchar(30)   NOT NULL,
    temperature_c    numeric(4, 1),
    heart_rate       smallint,
    respiration_rate smallint,
    weight_kg        numeric(6, 1),
    symptoms         text,
    note             text,
    CONSTRAINT pk_horse_health_log PRIMARY KEY (id),
    CONSTRAINT fk_horse_health_log_booking_horse_id FOREIGN KEY (booking_horse_id) REFERENCES booking_horse (id),
    CONSTRAINT fk_horse_health_log_recorded_by FOREIGN KEY (recorded_by) REFERENCES staff (user_id),
    CONSTRAINT fk_horse_health_log_vehicle_trip_id FOREIGN KEY (vehicle_trip_id) REFERENCES vehicle_trip (id),
    CONSTRAINT fk_horse_health_log_trip_stop_id FOREIGN KEY (trip_stop_id) REFERENCES trip_stop (id),
    CONSTRAINT fk_horse_health_log_horse_stay_id FOREIGN KEY (horse_stay_id) REFERENCES horse_stay (id),
    CONSTRAINT ck_horse_health_log_health_status CHECK (health_status IN ('NORMAL', 'WATCH', 'SICK', 'CRITICAL')),
    CONSTRAINT ck_horse_health_log_temperature_c CHECK (temperature_c BETWEEN 30 AND 45),
    CONSTRAINT ck_horse_health_log_heart_rate CHECK (heart_rate BETWEEN 10 AND 200),
    CONSTRAINT ck_horse_health_log_respiration_rate CHECK (respiration_rate BETWEEN 4 AND 100),
    CONSTRAINT ck_horse_health_log_weight_kg CHECK (weight_kg > 0),
    -- Đo tại trip_stop thì lượt chạy đã suy ra được, nên không ghi thêm vehicle_trip_id
    CONSTRAINT ck_horse_health_log_place CHECK (num_nonnulls(vehicle_trip_id, trip_stop_id, horse_stay_id) <= 1)
);
CREATE INDEX ix_horse_health_log_booking_horse_recorded_at ON horse_health_log (booking_horse_id, recorded_at);
CREATE INDEX ix_horse_health_log_recorded_by ON horse_health_log (recorded_by);
CREATE INDEX ix_horse_health_log_vehicle_trip_id ON horse_health_log (vehicle_trip_id);
CREATE INDEX ix_horse_health_log_trip_stop_id ON horse_health_log (trip_stop_id);
CREATE INDEX ix_horse_health_log_horse_stay_id ON horse_health_log (horse_stay_id);

CREATE TABLE handover_horse (
    handover_id    bigint  NOT NULL,
    horse_leg_id   bigint  NOT NULL,
    chip_scanned   varchar(30),
    chip_matched   boolean NOT NULL,   -- so với horse.microchip
    condition_note text,
    CONSTRAINT pk_handover_horse PRIMARY KEY (handover_id, horse_leg_id),
    CONSTRAINT fk_handover_horse_handover_id FOREIGN KEY (handover_id) REFERENCES handover (id) ON DELETE CASCADE,
    CONSTRAINT fk_handover_horse_horse_leg_id FOREIGN KEY (horse_leg_id) REFERENCES horse_leg (id)
);
CREATE INDEX ix_handover_horse_horse_leg_id ON handover_horse (horse_leg_id);

CREATE TABLE quotation_line (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    quotation_id     bigint         NOT NULL,
    line_no          int            NOT NULL,
    line_type        varchar(30)    NOT NULL,
    service_id       bigint,
    price_item_id    bigint,           -- NULL với dòng điều chỉnh tay
    booking_horse_id bigint,
    horse_leg_id     bigint,
    description      varchar(500)   NOT NULL,
    quantity         numeric(10, 2) NOT NULL,
    unit             varchar(30)    NOT NULL,
    unit_price       numeric(15, 2) NOT NULL,   -- snapshot: bảng giá đổi về sau không làm đổi báo giá cũ
    amount           numeric(15, 2) GENERATED ALWAYS AS (round(quantity * unit_price, 2)) STORED,
    is_pass_through  boolean        NOT NULL,   -- snapshot
    CONSTRAINT pk_quotation_line PRIMARY KEY (id),
    CONSTRAINT uk_quotation_line_quotation_line_no UNIQUE (quotation_id, line_no),
    CONSTRAINT fk_quotation_line_quotation_id FOREIGN KEY (quotation_id) REFERENCES quotation (id) ON DELETE CASCADE,
    CONSTRAINT fk_quotation_line_service_id FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_quotation_line_price_item_id FOREIGN KEY (price_item_id) REFERENCES price_item (id),
    CONSTRAINT fk_quotation_line_booking_horse_id FOREIGN KEY (booking_horse_id) REFERENCES booking_horse (id),
    CONSTRAINT fk_quotation_line_horse_leg_id FOREIGN KEY (horse_leg_id) REFERENCES horse_leg (id),
    CONSTRAINT ck_quotation_line_line_no CHECK (line_no >= 1),
    CONSTRAINT ck_quotation_line_line_type CHECK (line_type IN ('LINEHAUL', 'PICKUP', 'DELIVERY', 'DIRECT', 'FUEL', 'STALL', 'QUARANTINE',
                                                                'SERVICE', 'DOCS', 'INSURANCE', 'ADJUSTMENT')),
    CONSTRAINT ck_quotation_line_quantity CHECK (quantity > 0),
    -- Chỉ dòng điều chỉnh được có đơn giá âm (giảm giá)
    CONSTRAINT ck_quotation_line_unit_price CHECK (unit_price >= 0 OR line_type = 'ADJUSTMENT'),
    CONSTRAINT ck_quotation_line_service CHECK (line_type = 'ADJUSTMENT' OR service_id IS NOT NULL)
);
CREATE INDEX ix_quotation_line_service_id ON quotation_line (service_id);
CREATE INDEX ix_quotation_line_price_item_id ON quotation_line (price_item_id);
CREATE INDEX ix_quotation_line_booking_horse_id ON quotation_line (booking_horse_id);
CREATE INDEX ix_quotation_line_horse_leg_id ON quotation_line (horse_leg_id);

CREATE TABLE incident (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    booking_id       bigint,
    vehicle_trip_id  bigint,          -- một sự cố xe có thể ảnh hưởng nhiều đơn
    horse_leg_id     bigint,          -- sự cố của riêng một con ngựa
    incident_type    varchar(30)   NOT NULL,
    cause            varchar(30)   NOT NULL,   -- nguyên nhân, quyết định ai chịu chi phí
    name             varchar(200)  NOT NULL,
    description      text          NOT NULL,
    latitude         numeric(9, 6),
    longitude        numeric(9, 6),
    reported_by      bigint        NOT NULL,
    reported_at      timestamptz   NOT NULL DEFAULT now(),
    status           varchar(30)   NOT NULL,
    fit_confirmed_by bigint,
    fit_confirmed_at timestamptz,
    resolved_at      timestamptz,
    CONSTRAINT pk_incident PRIMARY KEY (id),
    CONSTRAINT fk_incident_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_incident_vehicle_trip_id FOREIGN KEY (vehicle_trip_id) REFERENCES vehicle_trip (id),
    CONSTRAINT fk_incident_horse_leg_id FOREIGN KEY (horse_leg_id) REFERENCES horse_leg (id),
    CONSTRAINT fk_incident_reported_by FOREIGN KEY (reported_by) REFERENCES staff (user_id),
    CONSTRAINT fk_incident_fit_confirmed_by FOREIGN KEY (fit_confirmed_by) REFERENCES staff (user_id),
    CONSTRAINT ck_incident_incident_type CHECK (incident_type IN ('HEALTH', 'VEHICLE_BREAKDOWN', 'ACCIDENT', 'DELAY', 'DOCUMENT', 'HANDOVER_TIMEOUT', 'OTHER')),
    CONSTRAINT ck_incident_cause CHECK (cause IN ('COMPANY_FAULT', 'HORSE_CONDITION', 'CUSTOMER_DOCS', 'AGENT_FAULT', 'FORCE_MAJEURE', 'RECIPIENT_ABSENT')),
    CONSTRAINT ck_incident_status CHECK (status IN ('REPORTED', 'PLAN_PENDING', 'PLAN_ACTIVE', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_incident_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_incident_subject CHECK (num_nonnulls(booking_id, vehicle_trip_id, horse_leg_id) >= 1),
    CONSTRAINT ck_incident_fit_confirmed CHECK ((fit_confirmed_by IS NULL) = (fit_confirmed_at IS NULL))
);
CREATE INDEX ix_incident_booking_id ON incident (booking_id);
CREATE INDEX ix_incident_vehicle_trip_id ON incident (vehicle_trip_id);
CREATE INDEX ix_incident_horse_leg_id ON incident (horse_leg_id);
CREATE INDEX ix_incident_reported_by ON incident (reported_by);
CREATE INDEX ix_incident_fit_confirmed_by ON incident (fit_confirmed_by);
CREATE INDEX ix_incident_status ON incident (status);
