package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.report.RevenueReportResponseDto;
import com.example.racehorse_transport.dto.report.RevenueReportResponseDto.RevenueBookingDto;
import com.example.racehorse_transport.dto.report.RevenueReportResponseDto.RevenueDayDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.policy.RevenuePolicy;
import com.example.racehorse_transport.repository.RevenueReportRepository;
import com.example.racehorse_transport.service.RevenueReportService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RevenueReportServiceImpl implements RevenueReportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RevenueReportRepository reports;

    @Override
    @Transactional(readOnly = true)
    public RevenueReportResponseDto revenue(Instant from, Instant to) {
        RevenuePolicy.requireRange(from, to);
        Map<Booking, List<Payment>> paymentsByBooking = reports.paymentsOfBookingsDepositedIn(PaymentType.DEPOSIT, PaymentStatus.PAID, from, to)
                .stream().collect(Collectors.groupingBy(Payment::getBooking, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Quotation> accepted = paymentsByBooking.isEmpty() ? Map.of()
                : reports.quotationsOf(paymentsByBooking.keySet().stream().map(Booking::getId).toList(), QuotationStatus.ACCEPTED).stream()
                        .collect(Collectors.toMap(q -> q.getBooking().getId(), q -> q, (a, b) -> a.getVersion() >= b.getVersion() ? a : b));
        List<RevenueBookingDto> rows = new ArrayList<>();
        paymentsByBooking.forEach((booking, payments) -> {
            // Đơn tính vào kỳ của khoản cọc trả đầu tiên
            Instant depositAt = payments.stream().filter(p -> p.getPaymentType() == PaymentType.DEPOSIT && p.getStatus() == PaymentStatus.PAID)
                    .map(Payment::getPaidAt).min(Comparator.naturalOrder()).orElseThrow();
            if (depositAt.isBefore(from)) return;
            Quotation quotation = accepted.get(booking.getId());
            BigDecimal quoted = quotation != null ? quotation.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal passThrough = quotation != null ? quotation.getPassThroughAmount() : BigDecimal.ZERO;
            BigDecimal received = sum(payments, p -> p.getPaymentType() != PaymentType.REFUND && p.getStatus() == PaymentStatus.PAID);
            BigDecimal extra = sum(payments, p -> p.getPaymentType() == PaymentType.EXTRA_CHARGE && p.getStatus() == PaymentStatus.PAID);
            BigDecimal refunded = sum(payments, p -> p.getPaymentType() == PaymentType.REFUND && p.getStatus() != PaymentStatus.FAILED);
            BigDecimal refundedPaid = sum(payments, p -> p.getPaymentType() == PaymentType.REFUND && p.getStatus() == PaymentStatus.PAID);
            rows.add(new RevenueBookingDto(booking.getId(), booking.getCode(), booking.getCustomer().getFullName(), depositAt, booking.getStatus(),
                    RevenuePolicy.bookingRevenue(booking.getStatus() == BookingStatus.CANCELLED, quoted, passThrough, extra, received, refunded),
                    passThrough, received.subtract(refundedPaid)));
        });
        rows.sort(Comparator.comparing(RevenueBookingDto::depositPaidAt).reversed());
        List<CostItem> costs = reports.approvedCosts(CostPayerType.COMPANY, from, to);
        BigDecimal revenue = rows.stream().map(RevenueBookingDto::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal companyCost = costs.stream().map(CostItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RevenueReportResponseDto(revenue,
                rows.stream().map(RevenueBookingDto::collected).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(RevenueBookingDto::passThrough).reduce(BigDecimal.ZERO, BigDecimal::add),
                companyCost, revenue.subtract(companyCost), days(from, to, rows, costs), rows);
    }

    private static List<RevenueDayDto> days(Instant from, Instant to, List<RevenueBookingDto> rows, List<CostItem> costs) {
        Map<LocalDate, BigDecimal> revenueByDay = rows.stream().collect(Collectors.groupingBy(r -> dayOf(r.depositPaidAt()),
                Collectors.reducing(BigDecimal.ZERO, RevenueBookingDto::revenue, BigDecimal::add)));
        Map<LocalDate, BigDecimal> costByDay = costs.stream().collect(Collectors.groupingBy(c -> dayOf(c.getIncurredAt()),
                Collectors.reducing(BigDecimal.ZERO, CostItem::getAmount, BigDecimal::add)));
        List<RevenueDayDto> days = new ArrayList<>();
        for (LocalDate day = dayOf(from); !day.isAfter(dayOf(to.minusNanos(1))); day = day.plusDays(1)) {
            days.add(new RevenueDayDto(day, revenueByDay.getOrDefault(day, BigDecimal.ZERO), costByDay.getOrDefault(day, BigDecimal.ZERO)));
        }
        return days;
    }

    private static LocalDate dayOf(Instant at) {
        return at.atZone(BUSINESS_ZONE).toLocalDate();
    }

    private static BigDecimal sum(List<Payment> payments, Predicate<Payment> filter) {
        return payments.stream().filter(filter).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
