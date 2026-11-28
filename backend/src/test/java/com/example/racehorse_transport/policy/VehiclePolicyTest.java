package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.exception.VehicleRuleException;
import org.junit.jupiter.api.Test;

class VehiclePolicyTest {

    @Test
    void acceptsCapacityAtTheLimit() {
        assertThatCode(() -> VehiclePolicy.requireCapacity(2, 1, 4)).doesNotThrowAnyException();
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
    void requiresHomeLocationToBeAHub() {
        assertThatCode(() -> VehiclePolicy.requireHomeHub(LocationType.HUB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> VehiclePolicy.requireHomeHub(LocationType.BORDER_GATE))
                .isInstanceOf(VehicleRuleException.class);
        assertThatThrownBy(() -> VehiclePolicy.requireHomeHub(LocationType.REST_STOP))
                .isInstanceOf(VehicleRuleException.class);
    }

    @Test
    void allowsMaintenanceReactivationAndPermanentRetirement() {
        assertThatCode(() -> VehiclePolicy.requireStatusTransition(VehicleStatus.ACTIVE, VehicleStatus.MAINTENANCE))
                .doesNotThrowAnyException();
        assertThatCode(() -> VehiclePolicy.requireStatusTransition(VehicleStatus.MAINTENANCE, VehicleStatus.ACTIVE))
                .doesNotThrowAnyException();
        assertThatCode(() -> VehiclePolicy.requireStatusTransition(VehicleStatus.ACTIVE, VehicleStatus.RETIRED))
                .doesNotThrowAnyException();
        assertThatCode(() -> VehiclePolicy.requireStatusTransition(VehicleStatus.MAINTENANCE, VehicleStatus.RETIRED))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> VehiclePolicy.requireStatusTransition(VehicleStatus.RETIRED, VehicleStatus.ACTIVE))
                .isInstanceOf(VehicleRuleException.class);
    }
}
