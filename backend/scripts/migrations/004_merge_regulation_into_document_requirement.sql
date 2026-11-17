-- Gộp bảng REGULATION vào DOCUMENT_REQUIREMENT (cửa khẩu = cột locationid, FK tới location).
-- Chạy sau 003, khi backend đang dừng: psql -X -v ON_ERROR_STOP=1 -f ...
-- Idempotent: chạy lại sau khi đã bỏ bảng regulation thì chỉ đảm bảo cột/FK tồn tại.
BEGIN;

ALTER TABLE document_requirement ADD COLUMN IF NOT EXISTS locationid integer;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_document_requirement_location') THEN
        ALTER TABLE document_requirement ADD CONSTRAINT fk_document_requirement_location
            FOREIGN KEY (locationid) REFERENCES location(locationid);
    END IF;
END $$;

DO $$ BEGIN
    IF to_regclass('regulation') IS NULL THEN
        RETURN;
    END IF;
    LOCK TABLE regulation, document_requirement IN ACCESS EXCLUSIVE MODE;

    -- Quy định không có loại giấy thì không ánh xạ được: dừng để xử lý tay thay vì làm mất dữ liệu
    IF EXISTS (SELECT 1 FROM regulation WHERE document_type IS NULL) THEN
        RAISE EXCEPTION 'regulation có document_type NULL; cần phân loại thủ công trước khi gộp';
    END IF;

    -- Quy định theo cửa khẩu -> requirement riêng của cửa khẩu đó (giữ tên, mô tả, trạng thái, người tạo)
    INSERT INTO document_requirement(document_group, document_type, document_name, description, trip_type,
            country, locationid, required, status, updated_by_staffid, updated_at)
    SELECT 'CLEARANCE', r.document_type, r.regulation_name, left(r.description, 1000), 'INTERNATIONAL',
           CASE upper(btrim(coalesce(nullif(btrim(l.destination_country), ''), r.country, 'ALL')))
                WHEN 'LÀO' THEN 'LA' WHEN 'LAO' THEN 'LA' WHEN 'LAOS' THEN 'LA'
                WHEN 'CAMPUCHIA' THEN 'KH' WHEN 'CAMBODIA' THEN 'KH'
                WHEN 'VIỆT NAM' THEN 'VN' WHEN 'VIETNAM' THEN 'VN'
                ELSE upper(btrim(coalesce(nullif(btrim(l.destination_country), ''), r.country, 'ALL'))) END,
           r.locationid, true, r.status, r.staffid, now()
    FROM regulation r JOIN location l ON l.locationid = r.locationid
    WHERE r.document_type IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM document_requirement d
                      WHERE d.document_group = 'CLEARANCE' AND d.document_type = r.document_type
                        AND d.locationid = r.locationid);

    -- Quy định chung chưa có requirement chung tương ứng -> tạo dòng áp dụng mọi chuyến
    INSERT INTO document_requirement(document_group, document_type, document_name, description, trip_type,
            country, locationid, required, status, updated_by_staffid, updated_at)
    SELECT DISTINCT ON (r.document_type) 'CLEARANCE', r.document_type, r.regulation_name, left(r.description, 1000),
           'ALL', 'ALL', NULL, true, r.status, r.staffid, now()
    FROM regulation r
    WHERE r.locationid IS NULL AND r.document_type IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM document_requirement d
                      WHERE d.document_group = 'CLEARANCE' AND d.document_type = r.document_type
                        AND d.locationid IS NULL)
    ORDER BY r.document_type, r.regulationid;

    IF to_regclass('trip_document') IS NOT NULL THEN
        ALTER TABLE trip_document DROP COLUMN IF EXISTS regulationid;
    END IF;
    DROP TABLE regulation;
END $$;

COMMIT;
