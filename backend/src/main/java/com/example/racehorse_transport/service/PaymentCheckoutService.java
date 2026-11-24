package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.PaymentCheckoutResponseDto;
import com.example.racehorse_transport.entity.Payment;
import java.util.List;

public interface PaymentCheckoutService {
    // Còn link số dư mở trước hạn mà VNPAY chưa trả lời, trong thời gian chờ kết quả sau hạn
    boolean hasPendingBalanceConfirmation(Long bookingId);

    boolean isBalanceOverdue(List<Payment> entries);

    PaymentCheckoutResponseDto checkout(Long paymentId, String username, String ipAddress);
}
