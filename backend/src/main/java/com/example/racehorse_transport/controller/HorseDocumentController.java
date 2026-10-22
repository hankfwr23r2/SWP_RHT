package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.document.HorseDocumentResponse;
import com.example.racehorse_transport.dto.document.ReviewHorseDocumentRequest;
import com.example.racehorse_transport.entity.HorseDocument;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.HorseDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Controller xử lý nghiệp vụ Hồ sơ Ngựa (Dev 2 - Domain: Document & Finance).
 *
 * Các API cốt lõi:
 *  POST /api/v1/horse-documents/upload              - Khách hàng upload hồ sơ
 *  PUT  /api/v1/horse-documents/{id}/review         - Chuyên viên duyệt / từ chối
 *  GET  /api/v1/horse-documents/horse/{horseId}     - Xem hồ sơ của một con ngựa
 *  GET  /api/v1/horse-documents/pending             - Danh sách hồ sơ chờ duyệt
 *  GET  /api/v1/horse-documents/{id}               - Chi tiết một hồ sơ
 */
@RestController
@RequestMapping("/horse-documents")
@RequiredArgsConstructor
public class HorseDocumentController {

    private final HorseDocumentService horseDocumentService;
    private final HorseRepository horseRepository;

    /**
     * Upload hồ sơ ngựa (Flow 2 - Bước 1).
     * Khách hàng tải lên giấy tờ ngựa để chuyên viên xét duyệt.
     *
     * POST /api/v1/horse-documents/upload
     * Content-Type: multipart/form-data
     * Params:
     *   - file         : File ảnh (JPG/PNG) hoặc PDF
     *   - horseId      : ID con ngựa
     *   - documentType : PASSPORT | VACCINATION | BLOOD_TEST | HEALTH_CERT | OTHER
     */
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'ADMIN')")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HorseDocumentResponse>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("horseId") Integer horseId,
            @RequestParam(value = "documentType", required = false, defaultValue = "OTHER") String documentType,
            Authentication authentication) {

        assertHorseAccess(horseId, authentication);

        HorseDocumentResponse response = horseDocumentService.uploadHorseDocument(
                horseId, documentType, file);

        return ResponseEntity.ok(ApiResponse.success(response,
                "Upload hồ sơ thành công! Hồ sơ đang chờ chuyên viên xét duyệt."));
    }

    /**
     * Chuyên viên duyệt hoặc từ chối hồ sơ (Flow 2 - Bước 2).
     *
     * PUT /api/v1/horse-documents/{id}/review
     * Body JSON:
     * {
     *   "approved": true,           // true = Duyệt, false = Từ chối
     *   "note": "Hồ sơ hợp lệ",    // Ghi chú (bắt buộc khi từ chối)
     *   "reviewerName": "Nguyen A"  // Tên chuyên viên
     * }
     */
    @PreAuthorize("hasAnyRole('SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN')")
    @PutMapping("/{id}/review")
    public ResponseEntity<ApiResponse<HorseDocumentResponse>> reviewDocument(
            @PathVariable Integer id,
            @RequestBody ReviewHorseDocumentRequest request) {

        HorseDocumentResponse response = horseDocumentService.reviewDocument(id, request);

        String message = request.isApproved()
                ? "Hồ sơ đã được DUYỆT thành công!"
                : "Hồ sơ đã bị TỪ CHỐI. Khách hàng cần bổ sung lại.";

        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    /**
     * Lấy toàn bộ hồ sơ của một con ngựa (mọi trạng thái).
     *
     * GET /api/v1/horse-documents/horse/{horseId}
     */
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'COORDINATOR', 'DRIVER', 'ESCORT', 'ADMIN')")
    @GetMapping("/horse/{horseId}")
    public ResponseEntity<ApiResponse<List<HorseDocumentResponse>>> getDocumentsByHorse(
            @PathVariable Integer horseId, Authentication authentication) {

        assertHorseAccess(horseId, authentication);

        List<HorseDocumentResponse> documents = horseDocumentService.getDocumentsByHorse(horseId);
        return ResponseEntity.ok(ApiResponse.success(documents,
                "Lấy danh sách hồ sơ ngựa ID=" + horseId + " thành công. Tổng: " + documents.size()));
    }

    private void assertHorseAccess(Integer horseId, Authentication authentication) {
        if (authentication == null || !authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CUSTOMER"))) return;
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ngựa với ID: " + horseId));
        if (horse.getCustomerID() == null || horse.getCustomerID().getUser() == null
                || !authentication.getName().equalsIgnoreCase(horse.getCustomerID().getUser().getUsername())) {
            throw new AccessDeniedException("Bạn không có quyền truy cập giấy tờ ngựa này.");
        }
    }

    /**
     * Lấy danh sách hồ sơ đang chờ duyệt (dành cho chuyên viên).
     *
     * GET /api/v1/horse-documents/pending
     */
    @PreAuthorize("hasAnyRole('SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN')")
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<HorseDocumentResponse>>> getPendingDocuments() {
        List<HorseDocumentResponse> pending = horseDocumentService.getPendingDocuments();
        return ResponseEntity.ok(ApiResponse.success(pending,
                "Danh sách hồ sơ chờ duyệt. Tổng: " + pending.size()));
    }

    /**
     * Xem chi tiết một hồ sơ.
     *
     * GET /api/v1/horse-documents/{id}
     */
    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'COORDINATOR', 'DRIVER', 'ESCORT', 'ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseDocumentResponse>> getDocumentById(
            @PathVariable Integer id) {

        HorseDocument doc = horseDocumentService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ ID: " + id));

        // Build response trực tiếp từ entity (các field review đã có sẵn trong entity)
        HorseDocumentResponse response = HorseDocumentResponse.builder()
                .id(doc.getId())
                .horseId(doc.getHorseID() != null ? doc.getHorseID().getId() : null)
                .horseName(doc.getHorseID() != null ? doc.getHorseID().getHorseName() : null)
                .documentType(doc.getDocumentType())
                .fileUrl(doc.getFileUrl())
                .status(doc.getStatus())
                .uploadDate(doc.getUploadDate())
                .reviewNote(doc.getReviewNote())
                .reviewerName(doc.getReviewerName())
                .reviewedAt(doc.getReviewedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết hồ sơ thành công."));
    }
}
