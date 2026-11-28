package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.PickupMissPriceResponseDto;
import com.example.racehorse_transport.dto.operation.PickupAddOnResponseDto;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A5). Gọi: luồng B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface BookingHorseService {

    // Loại một con ngựa khỏi đơn: ngựa không đạt khi nhận (GĐ6), kể cả khi nhận tại hub.
    // Bên cài đặt nhả khoang của chuyến và lập báo giá phiên bản mới.
    void exclude(Long bookingHorseId, String reason, Long actorUserId);

    void repricePickup(BookingHorse horse, HorseLeg retry, List<BookingHorse> riding, boolean waivePickup);

    Optional<PickupMissPriceResponseDto> previewPickup(BookingHorse horse, HorseLeg retry, List<BookingHorse> riding,
                                                       boolean waivePickup, boolean exclude);

    List<PickupAddOnResponseDto> pickupAddOns(Long horseId);

    PickupAddOnResponseDto completePickupAddOn(Long horseId, Long serviceId, Instant at);

    void reviseExcludedInsurance(BookingHorse horse);
}
