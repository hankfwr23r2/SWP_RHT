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
}
