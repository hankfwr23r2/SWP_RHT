package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.IdentityDocumentType;
import com.example.racehorse_transport.exception.CustomerRuleException;
import java.math.BigDecimal;

public final class CustomerProfilePolicy {

    private CustomerProfilePolicy() {}

    public static void requireCompleteIdentity(IdentityDocumentType type, String number, String countryCode) {
        if (type == null && number == null && countryCode == null) {
            return;
        }
        if (type == null || number == null || number.isBlank() || countryCode == null || countryCode.isBlank()) {
            throw new CustomerRuleException("Vui lòng nhập đủ loại, số và quốc gia cấp giấy tờ.");
        }
    }

    public static void requireAddressCountry(String countryCode, String provinceCountryCode) {
        if (!countryCode.equals(provinceCountryCode)) {
            throw new CustomerRuleException("Tỉnh không thuộc quốc gia đã chọn.");
        }
    }

    public static void requireCoordinatesTogether(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new CustomerRuleException("Cần nhập cả vĩ độ và kinh độ.");
        }
    }
}
