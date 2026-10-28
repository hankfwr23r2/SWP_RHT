package com.example.racehorse_transport.util;

import java.util.Map;

// Nhãn hiển thị của từng trạng thái đơn: một bảng duy nhất ở backend, frontend chỉ hiển thị nhãn nhận về.
// Nội bộ thấy nhãn nghiệp vụ, khách thấy nhãn dễ hiểu. Trạng thái chưa có trong bảng thì hiện nguyên mã.
public final class BookingStatusLabels {

    private record Label(String staff, String customer) {}

    private static final Map<String, Label> LABELS = Map.ofEntries(
            Map.entry("pending_intake", new Label("Chờ tiếp nhận", "Chờ tiếp nhận")),
            Map.entry("under_review", new Label("Đang thẩm định", "Đang thẩm định")),
            // Mã cũ còn trong dữ liệu: đơn đang thẩm định, chờ khách bổ sung hồ sơ
            Map.entry("resubmission_requested", new Label("Đang thẩm định", "Đang thẩm định")),
            Map.entry("pending_commercial", new Label("Chờ duyệt báo giá", "Đang lập báo giá")),
            Map.entry("awaiting_payment", new Label("Chờ đặt cọc", "Chờ đặt cọc")),
            Map.entry("quote_expired", new Label("Báo giá hết hạn", "Báo giá hết hạn")),
            Map.entry("waybill_issued", new Label("Đã có vận đơn", "Đã có vận đơn")),
            Map.entry("clearance_in_progress", new Label("Làm thủ tục giấy tờ", "Làm thủ tục giấy tờ")),
            Map.entry("clearance_done", new Label("Giấy tờ đã xong", "Giấy tờ đã xong")),
            Map.entry("ready_for_pickup", new Label("Sẵn sàng đón ngựa", "Sẵn sàng đón ngựa")),
            Map.entry("en_route_to_pickup", new Label("Đến điểm đón", "Đến điểm đón")),
            Map.entry("in_transit", new Label("Đang vận chuyển", "Đang vận chuyển")),
            Map.entry("incident_reported", new Label("Báo sự cố", "Đang xử lý sự cố")),
            Map.entry("pending_emergency_approval", new Label("Chờ duyệt xử lý sự cố", "Đang xử lý sự cố")),
            Map.entry("emergency_plan_active", new Label("Đang xử lý sự cố", "Đang xử lý sự cố")),
            Map.entry("arrived", new Label("Tới điểm giao", "Tới điểm giao")),
            Map.entry("handover_timeout", new Label("Quá hạn bàn giao", "Quá giờ nhận ngựa")),
            Map.entry("returning_to_hq", new Label("Chở về trụ sở", "Chở về trụ sở")),
            Map.entry("delivered_pending_settlement", new Label("Đã giao ngựa", "Đã giao ngựa")),
            Map.entry("expenses_submitted", new Label("Chờ đối soát chi phí", "Đang quyết toán")),
            Map.entry("settlement_issued", new Label("Chờ thanh toán quyết toán", "Chờ thanh toán quyết toán")),
            Map.entry("payment_overdue", new Label("Quá hạn thanh toán", "Quá hạn thanh toán")),
            Map.entry("completed", new Label("Đã hoàn tất", "Đã hoàn tất")),
            Map.entry("cancelled", new Label("Đã hủy", "Đã hủy")),
            Map.entry("rejected", new Label("Bị từ chối", "Bị từ chối")));

    private BookingStatusLabels() {}

    public static String staffLabel(String status) {
        Label label = status == null ? null : LABELS.get(status);
        return label != null ? label.staff() : status == null ? "" : status;
    }

    public static String customerLabel(String status) {
        Label label = status == null ? null : LABELS.get(status);
        return label != null ? label.customer() : status == null ? "" : status;
    }
}
