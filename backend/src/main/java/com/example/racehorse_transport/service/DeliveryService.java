package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.field.*;

public interface DeliveryService {

    // 1. Kíp xe check-in đến điểm giao đích & kích hoạt đếm thời gian
    BookingResponse arriveDelivery(Integer bookingId, DeliveryArriveRequest request);

    // 2. Nghiệm thu & Ký Biên bản bàn giao pháp lý (không rating tài xế)
    BookingResponse completeHandover(Integer bookingId, HandoverCompleteRequest request);

    // 3. Xử lý ngoại lệ: Người nhận không nhận được ngựa đúng hạn (quá 2 giờ)
    BookingResponse reportDeliveryTimeout(Integer bookingId, DeliveryTimeoutRequest request);

    // 4. Manager đối soát hóa đơn đội ngoài & phát hành Bảng quyết toán phát sinh
    BookingResponse issueSettlement(Integer bookingId, IssueSettlementRequest request);

    // 5. Khách hàng thanh toán số dư quyết toán phát sinh

    // 6. Kíp xe xác nhận xe và nhân sự đã về tới Trụ sở chính Việt Nam
    void returnToHq(ReturnToHqRequest request);
}
