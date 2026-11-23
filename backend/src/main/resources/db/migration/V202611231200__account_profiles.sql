-- Nhóm tài khoản và hồ sơ (docs/SUA_TAI_KHOAN_VA_HO_SO.md):
-- mỗi tài khoản một vai trò (app_user.role_code), khách có bảng customer, party chỉ chứa đối tác.
-- Tổng vẫn 59 bảng: thêm customer, bỏ user_role, party_address thành customer_address.

-- Đối tác: party có ít nhất một vai trò đối tác. Bảng tạm, tự xóa khi hết transaction.
CREATE TEMP TABLE tmp_partner ON COMMIT DROP AS
SELECT DISTINCT party_id
FROM party_role
WHERE role_type IN ('DEST_AGENT', 'QUARANTINE_FARM', 'STABLE', 'VET', 'RESCUE', 'INSURER');

-- Hồ sơ không phải đối tác: party của tài khoản, của khách hoặc người nhận
CREATE TEMP TABLE tmp_person ON COMMIT DROP AS
SELECT p.id AS party_id
FROM party p
WHERE (p.user_id IS NOT NULL
       OR EXISTS (SELECT 1 FROM party_role r WHERE r.party_id = p.id AND r.role_type IN ('CUSTOMER', 'CONSIGNEE')))
  AND p.id NOT IN (SELECT party_id FROM tmp_partner);

