-- Bậc 4: hub_service_area, staff, party_role, party_address, horse, route, vehicle, document_requirement.

CREATE TABLE hub_service_area (
    hub_location_id bigint      NOT NULL,
    province_code   varchar(10) NOT NULL,
    CONSTRAINT pk_hub_service_area PRIMARY KEY (hub_location_id, province_code),
    -- Mỗi tỉnh thuộc đúng một hub; đồng thời là index tra "tỉnh này thuộc hub nào"
    CONSTRAINT uk_hub_service_area_province_code UNIQUE (province_code),
    CONSTRAINT fk_hub_service_area_hub_location_id FOREIGN KEY (hub_location_id) REFERENCES location (id) ON DELETE CASCADE,
    CONSTRAINT fk_hub_service_area_province_code FOREIGN KEY (province_code) REFERENCES province (code)
);

CREATE TABLE staff (
    user_id               bigint       NOT NULL,
    staff_code            varchar(30)  NOT NULL,
    full_name             varchar(200) NOT NULL,
    phone                 varchar(20)  NOT NULL,
    hire_date             date         NOT NULL,
    employment_status     varchar(30)  NOT NULL,
    home_hub_id           bigint       NOT NULL,
    driver_license_class  varchar(10),
    driver_license_expiry date,
    CONSTRAINT pk_staff PRIMARY KEY (user_id),
    CONSTRAINT uk_staff_staff_code UNIQUE (staff_code),
    CONSTRAINT fk_staff_user_id FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_staff_home_hub_id FOREIGN KEY (home_hub_id) REFERENCES location (id),
    CONSTRAINT ck_staff_employment_status CHECK (employment_status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED')),
    CONSTRAINT ck_staff_driver_license CHECK ((driver_license_class IS NULL) = (driver_license_expiry IS NULL))
);
CREATE INDEX ix_staff_home_hub_id ON staff (home_hub_id);

CREATE TABLE party_role (
    party_id   bigint      NOT NULL,
    role_type  varchar(30) NOT NULL,
    valid_from date        NOT NULL,
    valid_to   date,
    CONSTRAINT pk_party_role PRIMARY KEY (party_id, role_type),
    CONSTRAINT fk_party_role_party_id FOREIGN KEY (party_id) REFERENCES party (id) ON DELETE CASCADE,
    CONSTRAINT ck_party_role_role_type CHECK (role_type IN ('CUSTOMER', 'CONSIGNEE', 'DEST_AGENT', 'QUARANTINE_FARM', 'STABLE', 'VET', 'RESCUE', 'INSURER')),
    CONSTRAINT ck_party_role_valid_to CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE INDEX ix_party_role_role_type ON party_role (role_type);

CREATE TABLE party_address (
    id            bigint GENERATED ALWAYS AS IDENTITY,
    party_id      bigint        NOT NULL,
    label         varchar(100)  NOT NULL,
    address_line  varchar(500)  NOT NULL,
    ward          varchar(100),
    province_code varchar(10)   NOT NULL,
    country_code  char(2)       NOT NULL,
    latitude      numeric(9, 6),
    longitude     numeric(9, 6),
    is_default    boolean       NOT NULL DEFAULT false,
    CONSTRAINT pk_party_address PRIMARY KEY (id),
    CONSTRAINT fk_party_address_party_id FOREIGN KEY (party_id) REFERENCES party (id) ON DELETE CASCADE,
    CONSTRAINT fk_party_address_province_code FOREIGN KEY (province_code) REFERENCES province (code),
    CONSTRAINT fk_party_address_country_code FOREIGN KEY (country_code) REFERENCES country (code),
    CONSTRAINT ck_party_address_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL))
);
-- Mỗi bên chỉ có một địa chỉ mặc định
CREATE UNIQUE INDEX uk_party_address_default ON party_address (party_id) WHERE is_default;
CREATE INDEX ix_party_address_party_id ON party_address (party_id);
CREATE INDEX ix_party_address_province_code ON party_address (province_code);
CREATE INDEX ix_party_address_country_code ON party_address (country_code);

