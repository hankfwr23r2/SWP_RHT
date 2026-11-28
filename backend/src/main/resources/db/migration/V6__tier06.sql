-- Bậc 6: departure_schedule_day, departure.

CREATE TABLE departure_schedule_day (
    schedule_id bigint   NOT NULL,
    weekday     smallint NOT NULL,
    CONSTRAINT pk_departure_schedule_day PRIMARY KEY (schedule_id, weekday),
    CONSTRAINT fk_departure_schedule_day_schedule_id FOREIGN KEY (schedule_id) REFERENCES departure_schedule (id) ON DELETE CASCADE,
    -- Theo ISO: 1 = thứ Hai, 7 = Chủ nhật
    CONSTRAINT ck_departure_schedule_day_weekday CHECK (weekday BETWEEN 1 AND 7)
);

CREATE TABLE departure (
    id                bigint GENERATED ALWAYS AS IDENTITY,
    schedule_id       bigint,
    route_id          bigint      NOT NULL,
    departure_date    date        NOT NULL,
    planned_depart_at timestamptz NOT NULL,
    booking_cutoff_at timestamptz NOT NULL,
    capacity_stalls   int         NOT NULL,
    -- Dẫn xuất: số khoang đã đặt. Chỉ DepartureService ghi, khi đang giữ SELECT ... FOR UPDATE
    booked_stalls     int         NOT NULL DEFAULT 0,
    status            varchar(30) NOT NULL,
    CONSTRAINT pk_departure PRIMARY KEY (id),
    -- Mỗi lịch sinh tối đa một chuyến mỗi ngày. Chuyến đột xuất có schedule_id NULL nên không bị giới hạn
    CONSTRAINT uk_departure_schedule_date UNIQUE (schedule_id, departure_date),
    CONSTRAINT fk_departure_schedule_id FOREIGN KEY (schedule_id) REFERENCES departure_schedule (id),
    CONSTRAINT fk_departure_route_id FOREIGN KEY (route_id) REFERENCES route (id),
    CONSTRAINT ck_departure_capacity_stalls CHECK (capacity_stalls > 0),
    -- DB chặn bán quá chỗ
    CONSTRAINT ck_departure_booked_stalls CHECK (booked_stalls BETWEEN 0 AND capacity_stalls),
    CONSTRAINT ck_departure_cutoff CHECK (booking_cutoff_at < planned_depart_at),
    CONSTRAINT ck_departure_status CHECK (status IN ('OPEN', 'CLOSED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX ix_departure_route_id ON departure (route_id);
CREATE INDEX ix_departure_status_planned_depart_at ON departure (status, planned_depart_at);
