package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.time.Duration;
import java.time.Instant;

public final class DeliveryComplaintPolicy {

    private DeliveryComplaintPolicy() {
    }

    public static boolean canCustomerSubmit(BookingStatus status, Instant deliveredAt, Instant now,
                                            Duration customerWindow, boolean responseRecorded) {
        return status == BookingStatus.DELIVERED
                && !responseRecorded
                && deliveredAt != null
                && !now.isBefore(deliveredAt)
                && !now.isAfter(deliveredAt.plus(customerWindow));
    }

    public static boolean canManagerSubmit(BookingStatus status, Instant deliveredAt, Instant now,
                                           Duration managerWindow, boolean complaintExists) {
        return (status == BookingStatus.DELIVERED || status == BookingStatus.COMPLETED)
                && !complaintExists
                && deliveredAt != null
                && !now.isBefore(deliveredAt)
                && !now.isAfter(deliveredAt.plus(managerWindow));
    }

    public static boolean canClose(BookingStatus status, Instant deliveredAt, Instant now,
                                   Duration customerWindow, boolean customerConfirmed, boolean complaintOpen) {
        return status == BookingStatus.DELIVERED
                && deliveredAt != null
                && !now.isBefore(deliveredAt)
                && !complaintOpen
                && (customerConfirmed || now.isAfter(deliveredAt.plus(customerWindow)));
    }
}
