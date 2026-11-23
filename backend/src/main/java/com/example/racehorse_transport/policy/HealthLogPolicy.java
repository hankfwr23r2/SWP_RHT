package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import com.example.racehorse_transport.exception.DispatchRuleException;
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

    // Quy tắc #34: đã nghỉ đủ ở trạm; rời sớm phải có lý do
    public static void requireRested(Instant arrivedAt, Instant now, int minRestMinutes, String earlyReason) {
        boolean rested = !Duration.between(arrivedAt, now).minusMinutes(minRestMinutes).isNegative();
        if (!rested && (earlyReason == null || earlyReason.isBlank())) {
            throw new DispatchRuleException("earlyReason", "Chưa nghỉ đủ " + minRestMinutes + " phút. Rời sớm phải ghi lý do.", true);
        }
    }
}
