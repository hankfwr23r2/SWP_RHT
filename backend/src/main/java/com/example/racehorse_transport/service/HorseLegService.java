package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.DispatchBookingResponseDto;
import com.example.racehorse_transport.dto.dispatch.HorseLegRequestDto;
import com.example.racehorse_transport.dto.dispatch.TripHorseResponseDto;
import java.time.LocalDate;
import java.util.List;

// Chặng của từng con ngựa trên lượt xe. Chủ sở hữu horse_leg: luồng B (B1).
public interface HorseLegService {

    // Đơn đã cọc hoặc đang vận hành có ngày đón hoặc ngày chuyến trục trong [from, to], kèm chặng đã lập của từng con
    List<DispatchBookingResponseDto> dispatchableBookings(LocalDate from, LocalDate to);

    // Ngựa đang trên lượt xe (chặng chưa hủy, chưa thất bại)
    List<TripHorseResponseDto> horsesOnTrip(Long vehicleTripId);

    // Gán ngựa vào lượt xe, tạo chặng mới nối tiếp chuỗi chặng của con ngựa
    List<TripHorseResponseDto> assign(Long vehicleTripId, HorseLegRequestDto request, String username);

    // Gỡ chặng chưa chạy, chỉ khi đó là chặng cuối của con ngựa
    List<TripHorseResponseDto> cancel(Long horseLegId);

    // Hợp đồng gọi chéo (KE_HOACH mục 3.3). Cài đặt: luồng B. Gọi: luồng A khi đơn rời chuyến (báo giấy chậm, hủy chuyến,
    // đổi chuyến, hủy đơn). Hủy mọi chặng còn PLANNED của đơn, kể cả chặng đại lý; nhả chỗ trên lượt xe và vị trí khoang. Trả về số chặng đã hủy
    int cancelPlannedLegsOfBooking(Long bookingId);
}
