-- Thêm khóa ngoại người liên quan cho email_otp, document_requirement và system_config.
-- Chạy sau 001/002, khi backend đang dừng: psql -X -v ON_ERROR_STOP=1 -f ...
-- Idempotent: chạy lại an toàn. Tên cột/constraint trùng với entity để Hibernate
-- ddl-auto=update không tạo thêm bản sao.
BEGIN;
LOCK TABLE email_otp, document_requirement, system_config IN ACCESS EXCLUSIVE MODE;

-- 1) email_otp.userid -> users(userid), xóa user thì OTP xóa theo
ALTER TABLE email_otp ADD COLUMN IF NOT EXISTS userid integer;
UPDATE email_otp o SET userid = u.userid
FROM users u
WHERE o.userid IS NULL AND lower(btrim(u.email)) = o.email;
-- OTP không thuộc tài khoản nào là rác (sống tối đa 10 phút), không giữ lại
DELETE FROM email_otp WHERE userid IS NULL;
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_email_otp_user') THEN
        ALTER TABLE email_otp ADD CONSTRAINT fk_email_otp_user
            FOREIGN KEY (userid) REFERENCES users(userid) ON DELETE CASCADE;
    END IF;
END $$;

-- 2) document_requirement.updated_by_staffid -> staff(userid)
ALTER TABLE document_requirement ADD COLUMN IF NOT EXISTS updated_by_staffid integer;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
               AND table_name = 'document_requirement' AND column_name = 'updated_by') THEN
        -- Username cũ -> staff; giá trị không khớp (vd 'SYSTEM' của seeder) để NULL
        UPDATE document_requirement d SET updated_by_staffid = s.userid
        FROM users u JOIN staff s ON s.userid = u.userid
        WHERE d.updated_by_staffid IS NULL AND u.username = d.updated_by;
        ALTER TABLE document_requirement DROP COLUMN updated_by;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_document_requirement_updated_by_staff') THEN
        ALTER TABLE document_requirement ADD CONSTRAINT fk_document_requirement_updated_by_staff
            FOREIGN KEY (updated_by_staffid) REFERENCES staff(userid);
    END IF;
END $$;

-- 3) system_config.updated_by_staffid -> staff(userid), cùng quy tắc với document_requirement
ALTER TABLE system_config ADD COLUMN IF NOT EXISTS updated_by_staffid integer;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()
               AND table_name = 'system_config' AND column_name = 'updated_by') THEN
        UPDATE system_config c SET updated_by_staffid = s.userid
        FROM users u JOIN staff s ON s.userid = u.userid
        WHERE c.updated_by_staffid IS NULL AND u.username = c.updated_by;
        ALTER TABLE system_config DROP COLUMN updated_by;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_system_config_updated_by_staff') THEN
        ALTER TABLE system_config ADD CONSTRAINT fk_system_config_updated_by_staff
            FOREIGN KEY (updated_by_staffid) REFERENCES staff(userid);
    END IF;
END $$;
COMMIT;
