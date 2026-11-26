-- Công nợ khoản thu thêm (PHUONG_AN_6_LUONG mục 13.3, 13.5)
ALTER TABLE payment
    ADD COLUMN issued_at timestamptz,
    ADD COLUMN extended_at timestamptz,
    ADD COLUMN disputed_at timestamptz,
    ADD COLUMN dispute_reason varchar(500),
    ADD COLUMN dispute_resolved_at timestamptz,
    ADD COLUMN dispute_decision varchar(20),
    ADD COLUMN dispute_response varchar(500),
    ADD COLUMN written_off_at timestamptz,
    ADD COLUMN written_off_by bigint REFERENCES app_user (id),
    ADD COLUMN write_off_reason varchar(500),
    ADD COLUMN block_released_at timestamptz,
    ADD CONSTRAINT ck_payment_debt_type CHECK (payment_type = 'EXTRA_CHARGE' OR
        (extended_at IS NULL AND disputed_at IS NULL AND written_off_at IS NULL)),
    ADD CONSTRAINT ck_payment_dispute CHECK ((disputed_at IS NULL) = (dispute_reason IS NULL)
        AND (dispute_resolved_at IS NULL) = (dispute_decision IS NULL)
        AND (dispute_resolved_at IS NULL OR disputed_at IS NOT NULL)
        AND (dispute_decision IS NULL OR dispute_decision IN ('ACCEPTED', 'REJECTED'))),
    ADD CONSTRAINT ck_payment_write_off CHECK (written_off_at IS NULL OR
        (status = 'CANCELLED' AND written_off_by IS NOT NULL AND write_off_reason IS NOT NULL));
CREATE INDEX ix_payment_written_off_by ON payment (written_off_by);

UPDATE payment SET issued_at = due_at - interval '7 days' WHERE payment_type = 'EXTRA_CHARGE' AND due_at IS NOT NULL;

INSERT INTO document_type (code, name, owner_type) VALUES ('DEBT_DECISION', 'Quyết định xóa nợ', 'BOOKING');

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('DEBT_CALL_DAYS', '14', 'DAY', 'PAYMENT', 'Quản lý gọi khách nợ sau số ngày từ lúc lập khoản thu', 8, 60),
       ('DEBT_BAD_DAYS', '30', 'DAY', 'PAYMENT', 'Sang nợ khó thu sau số ngày từ lúc lập khoản thu', 15, 365),
       ('DEBT_EXTENSION_MAX_DAYS', '14', 'DAY', 'PAYMENT', 'Số ngày gia hạn tối đa, một lần mỗi khoản', 1, 60),
       ('DEBT_BLOCK_MIN_AMOUNT', '200000', 'VND', 'PAYMENT', 'Nợ quá hạn từ mức này thì chưa đặt đơn mới được', 1, 100000000);
