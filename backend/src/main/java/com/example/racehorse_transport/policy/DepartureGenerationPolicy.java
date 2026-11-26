package com.example.racehorse_transport.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Sinh chuyến cụ thể từ lịch chạy định kỳ. Hàm thuần: ngày hôm nay, múi giờ do bên gọi truyền vào.
public final class DepartureGenerationPolicy {

    private DepartureGenerationPolicy() {
    }

    // Các ngày có chuyến trong khoảng (today, today + weeks tuần], nằm trong hiệu lực của lịch và đúng thứ trong tuần.
    // daysOfWeek theo ISO: 1 = thứ Hai, 7 = Chủ nhật. validTo null nghĩa là vô thời hạn.
    public static List<LocalDate> datesToGenerate(LocalDate today, int weeks, LocalDate validFrom, LocalDate validTo, Set<Short> daysOfWeek) {
        if (weeks <= 0 || daysOfWeek == null || daysOfWeek.isEmpty()) {
            return List.of();
        }
        LocalDate first = today.plusDays(1).isBefore(validFrom) ? validFrom : today.plusDays(1);
        LocalDate last = today.plusWeeks(weeks);
        if (validTo != null && validTo.isBefore(last)) {
            last = validTo;
        }
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (daysOfWeek.contains((short) date.getDayOfWeek().getValue())) {
                dates.add(date);
            }
        }
        return dates;
    }

    // Giờ xuất phát: ngày chạy và giờ của lịch, theo giờ địa phương của công ty
    public static Instant plannedDepartureAt(LocalDate departureDate, LocalTime departureTime, ZoneId zone) {
        return departureDate.atTime(departureTime).atZone(zone).toInstant();
    }

    // Chốt nhận đơn trước giờ xuất phát bao nhiêu giờ
    public static Instant bookingCutoffAt(Instant plannedDepartureAt, int bookingCutoffHours) {
        return plannedDepartureAt.minus(Duration.ofHours(bookingCutoffHours));
    }
}
