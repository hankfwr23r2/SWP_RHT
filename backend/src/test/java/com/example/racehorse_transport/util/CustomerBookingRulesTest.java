package com.example.racehorse_transport.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// Khách chỉ tự hủy được từ lúc có vận đơn tới khi xe đang chạy tới điểm đón, và không hủy được nữa sau khi đã trả 70%.
// Trả 70% chỉ mở khi xe đang chạy tới điểm đón (xe tới nơi, kíp nhận xong ngựa mới cho trả, phần đó kiểm ở BookingService).
class CustomerBookingRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup", "en_route_to_pickup"})
    void customerCanCancelAfterDepositUntilTheBalanceIsPaid(String status) {
        assertThat(CustomerBookingRules.canCancel(status, false, false)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending_intake", "under_review", "resubmission_requested", "pending_commercial"})
    void customerCanWithdrawForFreeBeforeTheDeposit(String status) {
        assertThat(CustomerBookingRules.canCancel(status, false, false)).isTrue();
        assertThat(CustomerBookingRules.isBeforeDeposit(status)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void customerCannotCancelOnceTheCrewHasTheHorses() {
        assertThat(CustomerBookingRules.canCancel("en_route_to_pickup", false, true)).isFalse();
        assertThat(CustomerBookingRules.canCancel("en_route_to_pickup", false, false)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"waybill_issued", "clearance_done", "ready_for_pickup", "en_route_to_pickup"})
    void customerCannotCancelOnceTheBalanceIsPaid(String status) {
        assertThat(CustomerBookingRules.canCancel(status, true, false)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"awaiting_payment", "quote_expired",
            "in_transit", "arrived", "completed", "cancelled", "rejected"})
    void customerCannotCancelWhileAQuoteIsOpenOrAfterTheTruckHasLeft(String status) {
        assertThat(CustomerBookingRules.canCancel(status, false, false)).isFalse();
    }

    @Test
    void balanceIsOnlyPayableWhileTheTruckIsOnItsWayToPickup() {
        assertThat(CustomerBookingRules.canPayBalance("en_route_to_pickup", false)).isTrue();
        assertThat(CustomerBookingRules.canPayBalance("en_route_to_pickup", true)).isFalse();
        for (String status : new String[]{"waybill_issued", "clearance_done", "ready_for_pickup", "in_transit", "awaiting_payment", "completed"}) {
            assertThat(CustomerBookingRules.canPayBalance(status, false)).as(status).isFalse();
        }
    }

    @Test
    void unknownOrMissingStatusIsNeverAllowed() {
        assertThat(CustomerBookingRules.canCancel(null, false, false)).isFalse();
        assertThat(CustomerBookingRules.canPayBalance(null, false)).isFalse();
        assertThat(CustomerBookingRules.canCancel("EN_ROUTE_TO_PICKUP", false, false)).isFalse();
    }
}
