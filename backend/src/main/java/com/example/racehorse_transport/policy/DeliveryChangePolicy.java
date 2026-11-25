package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public final class DeliveryChangePolicy {
    private DeliveryChangePolicy() {}

    public static String bookingReason(Booking booking) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) return "NOT_CONFIRMED";
        if (booking.getBookingType() == BookingType.EXPORT) return "EXPORT";
        if (booking.getDeliveryMethod() == TransferMethod.SELF) return "SELF";
        if (booking.getDeparture() == null) return "DIRECT";
        if (booking.getDeparture().getRoute().getDestination().getLocationType() != LocationType.HUB) return "NO_HUB";
        return null;
    }

    public static String paymentReason(List<Payment> payments) {
        if (payments.stream().anyMatch(row -> row.getPaymentType() == PaymentType.BALANCE
                && row.getStatus() == PaymentStatus.PAID)) return "BALANCE_PAID";
        if (PaymentPolicy.paidDeposit(payments).signum() <= 0) return "NO_DEPOSIT";
        return null;
    }

    public static void requireDestination(String oldProvince, String newProvince, Long deliveryHub, Long newHub,
            int places, int limit) {
        if (!Objects.equals(oldProvince, newProvince)) {
            throw BookingRuleException.conflict("Nơi giao mới phải cùng tỉnh vì giấy kiểm dịch ghi tỉnh giao.");
        }
        if (!Objects.equals(deliveryHub, newHub)) {
            throw BookingRuleException.conflict("Nơi giao mới phải thuộc cùng hub giao.");
        }
        if (places > limit) throw BookingRuleException.conflict("Đơn vượt giới hạn số nơi giao.");
    }

    public static boolean replaceLine(QuotationLine row) {
        return row.getHorseLeg() == null && BillingUnit.PER_KM.name().equals(row.getUnit())
                && (row.getLineType() == QuotationLineType.DELIVERY || row.getLineType() == QuotationLineType.FUEL);
    }

    public static BigDecimal deliveryCharge(List<QuotationLine> rows) {
        return rows.stream().filter(DeliveryChangePolicy::replaceLine)
                .map(row -> row.getQuantity().multiply(row.getUnitPrice()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal requireBalance(BigDecimal total, BigDecimal alreadyPaid, BigDecimal expectedTotal) {
        if (total.compareTo(alreadyPaid) < 0) {
            throw BookingRuleException.conflict("Tổng mới thấp hơn số tiền đã trả.");
        }
        if (expectedTotal != null && total.compareTo(expectedTotal) != 0) {
            throw BookingRuleException.conflict("Giá đã thay đổi. Vui lòng xem trước lại.");
        }
        return total.subtract(alreadyPaid);
    }
}
