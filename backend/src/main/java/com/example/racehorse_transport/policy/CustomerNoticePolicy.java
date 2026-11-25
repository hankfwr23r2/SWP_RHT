package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

// Câu chữ và khung giờ báo khách (MAN_HINH 3.4, 3.5c, 3.9), hàm thuần
public final class CustomerNoticePolicy {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM").withZone(ZONE);

    public record Notice(String title, String message) {}

    // Khung giờ báo khách: giờ kế hoạch làm tròn xuống đầu giờ, dài một giờ
    public record HourWindow(Instant from, Instant to) {}

    // Giờ xe dự kiến đến điểm đón, điểm giao của đơn. Chặng không gặp khách ở đầu nào thì đầu đó NULL
    public record ExpectedTimes(Instant pickup, Instant delivery) {

        public boolean isEmpty() {
            return pickup == null && delivery == null;
        }
    }

    private CustomerNoticePolicy() {}

    public static Notice orderReceived(String bookingCode) {
        return new Notice("Đã nhận đơn", "Công ty đã nhận đơn " + bookingCode + ". Chuyên viên sẽ kiểm hồ sơ rồi gửi báo giá.");
    }

    public static Notice orderWaitingIntake(String bookingCode) {
        return new Notice("Đơn mới chờ tiếp nhận", "Đơn " + bookingCode + " vừa được gửi và đang chờ tiếp nhận.");
    }

    public static Notice hubDropOff(String bookingCode, String hubName, Instant at) {
        return new Notice("Giờ hẹn đưa ngựa tới hub",
                "Đơn " + bookingCode + ": đưa ngựa tới " + hubName + " lúc " + moment(at) + ".");
    }

    public static Notice hubCollection(String bookingCode, String hubName, Instant at) {
        return new Notice("Giờ hẹn nhận ngựa ở hub",
                "Đơn " + bookingCode + ": nhận ngựa tại " + hubName + " lúc " + moment(at) + ".");
    }

    public static HourWindow window(Instant at) {
        ZonedDateTime from = at.atZone(ZONE).truncatedTo(ChronoUnit.HOURS);
        return new HourWindow(from.toInstant(), from.plusHours(1).toInstant());
    }

    // Giờ xe tới điểm lên (đón) và điểm xuống (giao). Chặng trục, chuyển và chặng đại lý không báo
    public static ExpectedTimes expectedTimes(HorseLeg leg) {
        boolean meetsShipper = leg.getLegType() == HorseLegType.PICKUP || leg.getLegType() == HorseLegType.DIRECT;
        boolean meetsConsignee = leg.getLegType() == HorseLegType.DELIVERY || leg.getLegType() == HorseLegType.DIRECT;
        Instant pickup = leg.getBoardTripStop() == null ? leg.getPlannedStartAt() : leg.getBoardTripStop().getPlannedArrivalAt();
        return new ExpectedTimes(meetsShipper ? pickup : null, meetsConsignee ? leg.getPlannedEndAt() : null);
    }

    // Giờ khách đang biết, suy từ các chặng trước cùng loại của con ngựa (theo thứ tự): chặng hủy lệch quá ngưỡng là đã báo lại,
    // lệch ít hơn thì khách vẫn giữ giờ cũ. Chặng đã chạy thì giờ cũ hết ý nghĩa. NULL: chưa báo lần nào
    public static ExpectedTimes lastTold(List<HorseLeg> previousSameType, int noticeMinutes) {
        ExpectedTimes told = null;
        for (HorseLeg leg : previousSameType) {
            ExpectedTimes planned = expectedTimes(leg);
            if (leg.getStatus() != HorseLegStatus.CANCELLED) {
                told = null;
            } else if (told == null || changed(told, planned, noticeMinutes)) {
                told = planned;
            }
        }
        return told;
    }

    // Lần đầu thì báo giờ dự kiến; đã báo rồi thì chỉ báo lại khi lệch quá ngưỡng
    public static Optional<Notice> expectedNotice(String bookingCode, ExpectedTimes told, ExpectedTimes now, int noticeMinutes) {
        if (now.isEmpty() || (told != null && !changed(told, now, noticeMinutes))) {
            return Optional.empty();
        }
        String plan = plan(now);
        return Optional.of(told == null
                ? new Notice("Giờ xe dự kiến", "Đơn " + bookingCode + ": xe dự kiến " + plan + ".")
                : new Notice("Giờ dự kiến thay đổi", "Đơn " + bookingCode + " đổi giờ: xe dự kiến " + plan + "."));
    }

    public static String text(HourWindow window) {
        return CLOCK.format(window.from()) + "–" + CLOCK.format(window.to()) + " ngày " + DAY.format(window.from());
    }

    public static String deliveryWindowText(HourWindow window) {
        if (!window.from().atZone(ZONE).toLocalDate().equals(window.to().atZone(ZONE).toLocalDate())) {
            return moment(window.from()) + " – " + moment(window.to());
        }
        return text(window);
    }

    private static String plan(ExpectedTimes times) {
        String pickup = times.pickup() == null ? null : "đến đón ngựa " + text(window(times.pickup()));
        String delivery = times.delivery() == null ? null : "giao ngựa " + text(window(times.delivery()));
        return pickup == null ? delivery : delivery == null ? pickup : pickup + ", " + delivery;
    }

    private static boolean changed(ExpectedTimes told, ExpectedTimes now, int noticeMinutes) {
        return shifted(told.pickup(), now.pickup(), noticeMinutes) || shifted(told.delivery(), now.delivery(), noticeMinutes);
    }

    private static boolean shifted(Instant told, Instant now, int noticeMinutes) {
        if (told == null || now == null) {
            return told != now;
        }
        return Duration.between(told, now).abs().compareTo(Duration.ofMinutes(noticeMinutes)) > 0;
    }

    private static String moment(Instant at) {
        return CLOCK.format(at) + " ngày " + DAY.format(at);
    }
}
