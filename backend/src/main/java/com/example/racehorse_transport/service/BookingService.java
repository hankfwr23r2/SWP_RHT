package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.CreateBookingRequest;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.entity.Booking;

import java.util.List;
import java.util.Optional;

public interface BookingService {
    List<Booking> findAll();
    Optional<Booking> findById(Integer id);
    Booking save(Booking entity);
    void deleteById(Integer id);

    // Nghiệp vụ Flow 1: Khách hàng tạo yêu cầu đặt chuyến
    BookingResponse createBooking(String customerUsername, CreateBookingRequest request);

    // Nghiệp vụ Flow 1: Khách hàng xem danh sách đơn của mình
    List<BookingResponse> getCustomerBookings(String customerUsername);

    // Nghiệp vụ Flow 1: Xem chi tiết đơn
    BookingResponse getBookingById(Integer id, String customerUsername);

    // Khách gửi lại hồ sơ sau khi bổ sung theo yêu cầu thẩm định.
    BookingResponse resubmitBooking(String bookingIdentifier, String customerUsername);

    // Nghiệp vụ Flow 1: Manager xem đơn theo trạng thái (chờ tiếp nhận)
    List<BookingResponse> getBookingsByStatus(String status);

    List<BookingResponse> getAssignedBookings(String username);

    // Nghiệp vụ Flow 1: Manager tiếp nhận đơn & kích hoạt thẩm định song song (pending_intake -> under_review)
    BookingResponse activateBooking(String bookingIdentifier, com.example.racehorse_transport.dto.manager.ActivateBookingRequest request);
    BookingResponse rejectBooking(String bookingIdentifier, com.example.racehorse_transport.dto.manager.RejectBookingRequest request);

    // Nghiệp vụ Phase 2: Coordinator chốt xe & lộ trình
    BookingResponse confirmCoordinatorPlan(String bookingIdentifier, com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest request);

    // Nghiệp vụ Phase 3: Manager phân kíp xe, phát hành báo giá 48h, trả lại hồ sơ
    BookingResponse assignCrew(String bookingIdentifier, com.example.racehorse_transport.dto.manager.AssignCrewRequest request);
    BookingResponse sendQuote(String bookingIdentifier, com.example.racehorse_transport.dto.manager.SendQuoteRequest request);
    BookingResponse sendBack(String bookingIdentifier, com.example.racehorse_transport.dto.manager.SendBackRequest request);

    // Nghiệp vụ Phase 4 & 5: Khách hàng duyệt cọc 30%, từ chối báo giá, giải phóng 48h tự động, gửi lại hồ sơ, thanh toán 70% ngày D
    BookingResponse resubmitDocuments(String bookingIdentifier, String customerUsername);
    BookingResponse payDeposit(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.PayDepositRequest request);
    BookingResponse payBalance(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.PayBalanceRequest request);
    BookingResponse rejectQuote(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.RejectQuoteRequest request);
    int releaseExpiredQuotations();
    int markOverdueSettlements();

    // Dev 4: Bàn giao, ký nhận, kết thúc đơn hàng (Flow 6)
    boolean completeBookingDelivery(Integer bookingId, String recipientName, String recipientSignature, String note);

    // Flow 4: Cập nhật tiến độ chuyến xe (check-in, quét chip, thu hồ sơ, xuất phát, an sinh, về HQ)
    BookingResponse updateTripProgress(String bookingIdentifier, com.example.racehorse_transport.dto.field.TripUpdateRequest request);

    // Flow 3-5: Driver/Escort lấy danh sách chuyến được phân công cho mình (filter server-side theo staffCode/username)
    List<BookingResponse> getMyFieldBookings(String username, String role);
}
