package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.PublicTrackingResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;

import java.util.List;

public interface BookingService {
    // Nghiệp vụ Flow 1: Khách hàng tạo yêu cầu đặt chuyến
    BookingResponse createBooking(String customerUsername, CreateBookingRequest request);

    // Nghiệp vụ Flow 1: Khách hàng xem danh sách đơn của mình
    List<BookingResponse> getCustomerBookings(String customerUsername);
    com.example.racehorse_transport.dto.common.PagedResponse<BookingResponse> getCustomerBookingsPaged(String customerUsername, int page, int size);

    // Nghiệp vụ Flow 1: Xem chi tiết đơn
    BookingResponse getBookingById(Integer id, String customerUsername);

    List<com.example.racehorse_transport.dto.booking.CustomerTripTeamResponse> getCustomerTripTeam(Integer id, String customerUsername);

    // Khách gửi lại hồ sơ sau khi bổ sung theo yêu cầu thẩm định.
    BookingResponse resubmitBooking(String bookingIdentifier, String customerUsername);

    // Nghiệp vụ Flow 1: Manager xem đơn theo trạng thái (chờ tiếp nhận)
    List<BookingResponse> getBookingsByStatus(String status);
    com.example.racehorse_transport.dto.common.PagedResponse<BookingResponse> getBookingsPaged(String status, int page, int size);

    List<BookingResponse> getAssignedBookings(String username);

    // Nghiệp vụ Flow 1: Manager tiếp nhận đơn & kích hoạt thẩm định song song (pending_intake -> under_review)
    BookingResponse activateBooking(String bookingIdentifier, com.example.racehorse_transport.dto.manager.ActivateBookingRequest request);
    BookingResponse rejectBooking(String bookingIdentifier, com.example.racehorse_transport.dto.manager.RejectBookingRequest request);

    // Nghiệp vụ Phase 2: Coordinator chốt xe & lộ trình
    BookingResponse confirmCoordinatorPlan(String bookingIdentifier, com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest request);

    // Nghiệp vụ Phase 3: Manager phân kíp xe, phát hành báo giá 24h, trả lại hồ sơ
    BookingResponse assignCrew(String bookingIdentifier, com.example.racehorse_transport.dto.manager.AssignCrewRequest request);
    BookingResponse sendQuote(String bookingIdentifier, com.example.racehorse_transport.dto.manager.SendQuoteRequest request);
    BookingResponse sendBack(String bookingIdentifier, com.example.racehorse_transport.dto.manager.SendBackRequest request);

    // Nghiệp vụ Phase 4 & 5: Khách hàng duyệt cọc 30%, từ chối báo giá, giải phóng 24h tự động, gửi lại hồ sơ, thanh toán 70% ngày D (qua VNPAY)
    BookingResponse resubmitDocuments(String bookingIdentifier, String customerUsername);
    // Áp hiệu ứng nghiệp vụ sau khi cổng thanh toán xác nhận đã nhận tiền
    Booking confirmDepositPaid(Payment payment, String actor);
    Booking confirmBalancePaid(Payment payment, String actor);
    BookingResponse rejectQuote(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.RejectQuoteRequest request);
    int releaseExpiredQuotations();
    int markOverdueSettlements();

    // Flow 4: Cập nhật tiến độ chuyến xe (check-in, quét chip, thu hồ sơ, xuất phát, an sinh, về HQ)
    BookingResponse updateTripProgress(String bookingIdentifier, com.example.racehorse_transport.dto.field.TripUpdateRequest request,
            String username, String role);

    // Flow 3-5: Driver/Escort lấy danh sách chuyến được phân công cho mình (filter server-side theo staffCode/username)
    List<BookingResponse> getMyFieldBookings(String username, String role);

    // Flow công khai: Tra cứu hành trình đơn hàng bằng mã đơn và 4 số cuối SĐT
    PublicTrackingResponse getPublicTracking(String code, String phoneLast4);
}
