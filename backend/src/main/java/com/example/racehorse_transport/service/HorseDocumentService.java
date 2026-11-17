package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.document.HorseDocumentResponse;
import com.example.racehorse_transport.dto.document.ReviewHorseDocumentRequest;
import com.example.racehorse_transport.entity.HorseDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface HorseDocumentService {
    List<HorseDocument> findAll();
    HorseDocument save(HorseDocument entity);
    void deleteById(Integer id);

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 2: Hồ sơ (Flow 2)
    // =========================================================================

    /**
     * Tải hồ sơ ngựa lên hệ thống lưu trữ và lưu metadata vào cơ sở dữ liệu.
     *
     * @param horseId      ID con ngựa cần gắn hồ sơ
     * @param documentType Loại giấy tờ (PASSPORT, VACCINATION, BLOOD_TEST, HEALTH_CERT, OTHER)
     * @param file         File tải lên (ảnh hoặc PDF)
     * @return Thông tin hồ sơ vừa tạo
     */
    HorseDocumentResponse uploadHorseDocument(Integer horseId,
                                              String documentType,
                                              MultipartFile file);

    /**
     * Chuyên viên duyệt hoặc từ chối hồ sơ ngựa (nhận DTO request).
     *
     * @param documentId ID hồ sơ cần xét duyệt
     * @param request    Thông tin duyệt (approved, note, reviewerName)
     * @return Thông tin hồ sơ sau khi cập nhật
     */
    HorseDocumentResponse reviewDocument(Integer documentId, ReviewHorseDocumentRequest request);

    /**
     * Overload duyệt hoặc từ chối hồ sơ ngựa nhận tham số rời (tương thích ngược).
     */
    HorseDocument reviewDocument(Integer documentId, boolean isApproved, String note);

    /**
     * Lấy danh sách toàn bộ hồ sơ của một con ngựa.
     *
     * @param horseId ID con ngựa
     * @return Danh sách hồ sơ
     */
    List<HorseDocumentResponse> getDocumentsByHorse(Integer horseId);

    /**
     * Lấy hồ sơ của mọi con ngựa thuộc khách hàng đang đăng nhập.
     *
     * @param customerUsername Tài khoản khách hàng
     * @return Danh sách hồ sơ
     */
    List<HorseDocumentResponse> getCustomerDocuments(String customerUsername);

    /**
     * Chặn khách hàng truy cập giấy tờ của ngựa không thuộc về mình.
     *
     * @param horseId          ID con ngựa
     * @param customerUsername Tài khoản khách hàng đang đăng nhập
     */
    void assertCustomerOwnsHorse(Integer horseId, String customerUsername);

    /**
     * Xóa giấy tờ ngựa; khách hàng chỉ được xóa giấy tờ của ngựa mình sở hữu.
     *
     * @param documentId       ID giấy tờ
     * @param customerUsername Tài khoản khách hàng cần kiểm tra quyền sở hữu, null nếu người xóa không phải khách
     */
    void deleteDocument(Integer documentId, String customerUsername);
}
