package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.MicrochipEntryMethod;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleEndCondition;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

// Quy tắc thực hiện lượt xe tại điểm dừng (B2), hàm thuần
public final class TripExecutionPolicy {

    public static boolean startsEarly(java.time.Instant planned, java.time.Instant actual, int thresholdMinutes) {
        return actual.isBefore(planned.minusSeconds(thresholdMinutes * 60L));
    }

    private TripExecutionPolicy() {}

    // Xe xuất phát khi lượt chưa chạy và tài xế đã xác nhận nhận việc (GĐ5)
    public static void requireStartable(VehicleTripStatus status, boolean driverAcknowledged) {
        if (status != VehicleTripStatus.PLANNED && status != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("status", "Lượt xe đã chạy hoặc đã hủy.", true);
        }
        if (!driverAcknowledged) {
            throw new DispatchRuleException("crew", "Tài xế chưa xác nhận nhận việc.", true);
        }
    }

    public static void requireEscortAcknowledged(boolean escortAcknowledged) {
        if (!escortAcknowledged) {
            throw new DispatchRuleException("crew", "Lượt chở ngựa phải có hộ tống đã nhận việc.", true);
        }
    }

    public static void requireInProgress(VehicleTripStatus status) {
        if (status != VehicleTripStatus.IN_PROGRESS) {
            throw new DispatchRuleException("status", "Lượt xe chưa xuất phát hoặc đã kết thúc.", true);
        }
    }

    // Tới điểm dừng theo thứ tự: điểm trước đã rời, điểm này chưa tới
    public static void requireCanArrive(boolean alreadyArrived, boolean previousDeparted) {
        if (alreadyArrived) {
            throw new DispatchRuleException("tripStopId", "Đã ghi nhận tới điểm dừng này.", true);
        }
        if (!previousDeparted) {
            throw new DispatchRuleException("tripStopId", "Chưa rời điểm dừng trước.", true);
        }
    }

    // Rời điểm dừng khi đã tới, chưa rời, và không còn lần giao nhận đang dở
    public static void requireCanDepart(boolean arrived, boolean departed, long pendingHandovers) {
        if (!arrived) {
            throw new DispatchRuleException("tripStopId", "Chưa tới điểm dừng này.", true);
        }
        if (departed) {
            throw new DispatchRuleException("tripStopId", "Đã rời điểm dừng này.", true);
        }
        if (pendingHandovers > 0) {
            throw new DispatchRuleException("tripStopId", "Còn lần giao nhận chưa xong ở điểm dừng này.", true);
        }
    }

    // Loại giao nhận hợp với điểm dừng: đón ở địa chỉ đón, giao ở địa chỉ giao, vào và ra hub ở hub
    public static void requireHandoverFitsStop(HandoverType type, TripStopType stopType) {
        boolean fits = switch (type) {
            case PICKUP -> stopType == TripStopType.PICKUP;
            case RETURN -> stopType == TripStopType.PICKUP;
            case DELIVERY -> stopType == TripStopType.DELIVERY;
            case HUB_IN, HUB_OUT -> stopType == TripStopType.HUB;
            // Bàn giao với đại lý nước ngoài ở cửa khẩu (GĐ7.4)
            // Bàn giao ca ở bất kỳ điểm dừng nào tổ xe đang đứng
            case SHIFT -> true;
            // Nghỉ đêm: gửi ngựa cho đối tác chuồng ở trạm nghỉ hoặc cửa khẩu rồi nhận lại
            case STAY_IN, STAY_OUT -> stopType == TripStopType.REST || stopType == TripStopType.VITAL_CHECK || stopType == TripStopType.BORDER;
            default -> false;
        };
        if (!fits) {
            throw new DispatchRuleException("handoverType", "Không giao nhận loại " + type + " ở điểm dừng " + stopType + ".");
        }
    }

    public static boolean isStableHandover(HandoverType type) {
        return type == HandoverType.STAY_IN || type == HandoverType.STAY_OUT;
    }

