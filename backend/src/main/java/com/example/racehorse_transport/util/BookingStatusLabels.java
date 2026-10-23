package com.example.racehorse_transport.util;

import java.util.Map;

// Nhãn hiển thị của từng trạng thái đơn: một bảng duy nhất ở backend, frontend chỉ hiển thị nhãn nhận về.
// Nội bộ thấy nhãn nghiệp vụ, khách thấy nhãn dễ hiểu. Trạng thái chưa có trong bảng thì hiện nguyên mã.
public final class BookingStatusLabels {

    private record Label(String staff, String customer) {}

    private static final Map<String, Label> LABELS = Map.ofEntries(
            Map.entry("pending_intake", new Label("Chờ quản lý tiếp nhận", "Đã gửi, chờ tiếp nhận")),
            Map.entry("under_review", new Label("Đang thẩm định nội bộ", "Đang thẩm định")),
            // Mã cũ còn trong dữ liệu: đơn đang thẩm định, chờ khách bổ sung hồ sơ
            Map.entry("resubmission_requested", new Label("Đang thẩm định nội bộ", "Đang thẩm định")),
            Map.entry("pending_commercial", new Label("Chờ quản lý duyệt báo giá", "Đang lập báo giá")),
            Map.entry("awaiting_payment", new Label("Chờ khách đặt cọc", "Chờ đặt cọc")),
            Map.entry("quote_expired", new Label("Báo giá hết hạn", "Báo giá hết hạn")),
            Map.entry("waybill_issued", new Label("Đã có Vận đơn, chờ kiểm dịch viên tiếp nhận", "Đã có vận đơn")),
            Map.entry("clearance_in_progress", new Label("Kiểm dịch viên đang làm thủ tục giấy tờ", "Đang làm thủ tục giấy tờ")),
            Map.entry("clearance_done", new Label("Giấy tờ xong, chờ tài xế và hộ tống nhận lệnh", "Giấy tờ đã xong")),
            Map.entry("ready_for_pickup", new Label("Sẵn sàng đón ngựa", "Sẵn sàng đón ngựa")),
            Map.entry("en_route_to_pickup", new Label("Xe đang đến điểm đón", "Xe đang đến điểm đón")),
            Map.entry("in_transit", new Label("Đang vận chuyển", "Đang vận chuyển")),
            Map.entry("incident_reported", new Label("Có sự cố, chờ điều phối lập phương án", "Xe gặp sự cố, đang xử lý")),
            Map.entry("pending_emergency_approval", new Label("Chờ quản lý duyệt phương án khẩn cấp", "Xe gặp sự cố, đang xử lý")),
            Map.entry("emergency_plan_active", new Label("Đang thực hiện phương án khẩn cấp", "Đang xử lý sự cố, ngựa được chăm sóc")),
            Map.entry("arrived", new Label("Xe đã tới điểm giao, chờ bàn giao", "Xe đã tới điểm giao")),
            Map.entry("handover_timeout", new Label("Quá hạn bàn giao, ngựa đang lưu chuồng gần điểm giao", "Quá giờ nhận ngựa, ngựa đang được lưu chuồng")),
            Map.entry("returning_to_hq", new Label("Quá hạn bàn giao, xe chở ngựa về trụ sở", "Ngựa đang được chở về trụ sở")),
            Map.entry("delivered_pending_settlement", new Label("Đã giao, chờ tài xế gửi chi phí", "Đã giao ngựa")),
            Map.entry("expenses_submitted", new Label("Chờ quản lý đối soát chi phí", "Đã giao, đang quyết toán")),
            Map.entry("settlement_issued", new Label("Đã phát hành quyết toán, chờ khách trả", "Chờ thanh toán quyết toán")),
            Map.entry("payment_overdue", new Label("Khách quá hạn thanh toán quyết toán", "Quá hạn thanh toán")),
            Map.entry("completed", new Label("Đã hoàn tất", "Đã hoàn tất")),
            Map.entry("cancelled", new Label("Khách từ chối báo giá", "Đã hủy")),
            Map.entry("rejected", new Label("Nhà xe đã từ chối đơn", "Đơn bị từ chối")));

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
