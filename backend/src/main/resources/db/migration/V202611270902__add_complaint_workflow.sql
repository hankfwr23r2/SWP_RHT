ALTER TABLE incident
    ADD COLUMN booking_horse_id bigint REFERENCES booking_horse (id),
    ADD COLUMN complaint_reply text,
    ADD COLUMN complaint_decision varchar(30),
    ADD COLUMN complaint_decision_reason text,
    ADD COLUMN complaint_decided_by bigint REFERENCES staff (user_id),
    ADD CONSTRAINT ck_incident_complaint_details CHECK (
        incident_type = 'COMPLAINT' OR
        num_nonnulls(booking_horse_id, complaint_reply, complaint_decision,
                    complaint_decision_reason, complaint_decided_by) = 0
    ),
    ADD CONSTRAINT ck_incident_complaint_decision CHECK (
        (complaint_decision IS NULL AND complaint_decision_reason IS NULL AND complaint_decided_by IS NULL)
        OR (complaint_decision IS NOT NULL AND complaint_decision IN ('ACCEPTED', 'REJECTED')
            AND complaint_decision_reason IS NOT NULL AND complaint_decided_by IS NOT NULL
            AND status = 'CLOSED' AND resolved_at IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_incident_complaint_booking ON incident (booking_id)
    WHERE incident_type = 'COMPLAINT';

INSERT INTO document_type (code, name, owner_type)
VALUES ('COMPLAINT_PHOTO', 'Ảnh khiếu nại', 'INCIDENT');

INSERT INTO system_config (config_key, config_value, unit, config_group, description)
VALUES ('COMPLAINT_MANAGER_DEADLINE_DAYS', '14', 'DAY', 'BOOKING', 'Hạn quản lý ghi nhận khiếu nại thay khách kể từ lúc giao');
