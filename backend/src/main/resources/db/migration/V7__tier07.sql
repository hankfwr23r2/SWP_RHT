-- Bậc 7: booking.

CREATE TABLE booking (
    id                    bigint GENERATED ALWAYS AS IDENTITY,
    code                  varchar(30)    NOT NULL,
    customer_party_id     bigint         NOT NULL,
    booking_type          varchar(30)    NOT NULL,
    -- Chuyến trục chính. NULL khi chưa gán chuyến hoặc khi đơn chạy thẳng. Mỗi đơn tối đa một chặng trục
    departure_id          bigint,
    requested_pickup_date date           NOT NULL,
    waybill_no            varchar(30),
    emergency_spend_limit numeric(15, 2),
    return_of_booking_id  bigint,
    -- Chỉ gồm 12 mốc thương mại. Tiến độ vận hành suy ra từ horse_leg, horse_stay, handover, incident
    status                varchar(30)    NOT NULL,
    created_by            bigint         NOT NULL,
    created_at            timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT pk_booking PRIMARY KEY (id),
    CONSTRAINT uk_booking_code UNIQUE (code),
    CONSTRAINT uk_booking_waybill_no UNIQUE (waybill_no),
    CONSTRAINT fk_booking_customer_party_id FOREIGN KEY (customer_party_id) REFERENCES party (id),
    CONSTRAINT fk_booking_departure_id FOREIGN KEY (departure_id) REFERENCES departure (id),
    CONSTRAINT fk_booking_return_of_booking_id FOREIGN KEY (return_of_booking_id) REFERENCES booking (id),
    CONSTRAINT fk_booking_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_booking_booking_type CHECK (booking_type IN ('LOCAL', 'INTERPROVINCE', 'EXPORT', 'IMPORT')),
    CONSTRAINT ck_booking_emergency_spend_limit CHECK (emergency_spend_limit >= 0),
    CONSTRAINT ck_booking_status CHECK (status IN ('PENDING_INTAKE', 'UNDER_REVIEW', 'NEEDS_RESUBMISSION', 'QUOTING', 'AWAITING_DEPOSIT',
                                                   'QUOTE_EXPIRED', 'CONFIRMED', 'SCHEDULE_CHANGE_REQUIRED', 'IN_OPERATION',
                                                   'DELIVERED', 'COMPLETED', 'CANCELLED', 'REJECTED'))
);
CREATE INDEX ix_booking_customer_party_id ON booking (customer_party_id);
CREATE INDEX ix_booking_departure_id ON booking (departure_id);
CREATE INDEX ix_booking_return_of_booking_id ON booking (return_of_booking_id);
CREATE INDEX ix_booking_created_by ON booking (created_by);
CREATE INDEX ix_booking_status_created_at ON booking (status, created_at);
