-- Bậc 13: document.
-- Mỗi chứng từ thuộc đúng một đối tượng (một cột chủ sở hữu có giá trị), khớp với document_type.subject (kiểm ở service).

CREATE TABLE document (
    id                 bigint GENERATED ALWAYS AS IDENTITY,
    document_type_id   bigint        NOT NULL,
    requirement_id     bigint,
    file_url           varchar(1000) NOT NULL,
    file_name          varchar(255)  NOT NULL,
    document_number    varchar(100),
    issue_date         date,
    expiry_date        date,
    uploaded_by        bigint        NOT NULL,
    uploaded_at        timestamptz   NOT NULL DEFAULT now(),
    captured_at        timestamptz,     -- thời điểm chụp, với ảnh hiện trường
    latitude           numeric(9, 6),
    longitude          numeric(9, 6),
    status             varchar(30)   NOT NULL,
    reviewed_by        bigint,
    reviewed_at        timestamptz,
    review_note        text,
    -- Chủ sở hữu: đúng một cột có giá trị
    horse_id           bigint,
    booking_id         bigint,
    booking_horse_id   bigint,
    horse_leg_id       bigint,
    vehicle_trip_id    bigint,
    horse_insurance_id bigint,
    incident_id        bigint,
    cost_item_id       bigint,
    handover_id        bigint,
    vehicle_id         bigint,
    staff_id           bigint,
    party_id           bigint,
    CONSTRAINT pk_document PRIMARY KEY (id),
    CONSTRAINT uk_document_file_url UNIQUE (file_url),
    CONSTRAINT fk_document_document_type_id FOREIGN KEY (document_type_id) REFERENCES document_type (id),
    CONSTRAINT fk_document_requirement_id FOREIGN KEY (requirement_id) REFERENCES document_requirement (id),
    CONSTRAINT fk_document_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES app_user (id),
    CONSTRAINT fk_document_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES staff (user_id),
    CONSTRAINT fk_document_horse_id FOREIGN KEY (horse_id) REFERENCES horse (id),
    CONSTRAINT fk_document_booking_id FOREIGN KEY (booking_id) REFERENCES booking (id),
    CONSTRAINT fk_document_booking_horse_id FOREIGN KEY (booking_horse_id) REFERENCES booking_horse (id),
    CONSTRAINT fk_document_horse_leg_id FOREIGN KEY (horse_leg_id) REFERENCES horse_leg (id),
    CONSTRAINT fk_document_vehicle_trip_id FOREIGN KEY (vehicle_trip_id) REFERENCES vehicle_trip (id),
    CONSTRAINT fk_document_horse_insurance_id FOREIGN KEY (horse_insurance_id) REFERENCES horse_insurance (id),
    CONSTRAINT fk_document_incident_id FOREIGN KEY (incident_id) REFERENCES incident (id),
    CONSTRAINT fk_document_cost_item_id FOREIGN KEY (cost_item_id) REFERENCES cost_item (id),
    CONSTRAINT fk_document_handover_id FOREIGN KEY (handover_id) REFERENCES handover (id),
    CONSTRAINT fk_document_vehicle_id FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_document_staff_id FOREIGN KEY (staff_id) REFERENCES staff (user_id),
    CONSTRAINT fk_document_party_id FOREIGN KEY (party_id) REFERENCES party (id),
    CONSTRAINT ck_document_expiry_date CHECK (expiry_date IS NULL OR issue_date IS NULL OR expiry_date >= issue_date),
    CONSTRAINT ck_document_coordinates CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ck_document_status CHECK (status IN ('UPLOADED', 'APPROVED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_document_owner CHECK (num_nonnulls(horse_id, booking_id, booking_horse_id, horse_leg_id, vehicle_trip_id,
                                                     horse_insurance_id, incident_id, cost_item_id, handover_id,
                                                     vehicle_id, staff_id, party_id) = 1),
    CONSTRAINT ck_document_reviewed CHECK (status NOT IN ('APPROVED', 'REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT ck_document_rejected CHECK (status <> 'REJECTED' OR review_note IS NOT NULL)
);
CREATE INDEX ix_document_document_type_id ON document (document_type_id);
CREATE INDEX ix_document_requirement_id ON document (requirement_id);
CREATE INDEX ix_document_uploaded_by ON document (uploaded_by);
CREATE INDEX ix_document_reviewed_by ON document (reviewed_by);
CREATE INDEX ix_document_horse ON document (horse_id) WHERE horse_id IS NOT NULL;
CREATE INDEX ix_document_booking ON document (booking_id) WHERE booking_id IS NOT NULL;
CREATE INDEX ix_document_booking_horse ON document (booking_horse_id) WHERE booking_horse_id IS NOT NULL;
CREATE INDEX ix_document_horse_leg ON document (horse_leg_id) WHERE horse_leg_id IS NOT NULL;
CREATE INDEX ix_document_vehicle_trip ON document (vehicle_trip_id) WHERE vehicle_trip_id IS NOT NULL;
CREATE INDEX ix_document_horse_insurance ON document (horse_insurance_id) WHERE horse_insurance_id IS NOT NULL;
CREATE INDEX ix_document_incident ON document (incident_id) WHERE incident_id IS NOT NULL;
CREATE INDEX ix_document_cost_item ON document (cost_item_id) WHERE cost_item_id IS NOT NULL;
CREATE INDEX ix_document_handover ON document (handover_id) WHERE handover_id IS NOT NULL;
CREATE INDEX ix_document_vehicle ON document (vehicle_id) WHERE vehicle_id IS NOT NULL;
CREATE INDEX ix_document_staff ON document (staff_id) WHERE staff_id IS NOT NULL;
CREATE INDEX ix_document_party ON document (party_id) WHERE party_id IS NOT NULL;
