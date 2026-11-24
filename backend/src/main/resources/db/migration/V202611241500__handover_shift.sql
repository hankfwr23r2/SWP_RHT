-- B3: bàn giao ca (SHIFT) đổi người phụ trách ngựa khi ngựa không đổi chỗ: đổi ca, thay hộ tống giữa đường (DB_SCHEMA thay đổi 24, quy tắc #37, #38)
ALTER TABLE handover DROP CONSTRAINT ck_handover_handover_type;
ALTER TABLE handover ADD CONSTRAINT ck_handover_handover_type
    CHECK (handover_type IN ('PICKUP', 'HUB_IN', 'HUB_OUT', 'STAY_IN', 'STAY_OUT', 'AGENT', 'DELIVERY', 'RETURN', 'SHIFT'));
