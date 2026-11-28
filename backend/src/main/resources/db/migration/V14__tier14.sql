-- Bậc 14: handover_item (cần document).

-- Giấy tờ gốc nhận hoặc trả trong lần giao nhận, ví dụ hộ chiếu ngựa bản gốc
CREATE TABLE handover_item (
    handover_id bigint      NOT NULL,
    document_id bigint      NOT NULL,
    direction   varchar(30) NOT NULL,
    CONSTRAINT pk_handover_item PRIMARY KEY (handover_id, document_id),
    CONSTRAINT fk_handover_item_handover_id FOREIGN KEY (handover_id) REFERENCES handover (id) ON DELETE CASCADE,
    CONSTRAINT fk_handover_item_document_id FOREIGN KEY (document_id) REFERENCES document (id),
    CONSTRAINT ck_handover_item_direction CHECK (direction IN ('RECEIVED', 'RETURNED'))
);
CREATE INDEX ix_handover_item_document_id ON handover_item (document_id);
