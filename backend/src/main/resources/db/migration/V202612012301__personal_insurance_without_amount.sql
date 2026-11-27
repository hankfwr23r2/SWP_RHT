-- Bảo hiểm khách tự có chỉ cần tệp hợp đồng và ngày hiệu lực; số tiền chỉ bắt buộc với bảo hiểm nhờ mua hộ.
ALTER TABLE horse_insurance
    DROP CONSTRAINT ck_horse_insurance_coverage,
    ADD CONSTRAINT ck_horse_insurance_coverage
        CHECK (status <> 'CONFIRMED' OR coverage_option <> 'ARRANGED'
            OR (coverage_amount IS NOT NULL AND currency_code IS NOT NULL));
