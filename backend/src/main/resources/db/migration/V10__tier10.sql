-- Bậc 10: horse_leg, handover, payment_transaction.

-- Một chặng di chuyển của một con ngựa. Do xe công ty chở (vehicle_trip_id) hoặc đại lý đảm nhận (agent_party_id)
CREATE TABLE horse_leg (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    booking_horse_id bigint      NOT NULL,
    seq              int         NOT NULL,
    stage            varchar(30) NOT NULL,
    from_location_id bigint,
    from_address_id  bigint,
    to_location_id   bigint,
    to_address_id    bigint,
    vehicle_trip_id  bigint,
    agent_party_id   bigint,
    board_stop_id    bigint,
    alight_stop_id   bigint,
    replaces_leg_id  bigint,
    planned_start    timestamptz NOT NULL,
    planned_end      timestamptz NOT NULL,
    actual_start     timestamptz,
    actual_end       timestamptz,
    status           varchar(30) NOT NULL,
    CONSTRAINT pk_horse_leg PRIMARY KEY (id),
    CONSTRAINT uk_horse_leg_horse_seq UNIQUE (booking_horse_id, seq),
    CONSTRAINT fk_horse_leg_booking_horse_id FOREIGN KEY (booking_horse_id) REFERENCES booking_horse (id),
    CONSTRAINT fk_horse_leg_from_location_id FOREIGN KEY (from_location_id) REFERENCES location (id),
    CONSTRAINT fk_horse_leg_from_address_id FOREIGN KEY (from_address_id) REFERENCES booking_address (id),
    CONSTRAINT fk_horse_leg_to_location_id FOREIGN KEY (to_location_id) REFERENCES location (id),
    CONSTRAINT fk_horse_leg_to_address_id FOREIGN KEY (to_address_id) REFERENCES booking_address (id),
    CONSTRAINT fk_horse_leg_vehicle_trip_id FOREIGN KEY (vehicle_trip_id) REFERENCES vehicle_trip (id),
    CONSTRAINT fk_horse_leg_agent_party_id FOREIGN KEY (agent_party_id) REFERENCES party (id),
    CONSTRAINT fk_horse_leg_board_stop_id FOREIGN KEY (board_stop_id) REFERENCES trip_stop (id),
    CONSTRAINT fk_horse_leg_alight_stop_id FOREIGN KEY (alight_stop_id) REFERENCES trip_stop (id),
    CONSTRAINT fk_horse_leg_replaces_leg_id FOREIGN KEY (replaces_leg_id) REFERENCES horse_leg (id),
    CONSTRAINT ck_horse_leg_seq CHECK (seq >= 1),
    CONSTRAINT ck_horse_leg_stage CHECK (stage IN ('PICKUP', 'LINEHAUL', 'DELIVERY', 'TRANSFER', 'RETURN', 'DIRECT')),
    CONSTRAINT ck_horse_leg_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_horse_leg_from CHECK (num_nonnulls(from_location_id, from_address_id) = 1),
    CONSTRAINT ck_horse_leg_to CHECK (num_nonnulls(to_location_id, to_address_id) = 1),
    -- Xe công ty hoặc đại lý, đúng một
    CONSTRAINT ck_horse_leg_carrier CHECK (num_nonnulls(vehicle_trip_id, agent_party_id) = 1),
    -- Chặng do đại lý chở thì không có điểm lên xuống trên xe công ty
    CONSTRAINT ck_horse_leg_agent_stops CHECK (agent_party_id IS NULL OR (board_stop_id IS NULL AND alight_stop_id IS NULL)),
    CONSTRAINT ck_horse_leg_planned CHECK (planned_end > planned_start)
);
CREATE INDEX ix_horse_leg_from_location_id ON horse_leg (from_location_id);
CREATE INDEX ix_horse_leg_from_address_id ON horse_leg (from_address_id);
CREATE INDEX ix_horse_leg_to_location_id ON horse_leg (to_location_id);
CREATE INDEX ix_horse_leg_to_address_id ON horse_leg (to_address_id);
CREATE INDEX ix_horse_leg_vehicle_trip_id ON horse_leg (vehicle_trip_id);
CREATE INDEX ix_horse_leg_agent_party_id ON horse_leg (agent_party_id);
CREATE INDEX ix_horse_leg_board_stop_id ON horse_leg (board_stop_id);
CREATE INDEX ix_horse_leg_alight_stop_id ON horse_leg (alight_stop_id);
CREATE INDEX ix_horse_leg_replaces_leg_id ON horse_leg (replaces_leg_id);
CREATE INDEX ix_horse_leg_status ON horse_leg (status);

