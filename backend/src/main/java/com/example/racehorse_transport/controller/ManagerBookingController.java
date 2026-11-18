package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.payment.ReconcileInsuranceRequestDto;
import com.example.racehorse_transport.service.PaymentService;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
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
    public ResponseEntity<ApiResponseDto<String>> reconcileInsurance(@PathVariable String id,
            @jakarta.validation.Valid @RequestBody ReconcileInsuranceRequestDto request,
            Authentication authentication) {
        paymentService.reconcileInsurance(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success("OK", "Đã ghi nhận đối soát bảo hiểm"));
    }


    // 1. API Manager lấy danh sách đơn chờ tiếp nhận (hoặc lọc theo status)
    @GetMapping("/intake")
    public ResponseEntity<ApiResponseDto<List<BookingResponseDto>>> getIntakeBookings(
            @RequestParam(value = "status", required = false, defaultValue = "pending_intake") String status) {
        List<BookingResponseDto> bookings = bookingService.getBookingsByStatus(status);
        return ResponseEntity.ok(ApiResponseDto.success(bookings, "Lấy danh sách đơn chờ tiếp nhận thành công"));
    }

    // 2. API Manager xem toàn bộ đơn (hỗ trợ phân trang tùy chọn)
    @GetMapping
    public ResponseEntity<ApiResponseDto<?>> getAllBookings(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        if (page != null) {
            com.example.racehorse_transport.dto.common.PagedResponseDto<BookingResponseDto> paged = bookingService.getBookingsPaged("ALL", page, size);
            return ResponseEntity.ok(ApiResponseDto.success(paged, "Lấy danh sách toàn bộ đơn hàng thành công"));
        }
        List<BookingResponseDto> bookings = bookingService.getBookingsByStatus("ALL");
        return ResponseEntity.ok(ApiResponseDto.success(bookings, "Lấy danh sách toàn bộ đơn hàng thành công"));
    }

    // 3. API Manager phân công kíp xe (1 Driver + 1 Escort cho từng xe)
    @PostMapping("/{id}/assign-crew")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> assignCrew(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.AssignCrewRequestDto request) {
        BookingResponseDto response = bookingService.assignCrew(id, request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Phân công kíp xe thành công"));
    }

    // 4. API Manager duyệt và phát hành báo giá.
    @PostMapping("/{id}/send-quote")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> sendQuote(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.SendQuoteRequestDto request,
            Authentication authentication) {
        // Người phát hành báo giá lấy từ token, không tin body
        request.setBy(authentication.getName());
        BookingResponseDto response = bookingService.sendQuote(id, request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Phát hành báo giá thành công."));
    }

    // 5. API Manager trả đơn về Specialist hoặc Coordinator
    @PostMapping("/{id}/send-back")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> sendBack(
            @PathVariable("id") String id,
            @jakarta.validation.Valid @RequestBody com.example.racehorse_transport.dto.manager.SendBackRequestDto request) {
        BookingResponseDto response = bookingService.sendBack(id, request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã trả lại hồ sơ đơn hàng"));
    }

    // 6. API Manager tiếp nhận và kích hoạt thẩm định song song (pending_intake -> under_review)
    @PostMapping("/{id}/activate")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> activateBooking(
            @PathVariable("id") String id,
            @RequestBody com.example.racehorse_transport.dto.manager.ActivateBookingRequestDto request) {
        BookingResponseDto response = bookingService.activateBooking(id, request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Tiếp nhận đơn hàng và kích hoạt thẩm định thành công"));
    }

    // 7. API Manager hoặc Coordinator từ chối đơn hàng
    @PreAuthorize("hasAnyRole('MANAGER', 'COORDINATOR', 'ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> rejectBooking(
            @PathVariable("id") String id,
            @RequestBody com.example.racehorse_transport.dto.manager.RejectBookingRequestDto request,
            Authentication authentication) {
        // Người từ chối và vai trò lấy từ token, không tin body: vai trò quyết định đơn ở bước nào được từ chối
        request.setBy(authentication.getName());
        request.setRole("manager");
        BookingResponseDto response = bookingService.rejectBooking(id, request);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã từ chối đơn hàng thành công"));
    }

    // Hủy đơn "Không nhận được ngựa" (kíp xe đã tới điểm đón nhưng không còn ngựa nào khớp hồ sơ): khách mất phần cọc
    @PostMapping("/{id}/cancel-pickup-failed")
    public ResponseEntity<ApiResponseDto<com.example.racehorse_transport.dto.payment.CancelWithRefundResponseDto>> cancelPickupFailed(
            @PathVariable("id") String id,
            @RequestBody(required = false) com.example.racehorse_transport.dto.payment.CancelBookingRequestDto request,
            Authentication authentication) {
        var response = paymentService.cancelPickupFailed(id, request != null ? request.getReason() : null, authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(response, response.getMessage()));
    }

    // Quản lý hủy chuyến đã cọc: lỗi do công ty thì hoàn 100%, lỗi do khách thì không hoàn (mất cọc, và 70% nếu đã trả).
    // Ngựa đã trên xe thì đơn sang "Chờ trả ngựa" cho tới khi tài xế trả ngựa và hồ sơ gốc có biên bản (RETURN_HORSES).
    @PostMapping("/{id}/cancel-trip")
    public ResponseEntity<ApiResponseDto<com.example.racehorse_transport.dto.payment.CancelWithRefundResponseDto>> cancelTrip(
            @PathVariable("id") String id,
            @RequestBody com.example.racehorse_transport.dto.payment.CancelTripRequestDto request,
            Authentication authentication) {
        var response = paymentService.cancelTrip(id, request.getFault(), request.getReason(), authentication.getName());
        return ResponseEntity.ok(ApiResponseDto.success(response, response.getMessage()));
    }

    // 8. API Kích hoạt quét & giải phóng xe/kíp xe quá hạn 24h (Dành cho Quản lý / Batch Job)
    @PostMapping("/maintenance/release-expired")
    public ResponseEntity<ApiResponseDto<Integer>> releaseExpired() {
        int count = bookingService.releaseExpiredQuotations();
        return ResponseEntity.ok(ApiResponseDto.success(count, "Đã quét và giải phóng thành công " + count + " đơn hết hạn"));
    }
}
