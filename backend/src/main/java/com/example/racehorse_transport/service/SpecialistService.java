package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.specialist.*;

import java.util.List;

public interface SpecialistService {

    // =========================================================================
    // FLOW 1: DUYỆT HỒ SƠ NGỰA (VERIFICATION)
    // =========================================================================

    /**
     * Lấy danh sách các đơn cần duyệt hồ sơ ngựa (trạng thái: under_review, pending_intake, etc.)
     */
    List<BookingResponse> getVerificationBookings();

    /**
     * Lấy chi tiết một đơn hàng để kiểm tra hồ sơ ngựa
     */
    BookingResponse getBookingDetail(String bookingIdentifier);

    /**
     * Specialist duyệt hồ sơ ngựa là ĐẠT (Approve Medical/Horse Profile)
     */
    BookingResponse approveMedical(String bookingIdentifier, ApproveMedicalRequest request, String username);

    /**
     * Specialist từ chối / yêu cầu khách hàng bổ sung lại hồ sơ ngựa (Request Resubmission)
     */
    BookingResponse requestResubmission(String bookingIdentifier, RequestResubmissionRequest request, String username);

    // =========================================================================
    // FLOW 2: THỦ TỤC KIỂM DỊCH & HẢI QUAN CHUYẾN ĐI (CLEARANCE & LEGAL)
    // =========================================================================

    /**
     * Lấy danh sách các đơn đang trong giai đoạn làm thủ tục kiểm dịch & hải quan (waybill_issued, clearance_in_progress, clearance_done)
     */
    List<BookingResponse> getClearanceBookings();

    /**
     * Specialist tiếp nhận Vận đơn sau khi khách đã cọc -> Chuyển sang clearance_in_progress
     */
    BookingResponse acceptWaybill(String bookingIdentifier, String username);

    /**
     * Specialist cập nhật ghi chú hoặc ảnh chụp chứng từ cho từng hạng mục (tự động chuyển Đã nộp / Chưa nộp)
     */
    BookingResponse updateClearanceItem(String bookingIdentifier, UpdateClearanceItemRequest request, String username);

    /**
     * Specialist bổ sung thêm một hạng mục giấy tờ cần làm cho chuyến đi
     */
    BookingResponse addClearanceItem(String bookingIdentifier, AddClearanceItemRequest request, String username);

    /**
     * Ghi nhận thông quan cho từng cá thể ngựa (tuyến Quốc tế)
     */
    BookingResponse markHorseCleared(String bookingIdentifier, MarkHorseClearedRequest request, String username);

    /**
     * Hoàn tất toàn bộ giấy tờ kiểm dịch & hải quan -> Chuyển đơn sang clearance_done
     */
    BookingResponse completeClearance(String bookingIdentifier, String username);
}