CREATE TABLE horse (
    id                   bigint GENERATED ALWAYS AS IDENTITY,
    owner_party_id       bigint        NOT NULL,
    name                 varchar(100)  NOT NULL,
    microchip            varchar(30),
    passport_no          varchar(50),
    gender               varchar(30)   NOT NULL,
    birth_year           smallint,
    color                varchar(50),
    breed                varchar(100),
    sire                 varchar(100),
    dam                  varchar(100),
    horse_type_id        bigint        NOT NULL,
    identification_marks text,
    weight_kg            numeric(6, 1),
    declared_value       numeric(15, 2),
    declared_currency    char(3),
    status               varchar(30)   NOT NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz,
    CONSTRAINT pk_horse PRIMARY KEY (id),
    CONSTRAINT uk_horse_microchip UNIQUE (microchip),
    CONSTRAINT uk_horse_passport_no UNIQUE (passport_no),
    CONSTRAINT fk_horse_owner_party_id FOREIGN KEY (owner_party_id) REFERENCES party (id),
    CONSTRAINT fk_horse_horse_type_id FOREIGN KEY (horse_type_id) REFERENCES horse_type (id),
    CONSTRAINT ck_horse_gender CHECK (gender IN ('STALLION', 'MARE', 'GELDING')),
    CONSTRAINT ck_horse_birth_year CHECK (birth_year BETWEEN 1980 AND 2100),
    CONSTRAINT ck_horse_weight_kg CHECK (weight_kg > 0),
    CONSTRAINT ck_horse_declared_value CHECK (declared_value >= 0),
    CONSTRAINT ck_horse_declared_currency CHECK (declared_currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_horse_declared CHECK ((declared_value IS NULL) = (declared_currency IS NULL)),
    CONSTRAINT ck_horse_status CHECK (status IN ('ACTIVE', 'RETIRED', 'DECEASED'))
);
CREATE INDEX ix_horse_owner_party_id ON horse (owner_party_id);
CREATE INDEX ix_horse_horse_type_id ON horse (horse_type_id);

CREATE TABLE route (
    id                      bigint GENERATED ALWAYS AS IDENTITY,
    code                    varchar(30)   NOT NULL,
    name                    varchar(200)  NOT NULL,
    origin_hub_id           bigint        NOT NULL,
    destination_location_id bigint        NOT NULL,
    distance_km             numeric(8, 2) NOT NULL,
    est_duration_minutes    int           NOT NULL,
    status                  varchar(30)   NOT NULL,
    CONSTRAINT pk_route PRIMARY KEY (id),
    CONSTRAINT uk_route_code UNIQUE (code),
    CONSTRAINT fk_route_origin_hub_id FOREIGN KEY (origin_hub_id) REFERENCES location (id),
    CONSTRAINT fk_route_destination_location_id FOREIGN KEY (destination_location_id) REFERENCES location (id),
    CONSTRAINT ck_route_distance_km CHECK (distance_km > 0),
    CONSTRAINT ck_route_est_duration_minutes CHECK (est_duration_minutes > 0),
    CONSTRAINT ck_route_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_route_endpoints CHECK (origin_hub_id <> destination_location_id)
);
CREATE INDEX ix_route_origin_hub_id ON route (origin_hub_id);
CREATE INDEX ix_route_destination_location_id ON route (destination_location_id);

CREATE TABLE vehicle (
    id                  bigint GENERATED ALWAYS AS IDENTITY,
    license_plate       varchar(20) NOT NULL,
    vehicle_class_id    bigint      NOT NULL,
    standard_stalls     int         NOT NULL,
    wide_stalls         int         NOT NULL,
    home_hub_id         bigint      NOT NULL,
    registration_expiry date,
    status              varchar(30) NOT NULL,
    CONSTRAINT pk_vehicle PRIMARY KEY (id),
    CONSTRAINT uk_vehicle_license_plate UNIQUE (license_plate),
    CONSTRAINT fk_vehicle_vehicle_class_id FOREIGN KEY (vehicle_class_id) REFERENCES vehicle_class (id),
    CONSTRAINT fk_vehicle_home_hub_id FOREIGN KEY (home_hub_id) REFERENCES location (id),
    CONSTRAINT ck_vehicle_standard_stalls CHECK (standard_stalls >= 0),
    CONSTRAINT ck_vehicle_wide_stalls CHECK (wide_stalls >= 0),
    CONSTRAINT ck_vehicle_total_stalls CHECK (standard_stalls + wide_stalls > 0),
    CONSTRAINT ck_vehicle_status CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'RETIRED'))
);
CREATE INDEX ix_vehicle_vehicle_class_id ON vehicle (vehicle_class_id);
CREATE INDEX ix_vehicle_home_hub_id ON vehicle (home_hub_id);

CREATE TABLE document_requirement (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    rule_id          bigint      NOT NULL,
    document_type_id bigint      NOT NULL,
    gate_location_id bigint,
    submitted_by     varchar(30) NOT NULL,
    is_mandatory     boolean     NOT NULL DEFAULT true,
    CONSTRAINT pk_document_requirement PRIMARY KEY (id),
    CONSTRAINT uk_document_requirement UNIQUE NULLS NOT DISTINCT (rule_id, document_type_id, gate_location_id),
    CONSTRAINT fk_document_requirement_rule_id FOREIGN KEY (rule_id) REFERENCES country_rule (id) ON DELETE CASCADE,
    CONSTRAINT fk_document_requirement_document_type_id FOREIGN KEY (document_type_id) REFERENCES document_type (id),
    CONSTRAINT fk_document_requirement_gate_location_id FOREIGN KEY (gate_location_id) REFERENCES location (id),
    CONSTRAINT ck_document_requirement_submitted_by CHECK (submitted_by IN ('CUSTOMER', 'CONSIGNEE', 'COMPANY', 'AGENT'))
);
CREATE INDEX ix_document_requirement_document_type_id ON document_requirement (document_type_id);
CREATE INDEX ix_document_requirement_gate_location_id ON document_requirement (gate_location_id);
