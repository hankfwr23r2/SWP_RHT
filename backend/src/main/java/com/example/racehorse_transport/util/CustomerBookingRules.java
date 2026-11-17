package com.example.racehorse_transport.util;

import java.util.Set;

/**
 * Khách được làm gì với đơn của mình.
 * Trước khi đặt cọc: rút yêu cầu miễn phí (trừ bước chờ cọc, ở đó khách dùng "Từ chối báo giá").
 * Sau khi đặt cọc: hủy thì mất phần cọc vận chuyển, cho tới khi kíp xe nhận xong ngựa ở điểm đón
 * (chip khớp, bản gốc, biên bản; xem BookingService.isPickupComplete). Từ lúc đó ngựa đã trên xe:
 * khách không tự hủy được, chỉ Quản lý hủy chuyến (kèm bước trả ngựa). Thanh toán 70% còn lại cũng
 * chỉ mở sau mốc nhận ngựa; đã trả đủ thì khách không tự hủy được nữa.
 */
public final class CustomerBookingRules {

    // Chưa đặt cọc: rút yêu cầu không mất phí
    private static final Set<String> FREE_WITHDRAW_STATUSES = Set.of(
            "pending_intake", "under_review", "resubmission_requested", "pending_commercial");
    // Đã đặt cọc, kíp xe chưa nhận ngựa: hủy thì mất phần cọc vận chuyển
    private static final Set<String> CANCELLABLE_STATUSES = Set.of(
            "waybill_issued", "clearance_in_progress", "clearance_done", "ready_for_pickup", "en_route_to_pickup");
    private static final Set<String> BALANCE_PAYABLE_STATUSES = Set.of("en_route_to_pickup");

    private CustomerBookingRules() {
    }

    /**
     * @param horsesPickedUp kíp xe đã nhận xong ngựa ở điểm đón: ngựa đang trên xe nên khách không tự hủy được,
     *                       cần Quản lý hủy chuyến để kíp trả ngựa và hồ sơ gốc có biên bản
     */
    public static boolean canCancel(String status, boolean balancePaid, boolean horsesPickedUp) {
        if (status == null || balancePaid) return false;
        if (FREE_WITHDRAW_STATUSES.contains(status)) return true;
        return CANCELLABLE_STATUSES.contains(status) && !horsesPickedUp;
    }

    public static boolean isBeforeDeposit(String status) {
        return status != null && FREE_WITHDRAW_STATUSES.contains(status);
    }

    public static boolean canPayBalance(String status, boolean balancePaid) {
        return !balancePaid && status != null && BALANCE_PAYABLE_STATUSES.contains(status);
    }
}
