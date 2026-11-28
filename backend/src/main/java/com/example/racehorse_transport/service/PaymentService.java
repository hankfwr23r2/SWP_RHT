package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.entity.Quotation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentService {
    BigDecimal outstanding(Quotation quotation);
    List<PaymentResponseDto> payments(Long bookingId, String username);

    List<PaymentResponseDto> paymentsForManager(Long bookingId);

    // Luồng B gọi khi hoàn tất nhận ngựa. A khóa đơn trước khi tạo khoản thu.
    void requestBalance(Long bookingId, Instant receivedAt);

    // Các thao tác nội bộ A7 chạy trong transaction đang giữ khóa booking.
    void createDeposit(Quotation quotation);

    void cancelPendingDeposits(Long bookingId);

    boolean expireDepositAndRelease(Quotation quotation, Instant now);

    BigDecimal reconcileBalance(Quotation quotation);

    Long createExtraCharge(Long bookingId, List<Long> costItemIds, Long actorUserId);

    Long createExtraChargeForUser(Long bookingId, List<Long> costItemIds, String username);
}
