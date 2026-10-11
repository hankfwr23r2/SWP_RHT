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

    // Nghiệp vụ Flow 1: Manager xem đơn theo trạng thái (chờ tiếp nhận)
    List<BookingResponse> getBookingsByStatus(String status);

    // Nghiệp vụ Phase 2: Coordinator chốt xe & lộ trình
    BookingResponse confirmCoordinatorPlan(String bookingIdentifier, com.example.racehorse_transport.dto.coordinator.CoordinatorPlanRequest request);

    // Nghiệp vụ Phase 3: Manager phân kíp xe, phát hành báo giá 48h, trả lại hồ sơ
    BookingResponse assignCrew(String bookingIdentifier, com.example.racehorse_transport.dto.manager.AssignCrewRequest request);
    BookingResponse sendQuote(String bookingIdentifier, com.example.racehorse_transport.dto.manager.SendQuoteRequest request);
    BookingResponse sendBack(String bookingIdentifier, com.example.racehorse_transport.dto.manager.SendBackRequest request);

    // Nghiệp vụ Phase 4 & 5: Khách hàng duyệt cọc 30%, từ chối báo giá, giải phóng 48h tự động
    BookingResponse payDeposit(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.PayDepositRequest request);
    BookingResponse rejectQuote(String bookingIdentifier, String customerUsername, com.example.racehorse_transport.dto.booking.RejectQuoteRequest request);
    int releaseExpiredQuotations();

    // Dev 4: Bàn giao, ký nhận, kết thúc đơn hàng (Flow 6)
    boolean completeBookingDelivery(Integer bookingId, String recipientName, String recipientSignature, String note);
}

