ALTER TABLE document
    ADD COLUMN evidence_horse_leg_id bigint,
    ADD CONSTRAINT fk_document_evidence_horse_leg FOREIGN KEY (evidence_horse_leg_id) REFERENCES horse_leg (id),
    ADD CONSTRAINT fk_document_evidence_handover_horse FOREIGN KEY (handover_id, evidence_horse_leg_id)
        REFERENCES handover_horse (handover_id, horse_leg_id),
    ADD CONSTRAINT ck_document_evidence_handover CHECK (evidence_horse_leg_id IS NULL OR handover_id IS NOT NULL);

INSERT INTO document_type (code, name, owner_type)
VALUES ('AGENT_DELIVERY_PROOF', 'Bằng chứng đại lý đã giao', 'HORSE_LEG');

ALTER TABLE handover_document DROP CONSTRAINT ck_handover_document_direction;
ALTER TABLE handover_document ADD CONSTRAINT ck_handover_document_direction
    CHECK (direction IN ('RECEIVED', 'NOT_RECEIVED', 'RETURNED'));

ALTER TABLE booking
    ADD COLUMN collection_code varchar(6) CHECK (collection_code ~ '^[0-9]{6}$'),
    ADD COLUMN collection_failed_attempts integer NOT NULL DEFAULT 0 CHECK (collection_failed_attempts >= 0),
    ADD COLUMN collection_confirmed boolean NOT NULL DEFAULT false;

ALTER TABLE handover_horse ADD COLUMN recipient_name varchar(120);

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('COLLECTION_CODE_MAX_ATTEMPTS', '5', 'COUNT', 'SECURITY', 'Số lần nhập sai mã nhận ngựa', 1, 10);
