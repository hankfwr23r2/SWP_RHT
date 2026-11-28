package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.AccountRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StaffAccountPolicyTest {

    @Test
    void permitsHeadquartersAndCompleteDriverLicenseOnly() {
        var date = LocalDate.of(2030, 1, 1);
        assertThatCode(() -> StaffAccountPolicy.requireHeadquarters(LocationType.HUB, true)).doesNotThrowAnyException();
        assertThatCode(() -> StaffAccountPolicy.requireValidLicense("DRIVER", "C", date)).doesNotThrowAnyException();
        assertThatThrownBy(() -> StaffAccountPolicy.requireValidLicense("MANAGER", "C", date)).isInstanceOf(AccountRuleException.class);
        assertThatThrownBy(() -> StaffAccountPolicy.requireValidLicense("DRIVER", "C", null)).isInstanceOf(AccountRuleException.class);
        assertThatThrownBy(() -> StaffAccountPolicy.requireHeadquarters(LocationType.HUB, false)).isInstanceOf(AccountRuleException.class);
        assertThatThrownBy(() -> StaffAccountPolicy.requireStaffRole("CUSTOMER")).isInstanceOf(AccountRuleException.class);
    }

    @Test
    void checksBcryptByteLimitForUnicode() {
        assertThatCode(() -> StaffAccountPolicy.requirePasswordFitsBcrypt("ấ".repeat(24))).doesNotThrowAnyException();
        assertThatThrownBy(() -> StaffAccountPolicy.requirePasswordFitsBcrypt("ấ".repeat(25))).isInstanceOf(AccountRuleException.class);
    }
}
