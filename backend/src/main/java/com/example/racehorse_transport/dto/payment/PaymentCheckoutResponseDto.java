package com.example.racehorse_transport.dto.payment;

import java.time.Instant;

public record PaymentCheckoutResponseDto(String paymentUrl, Instant expiresAt) {}
