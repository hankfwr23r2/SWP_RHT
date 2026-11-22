-- Bậc 3: location, party.

CREATE TABLE location (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    code            varchar(30)   NOT NULL,
    name            varchar(200)  NOT NULL,
    type            varchar(30)   NOT NULL,
    country_code    char(2)       NOT NULL,
    province_code   varchar(10),
    address         varchar(500)  NOT NULL,
    latitude        numeric(9, 6),
    longitude       numeric(9, 6),
    opening_minutes int,
    closing_minutes int,
    contact_phone   varchar(20),
    status          varchar(30)   NOT NULL,
    CONSTRAINT pk_location PRIMARY KEY (id),
    CONSTRAINT uk_location_code UNIQUE (code),
    CONSTRAINT fk_location_country_code FOREIGN KEY (country_code) REFERENCES country (code),
    CONSTRAINT fk_location_province_code FOREIGN KEY (province_code) REFERENCES province (code),
    CONSTRAINT ck_location_type CHECK (type IN ('HUB', 'QUARANTINE_FARM', 'REST_STOP', 'BORDER_GATE', 'STABLE', 'RACETRACK')),
    CONSTRAINT ck_location_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_location_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_location_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_location_opening_minutes CHECK (opening_minutes BETWEEN 0 AND 1439),
    CONSTRAINT ck_location_closing_minutes CHECK (closing_minutes BETWEEN 0 AND 1439),
    CONSTRAINT ck_location_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_location_country_code ON location (country_code);
CREATE INDEX ix_location_province_code ON location (province_code);
CREATE INDEX ix_location_type ON location (type);

CREATE TABLE party (
    id           bigint GENERATED ALWAYS AS IDENTITY,
    kind         varchar(30)  NOT NULL,
    display_name varchar(200) NOT NULL,
    phone        varchar(20),
    email        varchar(254),
    address      varchar(500),
    country_code char(2)      NOT NULL,
    id_type      varchar(30),
    id_number    varchar(50),
    id_country   char(2),
    tax_code     varchar(30),
    user_id      bigint,
    status       varchar(30)  NOT NULL,
    CONSTRAINT pk_party PRIMARY KEY (id),
    CONSTRAINT uk_party_user_id UNIQUE (user_id),
    CONSTRAINT uk_party_identity UNIQUE (id_type, id_number, id_country),
    CONSTRAINT fk_party_country_code FOREIGN KEY (country_code) REFERENCES country (code),
    CONSTRAINT fk_party_id_country FOREIGN KEY (id_country) REFERENCES country (code),
    CONSTRAINT fk_party_user_id FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_party_kind CHECK (kind IN ('PERSON', 'ORGANIZATION')),
    CONSTRAINT ck_party_id_type CHECK (id_type IN ('CCCD', 'PASSPORT', 'BUSINESS_REG')),
    -- Giấy tờ: khai đủ cả 3 cột hoặc bỏ trống cả 3
    CONSTRAINT ck_party_identity_complete CHECK (num_nonnulls(id_type, id_number, id_country) IN (0, 3)),
    CONSTRAINT ck_party_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
CREATE INDEX ix_party_country_code ON party (country_code);
CREATE INDEX ix_party_id_country ON party (id_country);
