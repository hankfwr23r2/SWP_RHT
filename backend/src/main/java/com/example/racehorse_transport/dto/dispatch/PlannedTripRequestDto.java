package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.enums.VehicleTripType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

// Lập lượt đón, giao, chạy thẳng, chuyển xe từ danh sách điểm và ngựa; hệ thống tính giờ.
// startAt: giờ rời điểm đầu, bỏ trống thì hệ thống chọn. vehicleId: bắt buộc khi lập, không cần khi xem trước
public record PlannedTripRequestDto(
        @NotNull VehicleTripType tripType,
        @Positive Long vehicleId,
        @NotNull @Size(min = 2, max = 50) List<@Valid @NotNull PlaceDto> places,
        @NotNull @Size(max = 50) List<@NotNull @Positive Long> bookingHorseIds,
        Instant startAt) {

    public record PlaceDto(@Positive Long locationId, @Positive Long bookingAddressId) {
    }
}
