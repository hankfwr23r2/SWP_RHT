package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.LocationType;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

// Quy tắc ghi sức khỏe và rời trạm nghỉ (B3), hàm thuần
public final class HealthLogPolicy {

    private HealthLogPolicy() {}

    // Mỗi lần ghi gắn đúng một nơi: điểm dừng của xe hoặc một lần lưu trú
    public static void requireOnePlace(boolean atStop, boolean inStay) {
        if (atStop == inStay) {
            throw new DispatchRuleException("tripStopId", "Ghi sức khỏe tại một điểm dừng hoặc trong một lần lưu trú.");
        }
    }

    public static boolean canRecordStay(LocationType type, HorseStayType stayType, boolean hasOperator) {
        return type == LocationType.HUB || type == LocationType.STABLE
                || (stayType == HorseStayType.OVERNIGHT && hasOperator);
    }

    public static String reportedNote(String reporter, String channel, String note) {
        if (reporter == null || reporter.isBlank()) {
            throw new DispatchRuleException("reportedBy", "Nhập tên người báo.");
        }
        if (channel == null || channel.isBlank()) {
            throw new DispatchRuleException("reportChannel", "Nhập kênh tiếp nhận báo cáo.");
        }
        String result = "Người báo: " + reporter.strip() + "\nKênh báo: " + channel.strip();
        if (note != null && !note.isBlank()) result += "\n" + note.strip();
        if (result.length() > 2000) {
            throw new DispatchRuleException("note", "Ghi chú gồm nguồn báo tối đa 2000 ký tự.");
        }
        return result;
    }

    // Bỏ uống 2 lần dừng liền thì ít nhất WATCH (docs/NGHIEP_VU_THEO_59_BANG.md mục 7.2)
    public static boolean refusedWaterTwice(IntakeLevel now, IntakeLevel previous) {
        return now == IntakeLevel.REFUSED && previous == IntakeLevel.REFUSED;
    }

    public static HealthStatus atLeastWatch(HealthStatus status) {
        return status.ordinal() < HealthStatus.WATCH.ordinal() ? HealthStatus.WATCH : status;
    }

    // Quy tắc #34: rời trạm khi mọi con trên xe đã được ghi uống và ăn ở trạm này
    public static void requireAllLogged(Collection<String> horsesNotLogged) {
        if (!horsesNotLogged.isEmpty()) {
            throw new DispatchRuleException("healthLogs", "Chưa ghi ăn uống cho: " + String.join(", ", horsesNotLogged) + ".", true);
        }
    }

    public static boolean isOvernightRest(Instant plannedArrivalAt, Instant plannedDepartureAt, int overnightRestHours) {
        return Duration.between(plannedArrivalAt, plannedDepartureAt).compareTo(Duration.ofHours(overnightRestHours)) >= 0;
    }

    public static boolean requireRested(Instant arrivedAt, Instant now, int minRestMinutes, String earlyReason) {
        return requireRested(arrivedAt, now, Duration.ofMinutes(minRestMinutes), minRestMinutes + " phút", earlyReason);
    }

    public static boolean requireOvernightRested(Instant arrivedAt, Instant now, int overnightRestHours, String earlyReason) {
        return requireRested(arrivedAt, now, Duration.ofHours(overnightRestHours), overnightRestHours + " giờ", earlyReason);
    }

    // Quy tắc #34: trả về false khi rời sớm có lý do.
    private static boolean requireRested(Instant arrivedAt, Instant now, Duration minimumRest, String minimumLabel, String earlyReason) {
        boolean rested = Duration.between(arrivedAt, now).compareTo(minimumRest) >= 0;
        if (!rested && (earlyReason == null || earlyReason.isBlank())) {
            throw new DispatchRuleException("earlyReason", "Chưa nghỉ đủ " + minimumLabel + ". Rời sớm phải ghi lý do.", true);
        }
        return rested;
    }
}
