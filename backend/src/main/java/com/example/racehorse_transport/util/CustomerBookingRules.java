package com.example.racehorse_transport.util;

import java.util.Set;

/**
 * Khách được làm gì với đơn đã đặt cọc. Thanh toán 70% còn lại là mốc chốt chuyến:
 * chỉ trả được khi xe đã sẵn sàng đón ngựa, và đã trả thì khách không tự hủy được nữa (cần hủy thì gọi hotline).
 * Hủy trước mốc đó thì mất 30% tiền cọc, nên không có khoản nào phải hoàn lại.
 */
public final class CustomerBookingRules {

    private static final Set<String> CANCELLABLE_STATUSES = Set.of(
            "waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup", "en_route_to_pickup");
    private static final Set<String> BALANCE_PAYABLE_STATUSES = Set.of("ready_for_pickup", "en_route_to_pickup");

    private CustomerBookingRules() {
    }

    public static boolean canCancel(String status, boolean balancePaid) {
        return !balancePaid && CANCELLABLE_STATUSES.contains(status);
    }

    public static boolean canPayBalance(String status, boolean balancePaid) {
        return !balancePaid && BALANCE_PAYABLE_STATUSES.contains(status);
    }
}
