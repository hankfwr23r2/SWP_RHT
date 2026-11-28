package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.enums.BookingStatus;

// Mail hóa đơn gửi khách ở các mốc tiền của đơn: báo giá, biên nhận cọc, cần chọn lại chuyến, hủy đơn, bảng quyết toán.
// Gọi trong transaction đổi trạng thái; mail dựng và gửi sau khi transaction lưu xong, lỗi gửi chỉ ghi log.
public interface BillMailService {

    void statusChanged(Long bookingId, BookingStatus from, BookingStatus to, String reason);
}
