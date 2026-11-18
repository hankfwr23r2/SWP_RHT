package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.HorseInsuranceDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.specialist.*;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
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

    @GetMapping("/insurance")
    public ResponseEntity<ApiResponseDto<List<BookingResponseDto>>> getInsuranceBookings(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        return ResponseEntity.ok(ApiResponseDto.success(specialistService.getInsuranceBookings(extractUsername(authHeader)), "Danh sách bảo hiểm được phân công"));
    }

    @PutMapping("/{id}/horses/{horseId}/insurance")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> updateInsurance(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable String id, @PathVariable String horseId,
            @RequestBody HorseInsuranceDto request) {
        return ResponseEntity.ok(ApiResponseDto.success(specialistService.updateInsurance(id, horseId, request, extractUsername(authHeader)), "Đã lưu bảo hiểm"));
    }


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
    public ResponseEntity<ApiResponseDto<List<BookingResponseDto>>> getVerificationBookings(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        List<BookingResponseDto> list = specialistService.getVerificationBookings(extractUsername(authHeader));
        return ResponseEntity.ok(ApiResponseDto.success(list, "Lấy danh sách đơn chờ thẩm định hồ sơ ngựa thành công"));
    }

    /**
     * 2. Lấy chi tiết một đơn hàng để thẩm định hoặc làm giấy tờ
     * GET /api/v1/specialist/bookings/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> getBookingDetail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id) {
        BookingResponseDto response = specialistService.getBookingDetail(id, extractUsername(authHeader));
        return ResponseEntity.ok(ApiResponseDto.success(response, "Lấy chi tiết đơn hàng thành công"));
    }

    /**
     * 3. Specialist duyệt đạt hồ sơ ngựa (Approve Horse Profile)
     * POST /api/v1/specialist/bookings/{id}/verify-horse
     */
    @PostMapping("/{id}/verify-horse")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> approveHorseProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody(required = false) ApproveMedicalRequestDto request) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.approveMedical(id, request, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Duyệt hồ sơ ngựa đạt tiêu chuẩn thành công"));
    }

    /**
     * 4. Specialist yêu cầu khách bổ sung hồ sơ ngựa (Request Resubmission)
     * POST /api/v1/specialist/bookings/{id}/request-resubmission
     */
    @PostMapping("/{id}/request-resubmission")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> requestResubmission(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody RequestResubmissionRequestDto request) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.requestResubmission(id, request, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã gửi yêu cầu bổ sung hồ sơ ngựa đến khách hàng"));
    }

    // =========================================================================
    // FLOW 2: THỦ TỤC KIỂM DỊCH & HẢI QUAN CHUYẾN ĐI (CLEARANCE)
    // =========================================================================

    /**
     * 5. Lấy danh sách các đơn trong giai đoạn làm giấy tờ kiểm dịch & hải quan
     * GET /api/v1/specialist/bookings/clearance
     */
    @GetMapping("/clearance")
    public ResponseEntity<ApiResponseDto<List<BookingResponseDto>>> getClearanceBookings(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        List<BookingResponseDto> list = specialistService.getClearanceBookings(extractUsername(authHeader));
        return ResponseEntity.ok(ApiResponseDto.success(list, "Lấy danh sách đơn làm thủ tục kiểm dịch thành công"));
    }

    /**
     * 6. Specialist tiếp nhận Vận đơn (sau khi khách cọc) để bắt đầu làm giấy tờ
     * POST /api/v1/specialist/bookings/{id}/accept-waybill
     */
    @PostMapping("/{id}/accept-waybill")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> acceptWaybill(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.acceptWaybill(id, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã tiếp nhận Vận đơn, bắt đầu làm thủ tục kiểm dịch"));
    }

    /**
     * 7. Specialist cập nhật ghi chú hoặc ảnh chụp chứng từ cho từng hạng mục
     * POST /api/v1/specialist/bookings/{id}/clearance-items
     */
    @PutMapping("/{id}/clearance-items")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> updateClearanceItems(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateClearanceItemsRequestDto request) {
        BookingResponseDto response = specialistService.updateClearanceItems(id, request, extractUsername(authHeader));
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã lưu cập nhật giấy tờ"));
    }

    @PostMapping("/{id}/clearance-items")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> updateClearanceItem(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateClearanceItemRequestDto request) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.updateClearanceItem(id, request, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Cập nhật tiến độ giấy tờ thành công"));
    }

    /**
     * 8. Specialist bổ sung thêm một hạng mục giấy tờ cần làm
     * POST /api/v1/specialist/bookings/{id}/add-clearance-item
     */
    @PostMapping("/{id}/add-clearance-item")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> addClearanceItem(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody AddClearanceItemRequestDto request) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.addClearanceItem(id, request, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã thêm hạng mục giấy tờ thành công"));
    }

    /**
     * 9. Ghi nhận thông quan cho từng cá thể ngựa (tuyến Quốc tế)
     * POST /api/v1/specialist/bookings/{id}/clearance-horse
     */
    @PostMapping("/{id}/clearance-horse")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> markHorseCleared(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @Valid @RequestBody MarkHorseClearedRequestDto request) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.markHorseCleared(id, request, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Cập nhật trạng thái thông quan của ngựa thành công"));
    }

    /**
     * 10. Hoàn tất toàn bộ giấy tờ kiểm dịch và hải quan -> chuyển đơn sang clearance_done
     * POST /api/v1/specialist/bookings/{id}/complete-clearance
     */
    @PostMapping("/{id}/complete-clearance")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> completeClearance(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.completeClearance(id, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã hoàn tất toàn bộ giấy tờ kiểm dịch & hải quan cho chuyến đi"));
    }

    /**
     * 11. Chuyển đơn xác nhận dịch tễ qua Specialist để duyệt (sau khi kíp xe xác nhận)
     * POST /api/v1/specialist/bookings/{id}/transfer-quarantine
     */
    @PostMapping("/{id}/transfer-quarantine")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> transferForQuarantine(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.transferForQuarantine(id, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Đã chuyển đơn sang Chuyên viên để duyệt xác nhận dịch tễ"));
    }

    /**
     * 12. Specialist duyệt xác nhận dịch tễ của chuyến đi
     * POST /api/v1/specialist/bookings/{id}/approve-quarantine
     */
    @PostMapping("/{id}/approve-quarantine")
    public ResponseEntity<ApiResponseDto<BookingResponseDto>> approveQuarantine(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @PathVariable("id") String id,
            @RequestBody(required = false) ApproveMedicalRequestDto request) {

        String username = extractUsername(authHeader);
        BookingResponseDto response = specialistService.approveQuarantine(id, request, username);
        return ResponseEntity.ok(ApiResponseDto.success(response, "Duyệt xác nhận dịch tễ chuyến đi thành công"));
    }
}
