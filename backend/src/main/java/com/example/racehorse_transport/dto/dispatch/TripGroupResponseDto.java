package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.enums.TripStopType;
import java.time.Instant;
import java.util.List;

// Một nhóm đơn hệ thống gợi ý gom vào một lượt đón hoặc giao. Điều phối duyệt: lập lượt xe với các điểm dừng gợi ý rồi xếp ngựa.
// overLimit: con ở trên xe lâu nhất vượt giới hạn giờ lái một chiều. bookings theo thứ tự xe ghé. routeKm, hours: ước lượng từ đường chim bay, để so sánh và chọn, không thay dẫn đường thật
public record TripGroupResponseDto(Integer seats, Double routeKm, Double hours, Boolean overLimit, List<GroupBookingDto> bookings, List<SuggestedStopDto> stops) {

    public record GroupBookingDto(Long bookingId, String bookingCode, Long bookingAddressId, String addressLine, List<GroupHorseDto> horses) {}

    public record GroupHorseDto(Long bookingHorseId, String horseName, Integer seats) {}

    // Cùng dạng với điểm dừng khi lập lượt xe (TripStopRequestDto)
    public record SuggestedStopDto(Long locationId, Long bookingAddressId, TripStopType stopType, Instant plannedArrivalAt, Instant plannedDepartureAt) {}
}
