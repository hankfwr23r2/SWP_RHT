package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.PaymentCheckoutResponseDto;

public interface PaymentCheckoutService {
    PaymentCheckoutResponseDto checkout(Long paymentId, String username, String ipAddress);
}
