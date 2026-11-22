-- Bậc 12: incident_plan, cost_item.

-- Phương án xử lý sự cố, có phiên bản
CREATE TABLE incident_plan (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    incident_id      bigint         NOT NULL,
    version          int            NOT NULL,
    resolution_type  varchar(30)    NOT NULL,
    directive        text           NOT NULL,
    estimated_cost   numeric(15, 2),
    handler_party_id bigint,          -- bên xử lý: thú y, cứu hộ, chuồng gửi
    status           varchar(30)    NOT NULL,
    proposed_by      bigint         NOT NULL,
    proposed_at      timestamptz    NOT NULL DEFAULT now(),
    decided_by       bigint,
    decided_at       timestamptz,
    rejection_reason text,
    CONSTRAINT pk_incident_plan PRIMARY KEY (id),
    CONSTRAINT uk_incident_plan_incident_version UNIQUE (incident_id, version),
    CONSTRAINT fk_incident_plan_incident_id FOREIGN KEY (incident_id) REFERENCES incident (id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_plan_handler_party_id FOREIGN KEY (handler_party_id) REFERENCES party (id),
    CONSTRAINT fk_incident_plan_proposed_by FOREIGN KEY (proposed_by) REFERENCES staff (user_id),
    CONSTRAINT fk_incident_plan_decided_by FOREIGN KEY (decided_by) REFERENCES staff (user_id),
    CONSTRAINT ck_incident_plan_version CHECK (version >= 1),
    CONSTRAINT ck_incident_plan_resolution_type CHECK (resolution_type IN ('CONTINUE', 'VET_TREATMENT', 'STABLE_NEARBY', 'TRANSFER_VEHICLE', 'RETURN_TO_HUB', 'CANCEL')),
    CONSTRAINT ck_incident_plan_estimated_cost CHECK (estimated_cost >= 0),
    CONSTRAINT ck_incident_plan_status CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'SUPERSEDED', 'DONE')),
    CONSTRAINT ck_incident_plan_rejection CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL),
    CONSTRAINT ck_incident_plan_decided CHECK (status IN ('PROPOSED') OR (decided_by IS NOT NULL AND decided_at IS NOT NULL))
);
-- Mỗi sự cố chỉ có một phương án đang hiệu lực
CREATE UNIQUE INDEX uk_incident_plan_active ON incident_plan (incident_id) WHERE status = 'APPROVED';
CREATE INDEX ix_incident_plan_handler_party_id ON incident_plan (handler_party_id);
CREATE INDEX ix_incident_plan_proposed_by ON incident_plan (proposed_by);
CREATE INDEX ix_incident_plan_decided_by ON incident_plan (decided_by);

-- Chi phí phát sinh. Hóa đơn hoặc biên lai là một document có cost_item_id trỏ về dòng này
CREATE TABLE cost_item (
    id                 bigint GENERATED ALWAYS AS IDENTITY,
    category           varchar(30)    NOT NULL,
    description        varchar(500)   NOT NULL,
    amount             numeric(15, 2) NOT NULL,
    currency           char(3)        NOT NULL,
    payer              varchar(30)    NOT NULL,   -- ai chịu khoản này
    booking_id         bigint,
    vehicle_trip_id    bigint,
    incident_id        bigint,
    handover_id        bigint,
    horse_stay_id      bigint,
    customer_consulted boolean        NOT NULL DEFAULT false,
    incurred_at        timestamptz    NOT NULL,
    recorded_by        bigint         NOT NULL,
    approved_by        bigint,
    approved_at        timestamptz,
    extra_payment_id   bigint,
    CONSTRAINT pk_cost_item PRIMARY KEY (id),
    CONSTRAINT fk_cost_item_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_cost_item_vehicle_trip_id FOREIGN KEY (vehicle_trip_id) REFERENCES vehicle_trip (id),
    CONSTRAINT fk_cost_item_incident_id FOREIGN KEY (incident_id) REFERENCES incident (id),
    CONSTRAINT fk_cost_item_handover_id FOREIGN KEY (handover_id) REFERENCES handover (id),
    CONSTRAINT fk_cost_item_horse_stay_id FOREIGN KEY (horse_stay_id) REFERENCES horse_stay (id),
    CONSTRAINT fk_cost_item_recorded_by FOREIGN KEY (recorded_by) REFERENCES staff (user_id),
    CONSTRAINT fk_cost_item_approved_by FOREIGN KEY (approved_by) REFERENCES staff (user_id),
    CONSTRAINT fk_cost_item_extra_payment_id FOREIGN KEY (extra_payment_id) REFERENCES payment (id),
    CONSTRAINT ck_cost_item_category CHECK (category IN ('TOLL', 'FUEL', 'STABLING', 'VET', 'RESCUE', 'REPAIR', 'WAITING', 'OTHER')),
    CONSTRAINT ck_cost_item_amount CHECK (amount > 0),
    CONSTRAINT ck_cost_item_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_cost_item_payer CHECK (payer IN ('COMPANY', 'CUSTOMER', 'CONSIGNEE', 'AGENT')),
    CONSTRAINT ck_cost_item_booking CHECK (payer = 'COMPANY' OR booking_id IS NOT NULL),
    CONSTRAINT ck_cost_item_subject CHECK (num_nonnulls(booking_id, vehicle_trip_id, incident_id) >= 1),
    CONSTRAINT ck_cost_item_approved CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
    CONSTRAINT ck_cost_item_extra_payment CHECK (extra_payment_id IS NULL OR payer <> 'COMPANY')
);
CREATE INDEX ix_cost_item_booking_id ON cost_item (booking_id);
CREATE INDEX ix_cost_item_vehicle_trip_id ON cost_item (vehicle_trip_id);
CREATE INDEX ix_cost_item_incident_id ON cost_item (incident_id);
CREATE INDEX ix_cost_item_handover_id ON cost_item (handover_id);
CREATE INDEX ix_cost_item_horse_stay_id ON cost_item (horse_stay_id);
CREATE INDEX ix_cost_item_recorded_by ON cost_item (recorded_by);
CREATE INDEX ix_cost_item_approved_by ON cost_item (approved_by);
CREATE INDEX ix_cost_item_extra_payment_id ON cost_item (extra_payment_id);
