ALTER TABLE booking_horse
    ADD COLUMN pickup_retry_departure_id bigint REFERENCES departure(id);

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('PICKUP_RETRY_PREPARATION_HOURS', '2', 'HOUR', 'HANDOVER', 'Thời gian chuẩn bị lượt đón lại', 0, 12);

INSERT INTO document_type (code, name, owner_type)
VALUES ('PICKUP_MISS_PHOTO', 'Ảnh đón hụt', 'BOOKING_HORSE');

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('PICKUP_MISS_WAIT_MINUTES', '30', 'MINUTE', 'HANDOVER', 'Thời gian chờ khi người gửi vắng hoặc ngựa chưa lên xe', 1, 240);
