package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.dto.manager.QuoteLineDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class HorseInsurancePolicy {
    private HorseInsurancePolicy() {}
    public static final String LABEL = "Thu hộ bảo hiểm bên thứ ba";
    public static void validateDeclaration(HorseInsuranceDto i) {
        if (i == null || !Set.of("personal", "assistance", "none").contains(Objects.toString(i.getOption(), "")))
            throw new IllegalArgumentException("Chọn bảo hiểm cho từng ngựa.");
        validateDraft(i);
        if ("personal".equals(i.getOption())) {
            if (blank(i.getProvider()) || blank(i.getPolicyNumber()))
                throw new IllegalArgumentException("Nhập đơn vị bảo hiểm và số hợp đồng cá nhân.");
            validateDates(i);
        }
    }
    public static void validateDraft(HorseInsuranceDto i) {
        if (i == null || !Set.of("personal", "assistance", "none").contains(Objects.toString(i.getOption(), "")))
            throw new IllegalArgumentException("Chọn bảo hiểm cho từng ngựa.");
        if (i.getPremium() != null && (i.getPremium().signum() < 0 || i.getPremium().stripTrailingZeros().scale() > 0 || i.getPremium().compareTo(new BigDecimal("1000000000000")) > 0))
            throw new IllegalArgumentException("Phí bảo hiểm phải là số nguyên VNĐ, từ 0 đến 1.000 tỷ.");
        if (i.getQuoteValidUntil() != null && !i.getQuoteValidUntil().isBlank()) {
            try { LocalDate.parse(i.getQuoteValidUntil()); }
            catch (Exception ex) { throw new IllegalArgumentException("Hạn báo giá bảo hiểm không hợp lệ."); }
        }
        if (java.util.stream.Stream.of(i.getDocuments(), i.getPolicyDocuments()).filter(Objects::nonNull)
            .anyMatch(docs -> docs.size() > 10 || docs.stream().anyMatch(u -> u == null || !u.startsWith("https://"))))
            throw new IllegalArgumentException("Chứng từ phải là liên kết HTTPS, tối đa 10 tệp.");
    }
    public static void validateDates(HorseInsuranceDto i) {
        try {
            if (LocalDate.parse(i.getValidUntil()).isBefore(LocalDate.parse(i.getValidFrom()))) throw new IllegalArgumentException();
        } catch (Exception e) { throw new IllegalArgumentException("Thời hạn bảo hiểm không hợp lệ."); }
    }
    public static void requireQuoteReady(List<BookingHorseDto> horses) {
        if (horses == null || horses.isEmpty()) throw new IllegalStateException("Cần xác nhận lại danh sách ngựa và bảo hiểm trước báo giá.");
        for (BookingHorseDto h : horses) {
            HorseInsuranceDto i = h.getInsurance();
            validateDeclaration(i);
            if ("assistance".equals(i.getOption()) && !blank(i.getQuoteValidUntil())
                    && LocalDate.parse(i.getQuoteValidUntil()).isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))))
                throw new IllegalStateException("Báo giá bảo hiểm đã hết hạn cho " + h.getName());
            if (!"none".equals(i.getOption()) && !Boolean.TRUE.equals(i.getReviewed()))
                throw new IllegalStateException("Chưa hoàn tất bảo hiểm cho " + h.getName());
            if ("assistance".equals(i.getOption()) && (blank(i.getProvider()) || i.getPremium() == null || i.getPremium().signum() <= 0))
                throw new IllegalStateException("Cần đơn vị cung cấp và phí bảo hiểm lớn hơn 0 cho " + h.getName());
        }
    }
    public static List<QuoteLineDto> quoteLines(List<BookingHorseDto> horses) {
        return horses.stream().filter(h -> h.getInsurance() != null && "assistance".equals(h.getInsurance().getOption()))
            .map(h -> QuoteLineDto.builder().label(LABEL).detail(h.getHorseId() + " · " + h.getName() + " · " + h.getInsurance().getProvider())
                .calculation("Phí do bên thứ ba báo. Công ty thu hộ, không áp dụng tỷ lệ theo giống.")
                .amount(h.getInsurance().getPremium()).build()).toList();
    }
    public static void requireDepartureReady(List<BookingHorseDto> horses, List<String> horseIds, java.time.Instant departure) {
        if (horses == null || horseIds == null) throw new IllegalStateException("Chưa có danh sách ngựa để xác nhận bảo hiểm.");
        for (BookingHorseDto h : horses) {
            if (!horseIds.contains(h.getHorseId()) || h.getInsurance() == null) continue;
            HorseInsuranceDto i = h.getInsurance();
            if ("none".equals(i.getOption())) continue;
            if (!Boolean.TRUE.equals(i.getReviewed()) || ("assistance".equals(i.getOption()) && (!Boolean.TRUE.equals(i.getArranged()) || blank(i.getPolicyNumber()))))
                throw new IllegalStateException("Chưa xác nhận bảo hiểm có hiệu lực cho " + h.getName());
            validateDates(i);
            LocalDate day = departure.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
            if (day.isBefore(LocalDate.parse(i.getValidFrom())) || day.isAfter(LocalDate.parse(i.getValidUntil())))
                throw new IllegalStateException("Bảo hiểm không bao phủ ngày khởi hành của " + h.getName());
        }
    }
    public static BigDecimal insuranceDeposit(Object quoteObject, BigDecimal paid) {
        if (!(quoteObject instanceof Map<?, ?> quote) || !(quote.get("lines") instanceof List<?> lines) || quote.get("total") == null) return BigDecimal.ZERO;
        BigDecimal total = new BigDecimal(quote.get("total").toString());
        if (total.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal insurance = BigDecimal.ZERO;
        for (Object row : lines) if (row instanceof Map<?, ?> line && LABEL.equals(line.get("label"))) insurance = insurance.add(new BigDecimal(line.get("amount").toString()));
        return paid.multiply(insurance).divide(total, 0, java.math.RoundingMode.HALF_UP).min(paid);
    }
    public static boolean blank(String s) { return s == null || s.isBlank(); }
}
