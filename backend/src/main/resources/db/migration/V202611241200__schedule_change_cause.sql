ALTER TABLE booking_status_history
    ADD COLUMN cause_type varchar(30),
    ADD COLUMN previous_departure_id bigint REFERENCES departure(id),
    ADD CONSTRAINT ck_booking_status_history_cause_type
        CHECK (cause_type IN ('CUSTOMER', 'COMPANY', 'AUTHORITY'));

CREATE INDEX idx_booking_status_history_previous_departure ON booking_status_history(previous_departure_id);

ALTER TABLE quotation_line ADD COLUMN refund_payment_id bigint REFERENCES payment(id);
CREATE INDEX idx_quotation_line_refund_payment ON quotation_line(refund_payment_id);

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('AUTHORITY_DELAY_REFUND_MISSES', '2', 'COUNT', 'PAYMENT',
        'Số lần lỡ chuyến do cơ quan cấp giấy để được hoàn cọc vận chuyển', 1, 10);

INSERT INTO document_type (code, name, owner_type)
VALUES ('REFUND_RECEIPT', 'Chứng từ chuyển khoản hoàn tiền', 'BOOKING');
