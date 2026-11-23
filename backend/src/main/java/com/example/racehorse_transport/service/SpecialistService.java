package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.HorseInsuranceDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.specialist.*;

import java.util.List;

public interface SpecialistService {
    List<BookingResponseDto> getInsuranceBookings(String username);
    BookingResponseDto updateInsurance(String id, String horseId, HorseInsuranceDto request, String username);


    // =========================================================================
    // FLOW 1: DUYỆT HỒ SƠ NGỰA (VERIFICATION)
    // =========================================================================

    /**
     * Lấy danh sách các đơn cần duyệt hồ sơ ngựa (trạng thái: under_review, pending_intake, etc.)
     */
    List<BookingResponseDto> getVerificationBookings(String username);

    /**
     * Lấy chi tiết một đơn hàng để kiểm tra hồ sơ ngựa
     */
    BookingResponseDto getBookingDetail(String bookingIdentifier, String username);

    /**
     * Specialist duyệt hồ sơ ngựa là ĐẠT (Approve Medical/Horse Profile)
     */
    BookingResponseDto approveMedical(String bookingIdentifier, ApproveMedicalRequestDto request, String username);

    /**
     * Specialist từ chối / yêu cầu khách hàng bổ sung lại hồ sơ ngựa (Request Resubmission)
     */
    BookingResponseDto requestResubmission(String bookingIdentifier, RequestResubmissionRequestDto request, String username);

    // =========================================================================
    // FLOW 2: THỦ TỤC KIỂM DỊCH & HẢI QUAN CHUYẾN ĐI (CLEARANCE & LEGAL)
    // =========================================================================

    /**
     * Lấy danh sách các đơn đang trong giai đoạn làm thủ tục kiểm dịch & hải quan (waybill_issued, clearance_in_progress, clearance_done)
     */
    List<BookingResponseDto> getClearanceBookings(String username);

    /**
     * Specialist tiếp nhận Vận đơn sau khi khách đã cọc -> Chuyển sang clearance_in_progress
     */
    BookingResponseDto acceptWaybill(String bookingIdentifier, String username);

    /**
     * Specialist cập nhật ghi chú hoặc ảnh chụp chứng từ cho từng hạng mục (tự động chuyển Đã nộp / Chưa nộp)
     */
    BookingResponseDto updateClearanceItem(String bookingIdentifier, UpdateClearanceItemRequestDto request, String username);
    BookingResponseDto updateClearanceItems(String bookingIdentifier, UpdateClearanceItemsRequestDto request, String username);

    /**
     * Specialist bổ sung thêm một hạng mục giấy tờ cần làm cho chuyến đi
     */
    BookingResponseDto addClearanceItem(String bookingIdentifier, AddClearanceItemRequestDto request, String username);

    /**
     * Ghi nhận thông quan cho từng cá thể ngựa (tuyến Quốc tế)
     */
    BookingResponseDto markHorseCleared(String bookingIdentifier, MarkHorseClearedRequestDto request, String username);

    /**
     * Hoàn tất toàn bộ giấy tờ kiểm dịch & hải quan -> Chuyển đơn sang clearance_done
     */
    BookingResponseDto completeClearance(String bookingIdentifier, String username);

    /**
     * Chuyển đơn xác nhận dịch tễ qua Specialist để duyệt (sau khi kíp xe xác nhận)
     */
    BookingResponseDto transferForQuarantine(String bookingIdentifier, String username);

    /**
     * Specialist duyệt xác nhận dịch tễ của chuyến đi
     */
    BookingResponseDto approveQuarantine(String bookingIdentifier, ApproveMedicalRequestDto request, String username);
}
