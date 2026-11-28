package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.enums.DepartureAvailability;
import com.example.racehorse_transport.dto.departure.DepartureDetailResponseDto;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.LocalDate;
import java.util.List;

// Chuyến cụ thể theo ngày. Chủ sở hữu bảng departure (kể cả cột dẫn xuất booked_stall_count): luồng A (A1).
// reserve/release là hợp đồng gọi chéo (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
public interface DepartureService {
    long sellableCapacity(Departure departure);
    DepartureAvailability availability(Departure departure, long spareCapacity);
    DepartureDetailResponseDto detail(Long departureId);
    DepartureDetailResponseDto removeVehicle(Long departureId, Long allotmentId, String reason, String username);

    Departure lockForLinehaulPlanning(Long departureId);

    void linehaulDeparted(Long departureId);

    void linehaulCompleted(Long departureId);

    // Giữ khoang trên chuyến. Khóa dòng departure (SELECT ... FOR UPDATE) rồi mới cộng booked_stall_count;
    // vượt capacity_stalls thì ném lỗi nghiệp vụ.
    void reserve(Long departureId, int stalls);

    // Nhả khoang: hủy đơn, từ chối, báo giá hết hạn, loại ngựa, đổi chuyến
    void release(Long departureId, int stalls);

    void reservePrivate(Long departureId, com.example.racehorse_transport.entity.Booking booking, int stalls);

    void releasePrivate(Long departureId, Long bookingId);

    // Sinh chuyến từ các lịch đang chạy cho DEPARTURE_GENERATION_WEEKS tuần tới. Trả về số chuyến mới.
    int generateFromSchedules();

    // Đóng các chuyến đang nhận đơn mà đã qua giờ chốt. Trả về số chuyến đã đóng.
    int closePastCutoff();

    // routeId, status null nghĩa là không lọc
    List<DepartureResponseDto> departures(Long routeId, DepartureStatus status, LocalDate fromDate, LocalDate toDate);

    // Hủy chuyến chưa chạy; các đơn trên chuyến chuyển sang SCHEDULE_CHANGE_REQUIRED kèm lý do
    DepartureResponseDto cancel(Long departureId, String reason, String actorUsername);

    void cancelForDelay(Long departureId, String reason);
}
