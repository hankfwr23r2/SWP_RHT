package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.VehicleRuleException;
import org.junit.jupiter.api.Test;

class VehiclePolicyTest {

    @Test
    void acceptsCapacityAtTheLimit() {
        assertThatCode(() -> VehiclePolicy.requireCapacity(2, 1, 3)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyNegativeAndExcessCapacityIncludingOverflow() {
        assertThatThrownBy(() -> VehiclePolicy.requireCapacity(0, 0, 3)).isInstanceOf(VehicleRuleException.class);
        assertThatThrownBy(() -> VehiclePolicy.requireCapacity(-1, 3, 3)).isInstanceOf(VehicleRuleException.class);
        assertThatThrownBy(() -> VehiclePolicy.requireCapacity(2, 2, 3)).isInstanceOf(VehicleRuleException.class);
        assertThatThrownBy(() -> VehiclePolicy.requireCapacity(Integer.MAX_VALUE, 1, Integer.MAX_VALUE))
                .isInstanceOf(VehicleRuleException.class);
    }

    @Test
    void acceptsOnlyHeadquartersOfTypeHub() {
        assertThatCode(() -> VehiclePolicy.requireHeadquarters(LocationType.HUB, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> VehiclePolicy.requireHeadquarters(LocationType.HUB, false))
                .isInstanceOf(VehicleRuleException.class);
        assertThatThrownBy(() -> VehiclePolicy.requireHeadquarters(LocationType.REST_STOP, true))
                .isInstanceOf(VehicleRuleException.class);
    }
}
