ALTER TABLE incident
    ADD COLUMN rest_started_at timestamptz;

INSERT INTO document_type (code, name, owner_type)
VALUES ('FIT_CHECK_PHOTO', 'Ảnh xác nhận đủ sức', 'INCIDENT');
