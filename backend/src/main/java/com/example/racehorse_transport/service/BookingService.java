package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.CreateBookingRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;

import java.util.List;

public interface BookingService {
    // Nghiệp vụ Flow 1: Khách hàng tạo yêu cầu đặt chuyến
    BookingResponseDto createBooking(String customerUsername, CreateBookingRequestDto request);

    // Nghiệp vụ Flow 1: Khách hàng xem danh sách đơn của mình
    List<BookingResponseDto> getCustomerBookings(String customerUsername);
    com.example.racehorse_transport.dto.common.PagedResponseDto<BookingResponseDto> getCustomerBookingsPaged(String customerUsername, int page, int size);

    // Nghiệp vụ Flow 1: Xem chi tiết đơn
    BookingResponseDto getBookingById(Integer id, String customerUsername);

    List<com.example.racehorse_transport.dto.booking.CustomerTripTeamResponseDto> getCustomerTripTeam(Integer id, String customerUsername);

    // Khách gửi lại hồ sơ sau khi bổ sung theo yêu cầu thẩm định.
    BookingResponseDto resubmitBooking(String bookingIdentifier, String customerUsername);

    // Nghiệp vụ Flow 1: Manager xem đơn theo trạng thái (chờ tiếp nhận)
    List<BookingResponseDto> getBookingsByStatus(String status);
    com.example.racehorse_transport.dto.common.PagedResponseDto<BookingResponseDto> getBookingsPaged(String status, int page, int size);

    List<BookingResponseDto> getAssignedBookings(String username);

    // Nghiệp vụ Flow 1: Manager tiếp nhận đơn & kích hoạt thẩm định song song (pending_intake -> under_review)
    BookingResponseDto activateBooking(String bookingIdentifier, com.example.racehorse_transport.dto.manager.ActivateBookingRequestDto request);
    BookingResponseDto rejectBooking(String bookingIdentifier, com.example.racehorse_transport.dto.manager.RejectBookingRequestDto request);

    // Nghiệp vụ Phase 2: Coordinator chốt xe & lộ trình
    BookingResponseDto confirmCoordinatorPlan(String bookingIdentifier, com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequestDto request);

    // Nghiệp vụ Phase 3: Manager phân kíp xe, phát hành báo giá 24h, trả lại hồ sơ
    BookingResponseDto assignCrew(String bookingIdentifier, com.example.racehorse_transport.dto.manager.AssignCrewRequestDto request);
    BookingResponseDto sendQuote(String bookingIdentifier, com.example.racehorse_transport.dto.manager.SendQuoteRequestDto request);
    BookingResponseDto sendBack(String bookingIdentifier, com.example.racehorse_transport.dto.manager.SendBackRequestDto request);

    // Nghiệp vụ Phase 4 & 5: Khách hàng duyệt cọc 30%, từ chối báo giá, giải phóng 24h tự động, gửi lại hồ sơ, thanh toán 70% ngày D (qua VNPAY)
    BookingResponseDto resubmitDocuments(String bookingIdentifier, String customerUsername);
    // Áp hiệu ứng nghiệp vụ sau khi cổng thanh toán xác nhận đã nhận tiền
    Booking confirmDepositPaid(Payment payment, String actor);
    Booking confirmBalancePaid(Payment payment, String actor);

    // Kíp xe đã nhận xong ngựa ở điểm đón (chip khớp, bản gốc, biên bản): lúc này khách mới thanh toán 70% còn lại
    boolean isPickupComplete(Booking booking);
    BookingResponseDto rejectQuote(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.RejectQuoteRequestDto request);
    int releaseExpiredQuotations();
    int markOverdueSettlements();

    // Flow 4: Cập nhật tiến độ chuyến xe (check-in, quét chip, thu hồ sơ, xuất phát, an sinh, về HQ)
    BookingResponseDto updateTripProgress(String bookingIdentifier, com.example.racehorse_transport.dto.field.TripUpdateRequestDto request,
            String username, String role);

    // Flow 3-5: Driver/Escort lấy danh sách chuyến được phân công cho mình (filter server-side theo staffCode/username)
    List<BookingResponseDto> getMyFieldBookings(String username, String role);

    // Flow công khai: Tra cứu hành trình đơn hàng bằng mã đơn và 4 số cuối SĐT
    PublicTrackingResponseDto getPublicTracking(String code, String phoneLast4);
}