-- ---------------------------------------------------------------------------
-- 1. Kiểm dữ liệu trước. Sai thì dừng, Flyway hoàn tác toàn bộ file
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    v_ids text;
BEGIN
    SELECT string_agg(u.id::text, ', ' ORDER BY u.id) INTO v_ids
    FROM app_user u
    WHERE (SELECT count(*) FROM user_role r WHERE r.user_id = u.id) <> 1;
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: tài khoản phải có đúng một vai trò, app_user.id: %', v_ids;
    END IF;

    SELECT string_agg(r.user_id::text, ', ' ORDER BY r.user_id) INTO v_ids
    FROM user_role r
    WHERE r.role_code = 'CUSTOMER' AND NOT EXISTS (SELECT 1 FROM party p WHERE p.user_id = r.user_id);
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: tài khoản khách không có party, app_user.id: %', v_ids;
    END IF;

    SELECT string_agg(r.user_id::text, ', ' ORDER BY r.user_id) INTO v_ids
    FROM user_role r
    WHERE r.role_code = 'CUSTOMER' AND EXISTS (SELECT 1 FROM staff s WHERE s.user_id = r.user_id);
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: tài khoản khách có hồ sơ nhân viên, app_user.id: %', v_ids;
    END IF;

    SELECT string_agg(r.user_id::text, ', ' ORDER BY r.user_id) INTO v_ids
    FROM user_role r
    WHERE r.role_code <> 'CUSTOMER' AND NOT EXISTS (SELECT 1 FROM staff s WHERE s.user_id = r.user_id);
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: tài khoản nội bộ không có hồ sơ nhân viên, app_user.id: %', v_ids;
    END IF;

    -- customer không có status: khách bị ngừng ở party sẽ hoạt động lại nếu tài khoản còn ACTIVE
    SELECT string_agg(p.id::text, ', ' ORDER BY p.id) INTO v_ids
    FROM party p
    JOIN user_role r ON r.user_id = p.user_id AND r.role_code = 'CUSTOMER'
    JOIN app_user u ON u.id = p.user_id
    WHERE p.status = 'INACTIVE' AND u.status = 'ACTIVE';
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: khách INACTIVE nhưng tài khoản còn ACTIVE, party.id: %', v_ids;
    END IF;

    SELECT string_agg(b.id::text, ', ' ORDER BY b.id) INTO v_ids
    FROM booking b
    JOIN party p ON p.id = b.customer_party_id
    WHERE NOT EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = p.user_id AND r.role_code = 'CUSTOMER');
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: người đặt đơn không có tài khoản khách, booking.id: %', v_ids;
    END IF;

    SELECT string_agg(h.id::text, ', ' ORDER BY h.id) INTO v_ids
    FROM horse h
    JOIN party p ON p.id = h.owner_party_id
    WHERE NOT EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = p.user_id AND r.role_code = 'CUSTOMER');
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: chủ ngựa không có tài khoản khách, horse.id: %', v_ids;
    END IF;

    SELECT string_agg(a.id::text, ', ' ORDER BY a.id) INTO v_ids
    FROM party_address a
    JOIN party p ON p.id = a.party_id
    WHERE NOT EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = p.user_id AND r.role_code = 'CUSTOMER');
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: địa chỉ không thuộc tài khoản khách, party_address.id: %', v_ids;
    END IF;

    -- Vừa là khách vừa là đối tác: không biết chứng từ thuộc hồ sơ nào
    SELECT string_agg(d.id::text, ', ' ORDER BY d.id) INTO v_ids
    FROM document d
    JOIN party p ON p.id = d.party_id
    JOIN tmp_partner t ON t.party_id = p.id
    WHERE p.user_id IS NOT NULL;
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: chứng từ của bên vừa là khách vừa là đối tác, document.id: %', v_ids;
    END IF;

    -- Chứng từ của khách chuyển sang customer_id thì loại giấy không được là PARTY (quy tắc #11)
    SELECT string_agg(d.id::text, ', ' ORDER BY d.id) INTO v_ids
    FROM document d
    JOIN document_type dt ON dt.id = d.document_type_id
    JOIN party p ON p.id = d.party_id
    WHERE p.user_id IS NOT NULL AND dt.subject = 'PARTY';
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: chứng từ của khách có loại giấy dành cho đối tác, document.id: %', v_ids;
    END IF;

    -- Chứng từ của tài khoản nội bộ không có cột chủ sở hữu tương ứng ở party
    SELECT string_agg(d.id::text, ', ' ORDER BY d.id) INTO v_ids
    FROM document d
    JOIN party p ON p.id = d.party_id
    JOIN user_role r ON r.user_id = p.user_id
    WHERE r.role_code <> 'CUSTOMER';
    IF v_ids IS NOT NULL THEN
        RAISE EXCEPTION 'account_profiles: chứng từ gắn party của tài khoản nội bộ, document.id: %', v_ids;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 2. app_user.role_code
-- ---------------------------------------------------------------------------
ALTER TABLE app_user ADD COLUMN role_code varchar(30);
UPDATE app_user u SET role_code = r.role_code FROM user_role r WHERE r.user_id = u.id;
ALTER TABLE app_user
    ALTER COLUMN role_code SET NOT NULL,
    ADD CONSTRAINT fk_app_user_role_code FOREIGN KEY (role_code) REFERENCES role (code);
CREATE INDEX ix_app_user_role_code ON app_user (role_code);

-- ---------------------------------------------------------------------------
-- 3. customer: hồ sơ khách, khóa chính kế thừa khóa của tài khoản
-- ---------------------------------------------------------------------------
CREATE TABLE customer (
    user_id                        bigint       NOT NULL,
    customer_type                  varchar(30)  NOT NULL,
    full_name                      varchar(200) NOT NULL,
    phone                          varchar(20),
    address                        varchar(500),
    country_code                   char(2)      NOT NULL,
    identity_document_type         varchar(30),
    identity_document_number       varchar(50),
    identity_document_country_code char(2),
    tax_code                       varchar(30),
    CONSTRAINT pk_customer PRIMARY KEY (user_id),
    CONSTRAINT uk_customer_identity UNIQUE (identity_document_type, identity_document_number, identity_document_country_code),
    CONSTRAINT fk_customer_user_id FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_customer_country_code FOREIGN KEY (country_code) REFERENCES country (code),
    CONSTRAINT fk_customer_identity_document_country_code FOREIGN KEY (identity_document_country_code) REFERENCES country (code),
    CONSTRAINT ck_customer_customer_type CHECK (customer_type IN ('PERSON', 'ORGANIZATION')),
    CONSTRAINT ck_customer_identity_document_type CHECK (identity_document_type IN ('CCCD', 'PASSPORT', 'BUSINESS_REG')),
    -- Giấy tờ: khai đủ cả 3 cột hoặc bỏ trống cả 3
    CONSTRAINT ck_customer_identity_complete CHECK (num_nonnulls(identity_document_type, identity_document_number, identity_document_country_code) IN (0, 3))
);
CREATE INDEX ix_customer_country_code ON customer (country_code);
CREATE INDEX ix_customer_identity_document_country_code ON customer (identity_document_country_code);

INSERT INTO customer (user_id, customer_type, full_name, phone, address, country_code,
                      identity_document_type, identity_document_number, identity_document_country_code, tax_code)
SELECT u.id, p.kind, p.display_name, p.phone, p.address, p.country_code, p.id_type, p.id_number, p.id_country, p.tax_code
FROM party p
JOIN app_user u ON u.id = p.user_id
WHERE u.role_code = 'CUSTOMER';

-- ---------------------------------------------------------------------------
-- 4. booking, horse, document trỏ tới customer
-- ---------------------------------------------------------------------------
ALTER TABLE booking ADD COLUMN customer_id bigint;
UPDATE booking b SET customer_id = p.user_id FROM party p WHERE p.id = b.customer_party_id;
ALTER TABLE booking
    ALTER COLUMN customer_id SET NOT NULL,
    ADD CONSTRAINT fk_booking_customer_id FOREIGN KEY (customer_id) REFERENCES customer (user_id),
    DROP COLUMN customer_party_id;
CREATE INDEX ix_booking_customer_id ON booking (customer_id);

ALTER TABLE horse ADD COLUMN owner_customer_id bigint;
UPDATE horse h SET owner_customer_id = p.user_id FROM party p WHERE p.id = h.owner_party_id;
ALTER TABLE horse
    ALTER COLUMN owner_customer_id SET NOT NULL,
    ADD CONSTRAINT fk_horse_owner_customer_id FOREIGN KEY (owner_customer_id) REFERENCES customer (user_id),
    DROP COLUMN owner_party_id;
CREATE INDEX ix_horse_owner_customer_id ON horse (owner_customer_id);

ALTER TABLE document
    DROP CONSTRAINT ck_document_owner,
    ADD COLUMN customer_id bigint;
UPDATE document d SET customer_id = p.user_id, party_id = NULL FROM party p WHERE p.id = d.party_id AND p.user_id IS NOT NULL;
ALTER TABLE document
    ADD CONSTRAINT fk_document_customer_id FOREIGN KEY (customer_id) REFERENCES customer (user_id),
    ADD CONSTRAINT ck_document_owner CHECK (num_nonnulls(horse_id, booking_id, booking_horse_id, horse_leg_id, vehicle_trip_id,
                                                         horse_insurance_id, incident_id, cost_item_id, handover_id,
                                                         vehicle_id, staff_id, party_id, customer_id) = 1);
CREATE INDEX ix_document_customer ON document (customer_id) WHERE customer_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 5. party_address thành customer_address
-- Thêm cột mới thay vì cập nhật tại chỗ: đổi khóa từng dòng có thể đụng unique index địa chỉ mặc định
-- ---------------------------------------------------------------------------
ALTER TABLE party_address RENAME TO customer_address;
ALTER SEQUENCE party_address_id_seq RENAME TO customer_address_id_seq;
ALTER TABLE customer_address ADD COLUMN customer_id bigint;
UPDATE customer_address a SET customer_id = p.user_id FROM party p WHERE p.id = a.party_id;
ALTER TABLE customer_address
    ALTER COLUMN customer_id SET NOT NULL,
    ADD CONSTRAINT fk_customer_address_customer_id FOREIGN KEY (customer_id) REFERENCES customer (user_id) ON DELETE CASCADE,
    DROP COLUMN party_id;
ALTER TABLE customer_address RENAME CONSTRAINT pk_party_address TO pk_customer_address;
ALTER TABLE customer_address RENAME CONSTRAINT fk_party_address_province_code TO fk_customer_address_province_code;
ALTER TABLE customer_address RENAME CONSTRAINT fk_party_address_country_code TO fk_customer_address_country_code;
ALTER TABLE customer_address RENAME CONSTRAINT ck_party_address_coordinates TO ck_customer_address_coordinates;
ALTER INDEX ix_party_address_province_code RENAME TO ix_customer_address_province_code;
ALTER INDEX ix_party_address_country_code RENAME TO ix_customer_address_country_code;
CREATE UNIQUE INDEX uk_customer_address_default ON customer_address (customer_id) WHERE is_default;
CREATE INDEX ix_customer_address_customer_id ON customer_address (customer_id);

-- ---------------------------------------------------------------------------
-- 6. booking_address: FK tự theo bảng đã đổi tên, chỉ đổi tên cột, ràng buộc và index
-- ---------------------------------------------------------------------------
ALTER TABLE booking_address RENAME COLUMN source_address_id TO source_customer_address_id;
ALTER TABLE booking_address RENAME CONSTRAINT fk_booking_address_source_address_id TO fk_booking_address_source_customer_address_id;
ALTER INDEX ix_booking_address_source_address_id RENAME TO ix_booking_address_source_customer_address_id;

-- ---------------------------------------------------------------------------
-- 7. booking_party chỉ là bản chụp
-- ---------------------------------------------------------------------------
ALTER TABLE booking_party DROP COLUMN party_id;

-- ---------------------------------------------------------------------------
-- 8. handover: from/to chỉ là đối tác, NULL là công ty hoặc bên trong đơn.
-- CHECK cũ (IS DISTINCT FROM) từ chối nhầm mọi dòng có cả hai cột NULL
-- ---------------------------------------------------------------------------
ALTER TABLE handover DROP CONSTRAINT ck_handover_parties;
UPDATE handover SET from_party_id = NULL WHERE from_party_id IN (SELECT party_id FROM tmp_person);
UPDATE handover SET to_party_id = NULL WHERE to_party_id IN (SELECT party_id FROM tmp_person);
ALTER TABLE handover
    ADD CONSTRAINT ck_handover_parties CHECK (from_party_id IS NULL OR to_party_id IS NULL OR from_party_id <> to_party_id);

-- ---------------------------------------------------------------------------
-- 9. party_role chỉ còn vai trò đối tác. Chỉ xóa party từng là khách, người nhận hoặc của tài khoản;
--    đối tác chưa gán vai trò thì giữ lại
-- ---------------------------------------------------------------------------
DELETE FROM party_role WHERE role_type IN ('CUSTOMER', 'CONSIGNEE');
DELETE FROM party p
WHERE p.id IN (SELECT party_id FROM tmp_person)
  AND NOT EXISTS (SELECT 1 FROM horse_leg x WHERE x.agent_party_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM horse_stay x WHERE x.operator_party_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM horse_insurance x WHERE x.insurer_party_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM incident_plan x WHERE x.handler_party_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM document x WHERE x.party_id = p.id);
ALTER TABLE party_role
    DROP CONSTRAINT ck_party_role_role_type,
    ADD CONSTRAINT ck_party_role_role_type CHECK (role_type IN ('DEST_AGENT', 'QUARANTINE_FARM', 'STABLE', 'VET', 'RESCUE', 'INSURER'));

-- ---------------------------------------------------------------------------
-- 10. Bỏ cột của party (ràng buộc và index của cột tự mất theo), bỏ user_role, thêm chủ sở hữu CUSTOMER
-- ---------------------------------------------------------------------------
ALTER TABLE party
    DROP COLUMN user_id,
    DROP COLUMN id_type,
    DROP COLUMN id_number,
    DROP COLUMN id_country;
DROP TABLE user_role;
ALTER TABLE document_type
    DROP CONSTRAINT ck_document_type_subject,
    ADD CONSTRAINT ck_document_type_subject CHECK (subject IN ('HORSE', 'BOOKING', 'BOOKING_HORSE', 'HORSE_LEG', 'VEHICLE_TRIP', 'INSURANCE',
                                                               'INCIDENT', 'COST_ITEM', 'HANDOVER', 'VEHICLE', 'STAFF', 'PARTY', 'CUSTOMER'));
