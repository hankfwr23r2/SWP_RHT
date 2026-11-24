package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.PaymentCheckoutResponseDto;
import com.example.racehorse_transport.entity.Payment;
import java.util.List;

public interface PaymentCheckoutService {
    boolean hasPendingBalanceConfirmation(Long bookingId);

    // Còn link của khoản thu chưa trả mà VNPAY chưa trả lời, tính tới hết hạn link cộng thời gian chờ kết quả
    boolean hasPendingConfirmation(Long bookingId);

    boolean isBalanceOverdue(List<Payment> entries);

    PaymentCheckoutResponseDto checkout(Long paymentId, String username, String ipAddress);
}
