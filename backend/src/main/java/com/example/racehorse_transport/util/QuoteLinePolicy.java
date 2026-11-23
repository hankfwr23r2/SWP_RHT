package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingServiceSelectionDto;
import com.example.racehorse_transport.dto.manager.QuoteLineDto;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Kiểm các dòng báo giá do trình duyệt quản lý gửi lên, trước khi backend tính tổng.
// Dòng "Khoang trên xe" và "Gói thức ăn" phải khớp đơn giá dịch vụ đã chụp lại trong đơn lúc khách đặt (cùng cách cộng
// với addServiceLine ở frontend/src/shared/lib/booking.ts). Các dòng khác do quản lý nhập, chỉ cần có tên và số tiền > 0.
public final class QuoteLinePolicy {
    private QuoteLinePolicy() {}

    public static final int MAX_DAILY_SERVICE_DAYS = 30;
    private static final String STALL_LABEL = "Khoang trên xe";
    private static final String FEED_LABEL = "Gói thức ăn";

    public static void requireValid(List<QuoteLineDto> lines, List<BookingHorseDto> horses) {
        if (lines == null) return;
        for (QuoteLineDto line : lines) {
            if (line.getLabel() == null || line.getLabel().isBlank()) {
                throw new IllegalArgumentException("Mỗi dòng báo giá phải có tên.");
            }
            if (line.getAmount() == null || line.getAmount().signum() <= 0) {
                throw new IllegalArgumentException("Số tiền của dòng \"" + line.getLabel().trim() + "\" phải lớn hơn 0.");
            }
        }
        for (QuoteLineDto line : lines) {
            String label = line.getLabel().trim();
            if (STALL_LABEL.equals(label)) requireMatchesCatalog(line, label, "stall", horses);
            else if (FEED_LABEL.equals(label)) requireMatchesCatalog(line, label, "feed", horses);
        }
    }

    private static void requireMatchesCatalog(QuoteLineDto line, String label, String category, List<BookingHorseDto> horses) {
        if (horses == null || horses.isEmpty()) return;
        Set<String> tripServices = new HashSet<>();
        BigDecimal fixed = BigDecimal.ZERO;
        BigDecimal perDay = BigDecimal.ZERO;
        for (BookingHorseDto horse : horses) {
            BookingServiceSelectionDto service = horse.getSelectedServices() == null ? null : horse.getSelectedServices().get(category);
            if (service == null) return; // Đơn cũ, phí cũ: không có đơn giá chụp lại để đối chiếu
            BigDecimal price = service.getPrice() == null ? BigDecimal.ZERO : service.getPrice();
            if ("PER_TRIP".equals(service.getBillingUnit())) {
                String key = service.getServiceCode() != null ? service.getServiceCode() : "service-" + service.getId();
                if (tripServices.add(key)) fixed = fixed.add(price);
            } else if ("PER_HORSE_DAY".equals(service.getBillingUnit())) {
                perDay = perDay.add(price);
            } else {
                fixed = fixed.add(price);
            }
        }
        BigDecimal amount = line.getAmount();
        if (perDay.signum() == 0) {
            if (amount.compareTo(fixed) != 0) throw mismatch(label, "đúng " + money(fixed));
            return;
        }
        BigDecimal[] days = amount.subtract(fixed).divideAndRemainder(perDay);
        boolean wholeDays = days[1].signum() == 0
                && days[0].compareTo(BigDecimal.ONE) >= 0
                && days[0].compareTo(BigDecimal.valueOf(MAX_DAILY_SERVICE_DAYS)) <= 0;
        if (!wholeDays) {
            throw mismatch(label, money(fixed) + " cộng " + money(perDay) + " nhân số ngày nguyên từ 1 đến " + MAX_DAILY_SERVICE_DAYS);
        }
    }

    private static IllegalArgumentException mismatch(String label, String expected) {
        return new IllegalArgumentException("Số tiền dòng \"" + label + "\" không khớp đơn giá dịch vụ khách đã chọn khi đặt đơn (phải là " + expected + ").");
    }

    private static String money(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + " đ";
    }
}
