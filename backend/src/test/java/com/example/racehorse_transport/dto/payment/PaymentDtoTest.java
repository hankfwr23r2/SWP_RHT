package com.example.racehorse_transport.dto.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentDtoTest {
    @Test
    void responseCopiesStoredAmountAndForeignKeys() {
        var booking = new Booking();
        booking.setId(1L);
        var quotation = new Quotation();
        quotation.setId(2L);
        var payment = new Payment();
        payment.setId(3L);
        payment.setBooking(booking);
        payment.setQuotation(quotation);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("3500"));

        var result = PaymentResponseDto.from(payment);
        assertThat(result.bookingId()).isEqualTo(1L);
        assertThat(result.quotationId()).isEqualTo(2L);
        assertThat(result.amount()).isEqualByComparingTo("3500");
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.confirmationUntil()).isNull();
        assertThat(PaymentResponseDto.from(payment, java.time.Instant.EPOCH).confirmationUntil()).isEqualTo(java.time.Instant.EPOCH);

        booking.setCode("BK-1");
        var gateway = PaymentResultResponseDto.from(payment, false, true, "00", "Đối soát", new BigDecimal("3500"), "123");
        assertThat(gateway.bookingCode()).isEqualTo("BK-1");
        assertThat(gateway.needsReview()).isTrue();
        assertThat(PaymentResultResponseDto.unverified("97", "Sai chữ ký").bookingCode()).isNull();
    }
}
