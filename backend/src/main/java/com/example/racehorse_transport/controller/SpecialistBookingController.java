package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.specialist.*;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.SpecialistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/specialist/bookings")
@PreAuthorize("hasAnyRole('SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class SpecialistBookingController {

    private final SpecialistService specialistService;
    private final JwtUtils jwtUtils;

    private String extractUsername(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtils.validateToken(token)) {
                return jwtUtils.getUsernameFromToken(token);
            }
        }
        return null;
    }

    // =========================================================================
    // FLOW 1: DUYỆT HỒ SƠ NGỰA (VERIFICATION)
    // =========================================================================

    /**
     * 1. Lấy danh sách các đơn chờ thẩm định hồ sơ ngựa
     * GET /api/v1/specialist/bookings/verification
     */
    @GetMapping("/verification")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getVerificationBookings() {
        List<BookingResponse> list = specialistService.getVerificationBookings();
        return ResponseEntity.ok(ApiResponse.success(list, "Lấy danh sách đơn chờ thẩm định hồ sơ ngựa thành công"));
    }

    /**
     * 2. Lấy chi tiết một đơn hàng để thẩm định hoặc làm giấy tờ
     * GET /api/v1/specialist/bookings/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingDetail(
            @PathVariable("id") String id) {
        BookingResponse response = specialistService.getBookingDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết đơn hàng thành công"));
    }

    /**
     * 3. Specialist duyệt đạt hồ sơ ngựa (Approve Horse Profile)
     * POST /api/v1/specialist/bookings/{id}/verify-horse
     */
    @PostMapping("/{id}/verify-horse")
    public ResponseEntity<ApiResponse<BookingResponse>> approveHorseProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody(required = false) ApproveMedicalRequest request) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.approveMedical(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Duyệt hồ sơ ngựa đạt tiêu chuẩn thành công"));
    }

    /**
     * 4. Specialist yêu cầu khách bổ sung hồ sơ ngựa (Request Resubmission)
     * POST /api/v1/specialist/bookings/{id}/request-resubmission
     */
    @PostMapping("/{id}/request-resubmission")
    public ResponseEntity<ApiResponse<BookingResponse>> requestResubmission(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody RequestResubmissionRequest request) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.requestResubmission(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã gửi yêu cầu bổ sung hồ sơ ngựa đến khách hàng"));
    }

    // =========================================================================
    // FLOW 2: THỦ TỤC KIỂM DỊCH & HẢI QUAN CHUYẾN ĐI (CLEARANCE)
    // =========================================================================

    /**
     * 5. Lấy danh sách các đơn trong giai đoạn làm giấy tờ kiểm dịch & hải quan
     * GET /api/v1/specialist/bookings/clearance
     */
    @GetMapping("/clearance")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getClearanceBookings() {
        List<BookingResponse> list = specialistService.getClearanceBookings();
        return ResponseEntity.ok(ApiResponse.success(list, "Lấy danh sách đơn làm thủ tục kiểm dịch thành công"));
    }

    /**
     * 6. Specialist tiếp nhận Vận đơn (sau khi khách cọc) để bắt đầu làm giấy tờ
     * POST /api/v1/specialist/bookings/{id}/accept-waybill
     */
    @PostMapping("/{id}/accept-waybill")
    public ResponseEntity<ApiResponse<BookingResponse>> acceptWaybill(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.acceptWaybill(id, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã tiếp nhận Vận đơn, bắt đầu làm thủ tục kiểm dịch"));
    }

    /**
     * 7. Specialist cập nhật ghi chú hoặc ảnh chụp chứng từ cho từng hạng mục
     * POST /api/v1/specialist/bookings/{id}/clearance-items
     */
    @PostMapping("/{id}/clearance-items")
    public ResponseEntity<ApiResponse<BookingResponse>> updateClearanceItem(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateClearanceItemRequest request) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.updateClearanceItem(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật tiến độ giấy tờ thành công"));
    }

    /**
     * 8. Specialist bổ sung thêm một hạng mục giấy tờ cần làm
     * POST /api/v1/specialist/bookings/{id}/add-clearance-item
     */
    @PostMapping("/{id}/add-clearance-item")
    public ResponseEntity<ApiResponse<BookingResponse>> addClearanceItem(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody AddClearanceItemRequest request) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.addClearanceItem(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã thêm hạng mục giấy tờ thành công"));
    }

    /**
     * 9. Ghi nhận thông quan cho từng cá thể ngựa (tuyến Quốc tế)
     * POST /api/v1/specialist/bookings/{id}/clearance-horse
     */
    @PostMapping("/{id}/clearance-horse")
    public ResponseEntity<ApiResponse<BookingResponse>> markHorseCleared(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody MarkHorseClearedRequest request) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.markHorseCleared(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật trạng thái thông quan của ngựa thành công"));
    }

    /**
     * 10. Hoàn tất toàn bộ giấy tờ kiểm dịch và hải quan -> chuyển đơn sang clearance_done
     * POST /api/v1/specialist/bookings/{id}/complete-clearance
     */
    @PostMapping("/{id}/complete-clearance")
    public ResponseEntity<ApiResponse<BookingResponse>> completeClearance(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.completeClearance(id, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã hoàn tất toàn bộ giấy tờ kiểm dịch & hải quan cho chuyến đi"));
    }

    /**
     * 11. Chuyển đơn xác nhận dịch tễ qua Specialist để duyệt (sau khi kíp xe xác nhận)
     * POST /api/v1/specialist/bookings/{id}/transfer-quarantine
     */
    @PostMapping("/{id}/transfer-quarantine")
    public ResponseEntity<ApiResponse<BookingResponse>> transferForQuarantine(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.transferForQuarantine(id, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã chuyển đơn sang Kiểm dịch viên để duyệt xác nhận dịch tễ"));
    }

    /**
     * 12. Specialist duyệt xác nhận dịch tễ của chuyến đi
     * POST /api/v1/specialist/bookings/{id}/approve-quarantine
     */
    @PostMapping("/{id}/approve-quarantine")
    public ResponseEntity<ApiResponse<BookingResponse>> approveQuarantine(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody(required = false) ApproveMedicalRequest request) {

        String username = extractUsername(authHeader);
        BookingResponse response = specialistService.approveQuarantine(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(response, "Duyệt xác nhận dịch tễ chuyến đi thành công"));
    }
}
