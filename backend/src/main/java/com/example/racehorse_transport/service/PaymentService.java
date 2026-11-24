package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.entity.Quotation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentService {
    List<PaymentResponseDto> payments(Long bookingId, String username);

    // Luồng B gọi khi hoàn tất nhận ngựa. A khóa đơn trước khi tạo khoản thu.
    void requestBalance(Long bookingId, Instant receivedAt);

    // Còn link số dư mở trước hạn mà VNPAY chưa trả lời (tối đa hạn của link cộng thời gian chờ kết quả)
    // Các thao tác nội bộ A7 chạy trong transaction đang giữ khóa booking.
    void createDeposit(Quotation quotation);

    void cancelPendingDeposits(Long bookingId);

    boolean expireDepositAndRelease(Quotation quotation, Instant now);

    BigDecimal reconcileBalance(Quotation quotation);

    Long createExtraCharge(Long bookingId, List<Long> costItemIds, Long actorUserId);

    Long createExtraChargeForUser(Long bookingId, List<Long> costItemIds, String username);
}
