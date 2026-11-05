package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.payment.ReconcileInsuranceRequest;
import com.example.racehorse_transport.service.PaymentService;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

@RestController
@RequestMapping("/manager/bookings")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class ManagerBookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;

    @PostMapping("/{id}/insurance-reconciliation")
    public ResponseEntity<ApiResponse<String>> reconcileInsurance(@PathVariable String id,
            @jakarta.validation.Valid @RequestBody ReconcileInsuranceRequest request,
            Authentication authentication) {
        paymentService.reconcileInsurance(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("OK", "Đã ghi nhận đối soát bảo hiểm"));
    }


    // 1. API Manager lấy danh sách đơn chờ tiếp nhận (hoặc lọc theo status)
    @GetMapping("/intake")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getIntakeBookings(
            @RequestParam(value = "status", required = false, defaultValue = "pending_intake") String status) {
        List<BookingResponse> bookings = bookingService.getBookingsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(bookings, "Lấy danh sách đơn chờ tiếp nhận thành công"));
    }

    // 2. API Manager xem toàn bộ đơn (hỗ trợ phân trang tùy chọn)
    @GetMapping
    public ResponseEntity<ApiResponse<?>> getAllBookings(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        if (page != null) {
            com.example.racehorse_transport.dto.common.PagedResponse<BookingResponse> paged = bookingService.getBookingsPaged("ALL", page, size);
            return ResponseEntity.ok(ApiResponse.success(paged, "Lấy danh sách toàn bộ đơn hàng thành công"));
        }
        List<BookingResponse> bookings = bookingService.getBookingsByStatus("ALL");
        return ResponseEntity.ok(ApiResponse.success(bookings, "Lấy danh sách toàn bộ đơn hàng thành công"));
    }

    // 3. API Manager phân công kíp xe (1 Driver + 1 Escort cho từng xe)
    @PostMapping("/{id}/assign-crew")
    public ResponseEntity<ApiResponse<BookingResponse>> assignCrew(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.AssignCrewRequest request) {
        BookingResponse response = bookingService.assignCrew(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Phân công kíp xe thành công"));
    }

    // 4. API Manager duyệt và phát hành báo giá.
    @PostMapping("/{id}/send-quote")
    public ResponseEntity<ApiResponse<BookingResponse>> sendQuote(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.SendQuoteRequest request,
            Authentication authentication) {
        // Người phát hành báo giá lấy từ token, không tin body
        request.setBy(authentication.getName());
        BookingResponse response = bookingService.sendQuote(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Phát hành báo giá thành công."));
    }

    // 5. API Manager trả đơn về Specialist hoặc Coordinator
    @PostMapping("/{id}/send-back")
    public ResponseEntity<ApiResponse<BookingResponse>> sendBack(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.SendBackRequest request) {
        BookingResponse response = bookingService.sendBack(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã trả lại hồ sơ đơn hàng"));
    }

    // 6. API Manager tiếp nhận và kích hoạt thẩm định song song (pending_intake -> under_review)
    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<BookingResponse>> activateBooking(
            @PathVariable("id") String id,
            @RequestBody com.example.racehorse_transport.dto.manager.ActivateBookingRequest request) {
        BookingResponse response = bookingService.activateBooking(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Tiếp nhận đơn hàng và kích hoạt thẩm định thành công"));
    }

    // 7. API Manager hoặc Coordinator từ chối đơn hàng
    @PreAuthorize("hasAnyRole('MANAGER', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<BookingResponse>> rejectBooking(
            @PathVariable("id") String id,
            @RequestBody com.example.racehorse_transport.dto.manager.RejectBookingRequest request,
            Authentication authentication) {
        // Người từ chối và vai trò lấy từ token, không tin body: vai trò quyết định đơn ở bước nào được từ chối
        request.setBy(authentication.getName());
        request.setRole("manager");
        BookingResponse response = bookingService.rejectBooking(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã từ chối đơn hàng thành công"));
    }

    // Hủy đơn "Không nhận được ngựa" (kíp xe đã tới điểm đón nhưng không còn ngựa nào khớp hồ sơ): khách mất phần cọc
    @PostMapping("/{id}/cancel-pickup-failed")
    public ResponseEntity<ApiResponse<com.example.racehorse_transport.dto.payment.CancelWithRefundResponse>> cancelPickupFailed(
            @PathVariable("id") String id,
            @RequestBody(required = false) com.example.racehorse_transport.dto.payment.CancelBookingRequest request,
            Authentication authentication) {
        var response = paymentService.cancelPickupFailed(id, request != null ? request.getReason() : null, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    // 8. API Kích hoạt quét & giải phóng xe/kíp xe quá hạn 24h (Dành cho Quản lý / Batch Job)
    @PostMapping("/maintenance/release-expired")
    public ResponseEntity<ApiResponse<Integer>> releaseExpired() {
        int count = bookingService.releaseExpiredQuotations();
        return ResponseEntity.ok(ApiResponse.success(count, "Đã quét và giải phóng thành công " + count + " đơn hết hạn"));
    }
}
