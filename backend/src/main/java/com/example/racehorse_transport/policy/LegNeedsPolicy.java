package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Chặng một con ngựa còn thiếu theo cách chạy của đơn (NGHIEP_VU mục 1.2), hàm thuần.
// Đầu khách tự chở không có chặng xe, ở trong nước hay ở hub nước ngoài (mô hình bến xe 26/11/2026).
public final class LegNeedsPolicy {

    private LegNeedsPolicy() {}

    public static List<HorseLegType> missing(BookingHorseStatus status, BookingType type, boolean hasDeparture,
                                             TransferMethod pickup, TransferMethod delivery, Collection<HorseLegType> planned) {
        if (status != BookingHorseStatus.ACTIVE) {
            return List.of();
        }
        List<HorseLegType> required = new ArrayList<>();
        if (!hasDeparture) {
            required.add(HorseLegType.DIRECT);
        } else {
            if (pickup != TransferMethod.SELF) {
                required.add(HorseLegType.PICKUP);
            }
            required.add(HorseLegType.LINEHAUL);
            if (delivery != TransferMethod.SELF) {
                required.add(HorseLegType.DELIVERY);
            }
        }
        required.removeIf(planned::contains);
        return List.copyOf(required);
    }
}
