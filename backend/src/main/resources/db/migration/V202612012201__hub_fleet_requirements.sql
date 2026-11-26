ALTER TABLE route ADD COLUMN fleet_outbound boolean NOT NULL DEFAULT false;

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('CREW_PER_VEHICLE_PERCENT', '120', 'PERCENT', 'SCHEDULE', 'Tỷ lệ nhân sự mỗi vai trò trên số xe chạy', 100, 1000),
       ('SMALL_BRANCH_VEHICLES_PER_HUB', '1', 'COUNT', 'SCHEDULE', 'Số xe nhánh 2 chỗ mỗi hub', 0, 100),
       ('MIN_BRANCH_SPARE_PER_HUB', '1', 'COUNT', 'SCHEDULE', 'Số xe dự phòng nhánh tối thiểu mỗi hub', 0, 100),
       ('BRANCH_SHARE_PERCENT', '50', 'PERCENT', 'SCHEDULE', 'Tỷ lệ ngựa do công ty đón, giao', 0, 100),
       ('BRANCH_VEHICLE_STALLS', '6', 'COUNT', 'SCHEDULE', 'Số chỗ của xe nhánh', 1, 100),
       ('SPARE_HUB_MIN_PAIRS', '2', 'COUNT', 'SCHEDULE', 'Số cặp tuyến tối thiểu để hub giữ xe để sẵn', 1, 100);
