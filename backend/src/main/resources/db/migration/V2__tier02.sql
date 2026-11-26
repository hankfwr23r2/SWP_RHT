-- Bậc 2: province, user_role, otp, country_rule.

CREATE TABLE province (
    code         varchar(10)  NOT NULL,
    country_code char(2)      NOT NULL,
    name         varchar(200) NOT NULL,
    CONSTRAINT pk_province PRIMARY KEY (code),
    CONSTRAINT fk_province_country_code FOREIGN KEY (country_code) REFERENCES country (code)
);
CREATE INDEX ix_province_country_code ON province (country_code);

CREATE TABLE user_role (
    user_id   bigint      NOT NULL,
    role_code varchar(30) NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_user_role_user_id FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role_code FOREIGN KEY (role_code) REFERENCES role (code)
);
CREATE INDEX ix_user_role_role_code ON user_role (role_code);

CREATE TABLE otp (
    id           bigint GENERATED ALWAYS AS IDENTITY,
    email        varchar(254) NOT NULL,
    user_id      bigint,
    purpose      varchar(30)  NOT NULL,
    otp_hash     varchar(100) NOT NULL,
    expires_at   timestamptz  NOT NULL,
    last_sent_at timestamptz  NOT NULL,
    attempts     int          NOT NULL DEFAULT 0,
    CONSTRAINT pk_otp PRIMARY KEY (id),
    CONSTRAINT uk_otp_email_purpose UNIQUE (email, purpose),
    CONSTRAINT fk_otp_user_id FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_otp_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    CONSTRAINT ck_otp_attempts CHECK (attempts >= 0)
);
CREATE INDEX ix_otp_user_id ON otp (user_id);

CREATE TABLE country_rule (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    country_code     char(2)     NOT NULL,
    direction        varchar(30) NOT NULL,
    permit_lead_days int         NOT NULL,
    cert_lead_days   int         NOT NULL,
    quarantine_days  int         NOT NULL,
    valid_from       date        NOT NULL,
    valid_to         date,
    CONSTRAINT pk_country_rule PRIMARY KEY (id),
    CONSTRAINT fk_country_rule_country_code FOREIGN KEY (country_code) REFERENCES country (code),
    CONSTRAINT ck_country_rule_direction CHECK (direction IN ('INTERPROVINCE', 'EXPORT', 'IMPORT')),
    CONSTRAINT ck_country_rule_permit_lead_days CHECK (permit_lead_days >= 0),
    CONSTRAINT ck_country_rule_cert_lead_days CHECK (cert_lead_days >= 0),
    CONSTRAINT ck_country_rule_quarantine_days CHECK (quarantine_days >= 0),
    CONSTRAINT ck_country_rule_valid_to CHECK (valid_to IS NULL OR valid_to >= valid_from),
    -- Cùng nước và cùng hướng thì không được trùng thời hạn hiệu lực
    CONSTRAINT ex_country_rule_period EXCLUDE USING gist (
        country_code WITH =,
        direction WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    )
);
