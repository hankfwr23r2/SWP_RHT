INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('COST_APPROVAL_REMINDER_1_DAYS', '3', 'DAY', 'PAYMENT', 'Nhắc duyệt chi phí lần 1 sau khi giao', 1, 30),
       ('COST_APPROVAL_REMINDER_2_DAYS', '7', 'DAY', 'PAYMENT', 'Nhắc duyệt chi phí lần 2 sau khi giao', 1, 30),
       ('COST_APPROVAL_DAYS', '14', 'DAY', 'PAYMENT', 'Quá hạn duyệt chi phí sau khi giao thì công ty chịu', 1, 60),
       ('LATE_INVOICE_DAYS', '30', 'DAY', 'PAYMENT', 'Chỉ thu khách chi phí lập trong số ngày sau khi giao', 1, 90),
       ('INCIDENT_AUTO_CLOSE_DAYS', '7', 'DAY', 'INCIDENT', 'Tự đóng sự cố đã xử lý sau số ngày', 1, 60),
       ('REFUND_DUE_DAYS', '7', 'DAY', 'PAYMENT', 'Hạn công ty chuyển khoản hoàn', 1, 60);

UPDATE payment SET due_at = CURRENT_TIMESTAMP + interval '7 days' WHERE payment_type = 'REFUND' AND status = 'PENDING' AND due_at IS NULL;
