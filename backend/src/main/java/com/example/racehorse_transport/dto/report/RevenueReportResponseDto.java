package com.example.racehorse_transport.dto.report;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Báo cáo doanh thu trong một khoảng: đơn tính theo ngày trả cọc, chi phí công ty chịu đã duyệt tính theo ngày phát sinh
public record RevenueReportResponseDto(
        BigDecimal revenue, BigDecimal collected, BigDecimal passThrough, BigDecimal companyCost, BigDecimal profit,
        List<RevenueDayDto> days, List<RevenueBookingDto> bookings) {

    // Một ngày theo giờ Việt Nam
    public record RevenueDayDto(LocalDate date, BigDecimal revenue, BigDecimal cost) {
    }

    public record RevenueBookingDto(Long bookingId, String code, String customerName, Instant depositPaidAt, BookingStatus status,
                                    BigDecimal revenue, BigDecimal passThrough, BigDecimal collected) {
    }
}
