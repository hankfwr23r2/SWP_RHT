-- B1: vị trí khoang của con ngựa trên lượt xe (DB_SCHEMA 9.4, quy tắc #39, #40). Đánh số từ đầu xe ra cửa sau, khoang rộng
-- chiếm vị trí này và vị trí kế sau (vách di động). NULL = chưa xếp hoặc chặng do đại lý chở
ALTER TABLE horse_leg
    ADD COLUMN stall_position smallint,
    ADD CONSTRAINT ck_horse_leg_stall_position CHECK (stall_position IS NULL OR stall_position >= 1),
    ADD CONSTRAINT ck_horse_leg_agent_stall_position CHECK (agent_party_id IS NULL OR stall_position IS NULL);
