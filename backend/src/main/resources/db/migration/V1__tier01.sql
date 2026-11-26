-- Bậc 1: bảng không phụ thuộc bảng nào khác.
-- Đặc tả: docs/DB_SCHEMA_59_BANG.md (nhóm 1, 2, 3, 5, 6, 7, 15).

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- cho EXCLUDE chống trùng thời hạn hiệu lực

CREATE TABLE country (
    code             char(2)      NOT NULL,
    name             varchar(200) NOT NULL,
    default_currency char(3)      NOT NULL,
    CONSTRAINT pk_country PRIMARY KEY (code),
    CONSTRAINT ck_country_code CHECK (code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_country_currency CHECK (default_currency ~ '^[A-Z]{3}$')
);

CREATE TABLE role (
    code varchar(30)  NOT NULL,
    name varchar(200) NOT NULL,
    CONSTRAINT pk_role PRIMARY KEY (code),
    CONSTRAINT ck_role_code CHECK (code IN ('CUSTOMER', 'ADMIN', 'MANAGER', 'COORDINATOR', 'SPECIALIST', 'DRIVER', 'ESCORT'))
);

CREATE TABLE horse_type (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    name        varchar(100) NOT NULL,
    description text,
    CONSTRAINT pk_horse_type PRIMARY KEY (id),
    CONSTRAINT uk_horse_type_name UNIQUE (name)
);

CREATE TABLE vehicle_class (
    id         bigint GENERATED ALWAYS AS IDENTITY,
    code       varchar(30)  NOT NULL,
    name       varchar(200) NOT NULL,
    max_stalls int          NOT NULL,
    CONSTRAINT pk_vehicle_class PRIMARY KEY (id),
    CONSTRAINT uk_vehicle_class_code UNIQUE (code),
    CONSTRAINT ck_vehicle_class_max_stalls CHECK (max_stalls > 0)
);

CREATE TABLE stall_type (
    code varchar(30)  NOT NULL,
    name varchar(200) NOT NULL,
    CONSTRAINT pk_stall_type PRIMARY KEY (code)
);

CREATE TABLE service (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    code            varchar(30)  NOT NULL,
    name            varchar(200) NOT NULL,
    layer           varchar(30)  NOT NULL,
    billing_unit    varchar(30)  NOT NULL,
    is_pass_through boolean      NOT NULL DEFAULT false,
    auto_apply      boolean      NOT NULL DEFAULT false,
    status          varchar(30)  NOT NULL,
    CONSTRAINT pk_service PRIMARY KEY (id),
    CONSTRAINT uk_service_code UNIQUE (code),
    CONSTRAINT ck_service_layer CHECK (layer IN ('LINEHAUL', 'PICKUP', 'DELIVERY', 'DIRECT', 'FUEL', 'STALL', 'QUARANTINE', 'ADD_ON', 'DOCS', 'SURCHARGE')),
    CONSTRAINT ck_service_billing_unit CHECK (billing_unit IN ('PER_STALL', 'PER_KM', 'PER_HORSE', 'PER_HORSE_DAY', 'PER_BOOKING')),
    CONSTRAINT ck_service_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE price_list (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    name             varchar(200)  NOT NULL,
    currency         char(3)       NOT NULL,
    margin_rate      numeric(5, 4) NOT NULL,
    fuel_buffer_rate numeric(5, 4) NOT NULL,
    deposit_rate     numeric(5, 4) NOT NULL,
    valid_from       date          NOT NULL,
    valid_to         date,
    status           varchar(30)   NOT NULL,
    CONSTRAINT pk_price_list PRIMARY KEY (id),
    CONSTRAINT ck_price_list_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_price_list_margin_rate CHECK (margin_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_price_list_fuel_buffer_rate CHECK (fuel_buffer_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_price_list_deposit_rate CHECK (deposit_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_price_list_valid_to CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_price_list_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    -- Các bảng giá ACTIVE cùng tiền tệ không được trùng thời hạn hiệu lực
    CONSTRAINT ex_price_list_active_period EXCLUDE USING gist (
        currency WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    ) WHERE (status = 'ACTIVE')
);

CREATE TABLE document_type (
    id      bigint GENERATED ALWAYS AS IDENTITY,
    code    varchar(30)  NOT NULL,
    name    varchar(200) NOT NULL,
    subject varchar(30)  NOT NULL,
    CONSTRAINT pk_document_type PRIMARY KEY (id),
    CONSTRAINT uk_document_type_code UNIQUE (code),
    CONSTRAINT ck_document_type_subject CHECK (subject IN ('HORSE', 'BOOKING', 'BOOKING_HORSE', 'HORSE_LEG', 'VEHICLE_TRIP', 'INSURANCE',
                                                           'INCIDENT', 'COST_ITEM', 'HANDOVER', 'VEHICLE', 'STAFF', 'PARTY'))
);

CREATE TABLE app_user (
    id                 bigint GENERATED ALWAYS AS IDENTITY,
    username           varchar(50)  NOT NULL,
    email              varchar(254) NOT NULL,
    password_hash      varchar(100) NOT NULL,
    email_verified     boolean      NOT NULL DEFAULT false,
    preferred_language varchar(5)   NOT NULL DEFAULT 'vi',
    status             varchar(30)  NOT NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_language CHECK (preferred_language IN ('vi', 'en')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);
-- Email duy nhất, so sánh không phân biệt hoa thường
CREATE UNIQUE INDEX uk_app_user_email ON app_user (lower(email));

-- Bộ đệm kết quả tính lộ trình. Không phải dữ liệu nghiệp vụ, có thể xóa toàn bộ bất kỳ lúc nào.
CREATE TABLE route_cache (
    cache_key  varchar(200) NOT NULL,
    payload    text         NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_route_cache PRIMARY KEY (cache_key)
);
CREATE INDEX ix_route_cache_created_at ON route_cache (created_at);