-- Một lần chuyển giao ngựa giữa các bên. Biên bản ký là một document có handover_id trỏ về dòng này
CREATE TABLE handover (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    kind            varchar(30) NOT NULL,
    trip_stop_id    bigint,
    horse_stay_id   bigint,
    from_party_id   bigint,   -- NULL = công ty
    to_party_id     bigint,   -- NULL = công ty
    staff_id        bigint      NOT NULL,
    wait_started_at timestamptz,
    handed_at       timestamptz,
    status          varchar(30) NOT NULL,
    note            text,
    CONSTRAINT pk_handover PRIMARY KEY (id),
    CONSTRAINT fk_handover_trip_stop_id FOREIGN KEY (trip_stop_id) REFERENCES trip_stop (id),
    CONSTRAINT fk_handover_horse_stay_id FOREIGN KEY (horse_stay_id) REFERENCES horse_stay (id),
    CONSTRAINT fk_handover_from_party_id FOREIGN KEY (from_party_id) REFERENCES party (id),
    CONSTRAINT fk_handover_to_party_id FOREIGN KEY (to_party_id) REFERENCES party (id),
    CONSTRAINT fk_handover_staff_id FOREIGN KEY (staff_id) REFERENCES staff (user_id),
    CONSTRAINT ck_handover_kind CHECK (kind IN ('PICKUP', 'HUB_IN', 'HUB_OUT', 'STAY_IN', 'STAY_OUT', 'AGENT', 'DELIVERY', 'RETURN')),
    CONSTRAINT ck_handover_status CHECK (status IN ('PENDING', 'DONE', 'TIMEOUT', 'FAILED')),
    CONSTRAINT ck_handover_place CHECK (num_nonnulls(trip_stop_id, horse_stay_id) = 1),
    CONSTRAINT ck_handover_parties CHECK (from_party_id IS DISTINCT FROM to_party_id),
    CONSTRAINT ck_handover_done CHECK (status <> 'DONE' OR handed_at IS NOT NULL)
);
CREATE INDEX ix_handover_trip_stop_id ON handover (trip_stop_id);
CREATE INDEX ix_handover_horse_stay_id ON handover (horse_stay_id);
CREATE INDEX ix_handover_from_party_id ON handover (from_party_id);
CREATE INDEX ix_handover_to_party_id ON handover (to_party_id);
CREATE INDEX ix_handover_staff_id ON handover (staff_id);

-- Giao dịch với cổng thanh toán
CREATE TABLE payment_transaction (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    payment_id      bigint         NOT NULL,
    gateway         varchar(30)    NOT NULL,
    gateway_txn_no  varchar(100)   NOT NULL,
    bank_code       varchar(30),
    response_code   varchar(10)    NOT NULL,
    amount          numeric(15, 2) NOT NULL,
    status          varchar(30)    NOT NULL,
    gateway_paid_at timestamptz,
    received_at     timestamptz    NOT NULL DEFAULT now(),
    raw_payload     jsonb          NOT NULL,   -- ngoại lệ được phép dùng JSON: phản hồi thô của cổng
    CONSTRAINT pk_payment_transaction PRIMARY KEY (id),
    -- Cổng gửi lại callback nhiều lần cũng không tạo bản ghi trùng
    CONSTRAINT uk_payment_transaction_gateway_txn UNIQUE (gateway, gateway_txn_no),
    CONSTRAINT fk_payment_transaction_payment_id FOREIGN KEY (payment_id) REFERENCES payment (id),
    CONSTRAINT ck_payment_transaction_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_transaction_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);
CREATE INDEX ix_payment_transaction_payment_id ON payment_transaction (payment_id);
