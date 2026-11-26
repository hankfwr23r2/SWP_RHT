-- Bậc 5: route_stop, departure_schedule, price_item, system_config.

CREATE TABLE route_stop (
    route_id       bigint      NOT NULL,
    seq            int         NOT NULL,
    location_id    bigint      NOT NULL,
    stop_type      varchar(30) NOT NULL,
    offset_minutes int         NOT NULL,
    dwell_minutes  int         NOT NULL,
    -- PK (route_id, seq) nên một tuyến có thể ghé cùng một điểm hai lần
    CONSTRAINT pk_route_stop PRIMARY KEY (route_id, seq),
    CONSTRAINT fk_route_stop_route_id FOREIGN KEY (route_id) REFERENCES route (id) ON DELETE CASCADE,
    CONSTRAINT fk_route_stop_location_id FOREIGN KEY (location_id) REFERENCES location (id),
    CONSTRAINT ck_route_stop_seq CHECK (seq >= 1),
    CONSTRAINT ck_route_stop_stop_type CHECK (stop_type IN ('HUB', 'REST', 'VITAL_CHECK', 'BORDER', 'QUARANTINE')),
    CONSTRAINT ck_route_stop_offset_minutes CHECK (offset_minutes >= 0),
    CONSTRAINT ck_route_stop_dwell_minutes CHECK (dwell_minutes >= 0)
);
CREATE INDEX ix_route_stop_location_id ON route_stop (location_id);

CREATE TABLE departure_schedule (
    id                   bigint GENERATED ALWAYS AS IDENTITY,
    route_id             bigint      NOT NULL,
    departure_time       time        NOT NULL,
    booking_cutoff_hours int         NOT NULL,
    capacity_stalls      int         NOT NULL,
    valid_from           date        NOT NULL,
    valid_to             date,
    status               varchar(30) NOT NULL,
    CONSTRAINT pk_departure_schedule PRIMARY KEY (id),
    CONSTRAINT fk_departure_schedule_route_id FOREIGN KEY (route_id) REFERENCES route (id),
    CONSTRAINT ck_departure_schedule_booking_cutoff_hours CHECK (booking_cutoff_hours > 0),
    CONSTRAINT ck_departure_schedule_capacity_stalls CHECK (capacity_stalls > 0),
    CONSTRAINT ck_departure_schedule_valid_to CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_departure_schedule_status CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED'))
);
CREATE INDEX ix_departure_schedule_route_id ON departure_schedule (route_id);

CREATE TABLE price_item (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    price_list_id    bigint         NOT NULL,
    service_id       bigint         NOT NULL,
    route_id         bigint,
    vehicle_class_id bigint,
    stall_type       varchar(30),
    km_from          numeric(8, 2),
    km_to            numeric(8, 2),
    unit_price       numeric(15, 2) NOT NULL,
    CONSTRAINT pk_price_item PRIMARY KEY (id),
    CONSTRAINT uk_price_item UNIQUE NULLS NOT DISTINCT (price_list_id, service_id, route_id, vehicle_class_id, stall_type, km_from),
    CONSTRAINT fk_price_item_price_list_id FOREIGN KEY (price_list_id) REFERENCES price_list (id) ON DELETE CASCADE,
    CONSTRAINT fk_price_item_service_id FOREIGN KEY (service_id) REFERENCES service (id),
    CONSTRAINT fk_price_item_route_id FOREIGN KEY (route_id) REFERENCES route (id),
    CONSTRAINT fk_price_item_vehicle_class_id FOREIGN KEY (vehicle_class_id) REFERENCES vehicle_class (id),
    CONSTRAINT fk_price_item_stall_type FOREIGN KEY (stall_type) REFERENCES stall_type (code),
    CONSTRAINT ck_price_item_km_pair CHECK ((km_from IS NULL) = (km_to IS NULL)),
    CONSTRAINT ck_price_item_km_range CHECK (km_from IS NULL OR km_from < km_to),
    CONSTRAINT ck_price_item_unit_price CHECK (unit_price >= 0)
);
CREATE INDEX ix_price_item_service_id ON price_item (service_id);
CREATE INDEX ix_price_item_route_id ON price_item (route_id);
CREATE INDEX ix_price_item_vehicle_class_id ON price_item (vehicle_class_id);
CREATE INDEX ix_price_item_stall_type ON price_item (stall_type);

-- Chỉ chứa tham số kỹ thuật, không chứa giá tiền (giá nằm ở price_list/price_item)
CREATE TABLE system_config (
    key          varchar(100) NOT NULL,
    value        varchar(500) NOT NULL,
    unit         varchar(30),
    config_group varchar(50)  NOT NULL,
    description  text,
    updated_by   bigint,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_system_config PRIMARY KEY (key),
    CONSTRAINT fk_system_config_updated_by FOREIGN KEY (updated_by) REFERENCES staff (user_id)
);
CREATE INDEX ix_system_config_updated_by ON system_config (updated_by);
