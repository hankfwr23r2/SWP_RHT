package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.MarkRefundedRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import java.util.List;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.entity.Quotation;
import java.math.BigDecimal;
import org.springframework.web.multipart.MultipartFile;

public interface RefundService {
    void createPending(Long bookingId, String currency, BigDecimal amount);

    BookingCancellationResponseDto reviewCancelledLines(Long bookingId);

    List<PaymentResponseDto> refundUnspentLines(Long bookingId, List<Long> lineIds);

    void refundOverpayment(Quotation quotation);

    PaymentResponseDto markRefunded(Long paymentId, String username, MarkRefundedRequestDto request, MultipartFile receipt);
}