    public static void requireNetworkStableStop(boolean hasLocation) {
        if (!hasLocation) {
            throw new DispatchRuleException("tripStopId", "Không gửi chuồng tại địa chỉ khách được. Chọn đổi xe hoặc đưa về hub.", true);
        }
    }

    // Ngựa nhận lên xe (đón, ra hub) hay trả xuống (giao, vào hub)
    public static boolean isBoarding(HandoverType type) {
        return type == HandoverType.PICKUP || type == HandoverType.HUB_OUT;
    }

    // Chip đọc được khớp chip trong hồ sơ ngựa, sau khi chuẩn hóa
    public static boolean chipMatches(String scanned, String expected) {
        String normalized = MicrochipPolicy.normalize(scanned);
        return !normalized.isEmpty() && normalized.equals(MicrochipPolicy.normalize(expected));
    }

    public static String manualEntryReason(MicrochipEntryMethod method, String reason) {
        if (method != MicrochipEntryMethod.MANUAL) return null;
        if (reason == null || reason.isBlank()) {
            throw new DispatchRuleException("manualReason", "Vui lòng ghi lý do gõ tay mã chip.");
        }
        return reason.strip();
    }

    // Hoàn tất giao nhận khi có ít nhất một con đã quét đúng chip; con sai chip thì không nhận (GĐ6)
    public static void requireSomeMatched(Collection<Boolean> matches) {
        if (matches.stream().noneMatch(Boolean.TRUE::equals)) {
            throw new DispatchRuleException("horses", "Chưa có con nào quét đúng chip.", true);
        }
    }

    public static void requireLastStopArrived(boolean lastStopArrived) {
        if (!lastStopArrived) {
            throw new DispatchRuleException("status", "Chưa tới điểm dừng cuối.", true);
        }
    }

    // Kết thúc lượt khi đã tới điểm cuối và không còn ngựa trên xe
    public static void requireCanComplete(boolean lastStopArrived, long horsesStillOnBoard) {
        requireLastStopArrived(lastStopArrived);
        if (horsesStillOnBoard > 0) {
            throw new DispatchRuleException("status", "Còn " + horsesStillOnBoard + " con ngựa trên xe chưa bàn giao.", true);
        }
    }

    public static void requireVehicleConditionNote(VehicleEndCondition condition, String note) {
        if (condition == VehicleEndCondition.NEEDS_MAINTENANCE && (note == null || note.isBlank())) {
            throw new DispatchRuleException("endVehicleNote", "Nhập ghi chú khi xe cần bảo dưỡng.");
        }
    }

    // Bắt đầu chờ người nhận ở lần giao đang mở (mục 6.5)
    public static void requireCanStartWaiting(HandoverType type, boolean pending, boolean alreadyWaiting) {
        if (type != HandoverType.DELIVERY || !pending) {
            throw new DispatchRuleException("handoverId", "Chỉ bắt đầu chờ ở lần giao ngựa đang mở.", true);
        }
        if (alreadyWaiting) {
            throw new DispatchRuleException("handoverId", "Đã bắt đầu chờ người nhận.", true);
        }
    }

    // Xe tới sau khung giờ đã báo thì người nhận vắng không phải lỗi của khách (mục 13.2)
    // Không gặp người nhận: chỉ khi đã chờ hết thời gian chờ miễn phí (mục 6.6, B5)
    public static void requireFreeWaitOver(Instant waitStartedAt, Instant now, int freeWaitMinutes) {
        if (waitStartedAt == null) {
            throw new DispatchRuleException("handoverId", "Chưa bắt đầu chờ người nhận.", true);
        }
        if (Duration.between(waitStartedAt, now).compareTo(Duration.ofMinutes(freeWaitMinutes)) < 0) {
            throw new DispatchRuleException("handoverId", "Chưa hết " + freeWaitMinutes + " phút chờ miễn phí.", true);
        }
    }
}

