package com.example.racehorse_transport.policy;

import static com.example.racehorse_transport.entity.enums.BookingStatus.COMPLETED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.DELIVERED;
import static com.example.racehorse_transport.entity.enums.BookingStatus.IN_OPERATION;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryComplaintPolicyTest {

    private static final Instant DELIVERED_AT = Instant.parse("2026-11-25T08:00:00Z");
    private static final Duration CUSTOMER_WINDOW = Duration.ofDays(7);
    private static final Duration MANAGER_WINDOW = Duration.ofDays(14);
    private static final Instant CUSTOMER_DEADLINE = DELIVERED_AT.plus(CUSTOMER_WINDOW);
    private static final Instant MANAGER_DEADLINE = DELIVERED_AT.plus(MANAGER_WINDOW);

    @Test
    void customerWindowIncludesDeliveryAndTheExactDeadline() {
        assertThat(DeliveryComplaintPolicy.canCustomerSubmit(DELIVERED, DELIVERED_AT, DELIVERED_AT,
                CUSTOMER_WINDOW, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canCustomerSubmit(DELIVERED, DELIVERED_AT, CUSTOMER_DEADLINE,
                CUSTOMER_WINDOW, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canCustomerSubmit(DELIVERED, DELIVERED_AT, CUSTOMER_DEADLINE.plusSeconds(1),
                CUSTOMER_WINDOW, false)).isFalse();
        assertThat(DeliveryComplaintPolicy.canCustomerSubmit(DELIVERED, DELIVERED_AT, DELIVERED_AT.minusSeconds(1),
                CUSTOMER_WINDOW, false)).isFalse();
    }

    @Test
    void customerCannotSubmitAgainOrSubmitForAnUndeliveredOrder() {
        assertThat(DeliveryComplaintPolicy.canCustomerSubmit(DELIVERED, DELIVERED_AT, DELIVERED_AT,
                CUSTOMER_WINDOW, true)).isFalse();
        assertThat(DeliveryComplaintPolicy.canCustomerSubmit(IN_OPERATION, DELIVERED_AT, DELIVERED_AT,
                CUSTOMER_WINDOW, false)).isFalse();
        assertThat(DeliveryComplaintPolicy.canCustomerSubmit(DELIVERED, null, DELIVERED_AT,
                CUSTOMER_WINDOW, false)).isFalse();
    }

    @Test
    void managerCanSubmitFromDeliveryThroughDayFourteenIncludingForCompletedOrders() {
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(DELIVERED, DELIVERED_AT, DELIVERED_AT,
                MANAGER_WINDOW, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(DELIVERED, DELIVERED_AT, DELIVERED_AT.plus(Duration.ofDays(5)),
                MANAGER_WINDOW, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(DELIVERED, DELIVERED_AT, CUSTOMER_DEADLINE,
                MANAGER_WINDOW, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(DELIVERED, DELIVERED_AT, CUSTOMER_DEADLINE.plusSeconds(1),
                MANAGER_WINDOW, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(COMPLETED, DELIVERED_AT, MANAGER_DEADLINE,
                MANAGER_WINDOW, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(COMPLETED, DELIVERED_AT, MANAGER_DEADLINE.plusSeconds(1),
                MANAGER_WINDOW, false)).isFalse();
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(COMPLETED, DELIVERED_AT, MANAGER_DEADLINE,
                MANAGER_WINDOW, true)).isFalse();
    }

    @Test
    void managerCannotSubmitBeforeDeliveryOrForAnUndeliveredOrder() {
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(DELIVERED, DELIVERED_AT, DELIVERED_AT.minusSeconds(1),
                MANAGER_WINDOW, false)).isFalse();
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(IN_OPERATION, DELIVERED_AT, DELIVERED_AT,
                MANAGER_WINDOW, false)).isFalse();
        assertThat(DeliveryComplaintPolicy.canManagerSubmit(DELIVERED, null, DELIVERED_AT,
                MANAGER_WINDOW, false)).isFalse();
    }

    @Test
    void confirmationAllowsEarlyClosureAndAnOpenComplaintAlwaysBlocksIt() {
        Instant dayOne = DELIVERED_AT.plus(Duration.ofDays(1));
        assertThat(DeliveryComplaintPolicy.canClose(DELIVERED, DELIVERED_AT, dayOne,
                CUSTOMER_WINDOW, true, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canClose(DELIVERED, DELIVERED_AT, dayOne,
                CUSTOMER_WINDOW, false, false)).isFalse();
        assertThat(DeliveryComplaintPolicy.canClose(DELIVERED, DELIVERED_AT, CUSTOMER_DEADLINE,
                CUSTOMER_WINDOW, false, false)).isFalse();
        assertThat(DeliveryComplaintPolicy.canClose(DELIVERED, DELIVERED_AT, CUSTOMER_DEADLINE.plusSeconds(1),
                CUSTOMER_WINDOW, false, false)).isTrue();
        assertThat(DeliveryComplaintPolicy.canClose(DELIVERED, DELIVERED_AT, CUSTOMER_DEADLINE.plusSeconds(1),
                CUSTOMER_WINDOW, false, true)).isFalse();
    }
}
