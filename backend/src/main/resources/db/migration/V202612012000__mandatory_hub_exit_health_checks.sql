ALTER TABLE health_check_task DROP CONSTRAINT health_check_task_check_type_check;
ALTER TABLE health_check_task ADD CONSTRAINT health_check_task_check_type_check
    CHECK (check_type IN ('ROUTINE', 'FOLLOW_UP', 'BEFORE_DEPARTURE', 'BEFORE_DELIVERY'));

INSERT INTO system_config (config_key, config_value, unit, config_group, description)
VALUES ('PRE_EXIT_CHECK_HOURS', '12', 'HOUR', 'HANDOVER', 'Tạo việc khám trước giờ dự kiến rời hub');
