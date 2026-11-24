package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.QuotationLineType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QuotationPolicy {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    private QuotationPolicy() {}

    public record Totals(BigDecimal total, BigDecimal passThrough, BigDecimal deposit) {}

    public static void requireTransition(QuotationStatus current, QuotationStatus target) {
        boolean available = switch (current) {
            case DRAFT -> target == QuotationStatus.SENT || target == QuotationStatus.ACCEPTED;
            case SENT -> target == QuotationStatus.ACCEPTED || target == QuotationStatus.REJECTED
                    || target == QuotationStatus.EXPIRED || target == QuotationStatus.SUPERSEDED;
            case ACCEPTED -> target == QuotationStatus.EXPIRED || target == QuotationStatus.SUPERSEDED;
            case REJECTED, EXPIRED -> target == QuotationStatus.SUPERSEDED;
            case SUPERSEDED -> false;
        };
        if (!available) {
            throw BookingRuleException.conflict("Không thể chuyển trạng thái báo giá.");
        }
    }

    public static Instant deadline(Instant now, int validHours, LocalDate pickup, List<Instant> insuranceDeadlines) {
        Instant endOfPickup = pickup.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant expires = now.plus(validHours, ChronoUnit.HOURS);
        if (endOfPickup.isBefore(expires)) {
            expires = endOfPickup;
        }
        for (Instant insurance : insuranceDeadlines) {
            if (insurance.isBefore(expires)) {
                expires = insurance;
            }
        }
        return DepositPaymentWindowPolicy.expiresAt(expires);
    }

    // Đổi chuyến hoặc ngày đón sau khi lập nháp thì giá và lịch trên nháp không còn đúng
    public static boolean matchesSchedule(Quotation quotation, Booking booking) {
        LocalDate departureDate = booking.getDeparture() == null ? booking.getRequestedPickupDate()
                : booking.getDeparture().getDepartureDate();
        return booking.getRequestedPickupDate().equals(quotation.getConfirmedPickupDate())
                && departureDate.equals(quotation.getConfirmedDepartureDate());
    }

    public static Totals totals(List<QuotationLine> lines, BigDecimal rate) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal passThrough = BigDecimal.ZERO;
        for (QuotationLine line : lines) {
            BigDecimal amount = line.getQuantity().multiply(line.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);
            if (Boolean.TRUE.equals(line.getPassThrough())) {
                passThrough = passThrough.add(amount);
            }
        }
        if (total.compareTo(passThrough) < 0 || total.compareTo(MAX_AMOUNT) > 0) {
            throw BookingRuleException.conflict("Tổng báo giá phải đủ phần thu hộ và nằm trong giới hạn lưu trữ.");
        }
        return new Totals(total, passThrough, DepositPolicy.deposit(rate, total, passThrough));
    }

    public static Map<QuotationLine, BigDecimal> cappedDiscounts(List<QuotationLine> lines) {
        BigDecimal available = lines.stream()
                .filter(line -> !line.getPassThrough() && line.getUnitPrice().signum() >= 0)
                .map(line -> line.getUnitPrice().multiply(line.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<QuotationLine, BigDecimal> prices = new LinkedHashMap<>();
        for (QuotationLine line : lines) {
            if (line.getLineType() == QuotationLineType.ADJUSTMENT && line.getUnitPrice().signum() < 0) {
                BigDecimal discount = line.getUnitPrice().negate().min(available.setScale(0, RoundingMode.DOWN));
                prices.put(line, discount.negate());
                available = available.subtract(discount);
            }
        }
        return prices;
    }
}
