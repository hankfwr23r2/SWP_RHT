package com.example.racehorse_transport.dto.quotation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuotationDtoTest {
    @Test
    void adjustmentDtoOnlyCopiesTheReasonAndTheResolvedForeignKey() {
        var quotation = new Quotation();
        var line = new QuotationAdjustmentRequestDto(new BigDecimal("-5000"), "  Điều chỉnh  ").toEntity(quotation);
        assertThat(line.getQuotation()).isSameAs(quotation);
        assertThat(line.getDescription()).isEqualTo("Điều chỉnh");
        assertThat(line.getId()).isNull();
        assertThat(line.getLineType()).isNull();
        assertThat(line.getUnitPrice()).isNull();
        assertThat(line.getAmount()).isNull();
    }

    @Test
    void quotationResponseCopiesTheStoredAmountsAndLines() {
        var booking = new Booking();
        booking.setId(2L);
        var quotation = new Quotation();
        quotation.setId(3L);
        quotation.setBooking(booking);
        quotation.setVersion(1);
        quotation.setStatus(QuotationStatus.SENT);
        quotation.setTotalAmount(new BigDecimal("3600"));
        var line = new QuotationLine();
        line.setLineNumber(1);
        line.setAmount(new BigDecimal("3600"));
        line.setPassThrough(false);
        var result = QuotationResponseDto.from(quotation, List.of(line));
        assertThat(result.bookingId()).isEqualTo(2L);
        assertThat(result.totalAmount()).isEqualByComparingTo("3600");
        assertThat(result.lines()).containsExactly(QuotationLineResponseDto.from(line));
    }
}
