-- B3: ghi uống nước và ăn của ngựa trong lần dừng hoặc lưu trú (docs/MAN_HINH_VA_TAC_DONG.md mục 8, quyết định số 5)
ALTER TABLE horse_health_log
    ADD COLUMN water_intake varchar(30),
    ADD COLUMN feed_intake  varchar(30),
    ADD CONSTRAINT ck_horse_health_log_water_intake CHECK (water_intake IN ('NORMAL', 'REDUCED', 'REFUSED')),
    ADD CONSTRAINT ck_horse_health_log_feed_intake CHECK (feed_intake IN ('NORMAL', 'REDUCED', 'REFUSED'));
