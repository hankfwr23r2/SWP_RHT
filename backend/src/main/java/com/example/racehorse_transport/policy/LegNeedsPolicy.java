package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Chặng một con ngựa còn thiếu theo cách chạy của đơn (NGHIEP_VU mục 1.2), hàm thuần.
// Đầu khách tự chở không có chặng xe. Đầu nước ngoài của đơn xuất, nhập do đại lý chở nhưng vẫn phải lập chặng.
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
            if (type == BookingType.IMPORT || pickup != TransferMethod.SELF) {
                required.add(HorseLegType.PICKUP);
            }
            required.add(HorseLegType.LINEHAUL);
            if (type == BookingType.EXPORT || delivery != TransferMethod.SELF) {
                required.add(HorseLegType.DELIVERY);
            }
        }
        required.removeIf(planned::contains);
        return List.copyOf(required);
    }
}
