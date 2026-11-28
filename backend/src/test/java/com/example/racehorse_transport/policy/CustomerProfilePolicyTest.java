package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.IdentityDocumentType;
import com.example.racehorse_transport.exception.CustomerRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CustomerProfilePolicyTest {

    @Test
    void acceptsCompleteOrAbsentIdentityAndRejectsPartialIdentity() {
        assertThatCode(() -> CustomerProfilePolicy.requireCompleteIdentity(null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> CustomerProfilePolicy.requireCompleteIdentity(IdentityDocumentType.PASSPORT, "P1", "VN"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> CustomerProfilePolicy.requireCompleteIdentity(IdentityDocumentType.PASSPORT, " ", "VN"))
                .isInstanceOf(CustomerRuleException.class);
    }

    @Test
    void requiresMatchingProvinceCountryAndPairedCoordinates() {
        assertThatCode(() -> CustomerProfilePolicy.requireAddressCountry("VN", "VN")).doesNotThrowAnyException();
        assertThatCode(() -> CustomerProfilePolicy.requireCoordinatesTogether(null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> CustomerProfilePolicy.requireAddressCountry("KH", "VN")).isInstanceOf(CustomerRuleException.class);
        assertThatThrownBy(() -> CustomerProfilePolicy.requireCoordinatesTogether(BigDecimal.ONE, null))
                .isInstanceOf(CustomerRuleException.class);
    }

    @Test
    void rejectsPointFarFromProvinceCentre() {
        BigDecimal lat = new BigDecimal("10.776");
        BigDecimal lng = new BigDecimal("106.700");
        assertThatCode(() -> CustomerProfilePolicy.requirePointNearProvince(null, null, lat, lng, 200)).doesNotThrowAnyException();
        assertThatCode(() -> CustomerProfilePolicy.requirePointNearProvince(new BigDecimal("11.33"), new BigDecimal("106.37"), lat, lng, 200))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> CustomerProfilePolicy.requirePointNearProvince(new BigDecimal("15.5"), new BigDecimal("106.5"), lat, lng, 200))
                .isInstanceOf(CustomerRuleException.class).hasMessage("Vị trí ghim cách tỉnh đã chọn quá 200 km.");
        // Hai điểm cùng kinh độ cách nhau khoảng 55,6 km
        assertThatCode(() -> CustomerProfilePolicy.requirePointNearProvince(new BigDecimal("11.276"), lng, lat, lng, 56)).doesNotThrowAnyException();
        assertThatThrownBy(() -> CustomerProfilePolicy.requirePointNearProvince(new BigDecimal("11.276"), lng, lat, lng, 55))
                .isInstanceOf(CustomerRuleException.class);
    }
}
