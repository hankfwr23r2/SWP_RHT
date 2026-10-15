package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.document.HorseDocumentResponse;
import com.example.racehorse_transport.dto.document.ReviewHorseDocumentRequest;
import com.example.racehorse_transport.entity.HorseDocument;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HorseDocumentService {
    List<HorseDocument> findAll();
    Optional<HorseDocument> findById(Integer id);
    HorseDocument save(HorseDocument entity);
    void deleteById(Integer id);

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 2: Hồ sơ (Flow 2)
    // =========================================================================

    /**
     * Upload hồ sơ ngựa lên Cloudinary và lưu metadata vào DB (trả về DTO).
     *
     * @param horseId      ID con ngựa cần gắn hồ sơ
     * @param documentType Loại giấy tờ (PASSPORT, VACCINATION, BLOOD_TEST, HEALTH_CERT, OTHER)
     * @param expiryDate   Ngày hết hạn giấy tờ (nullable)
     * @param file         File tải lên (ảnh hoặc PDF)
     * @return Thông tin hồ sơ vừa tạo
     */
    HorseDocumentResponse uploadHorseDocument(Integer horseId,
                                              String documentType,
                                              LocalDate expiryDate,
                                              MultipartFile file);

    /**
     * Overload upload hồ sơ ngựa trả về Entity (tương thích ngược).
     */
    HorseDocument uploadHorseDocument(Integer horseId, String documentType,
                                      MultipartFile file, LocalDate expiryDate);

    /**
     * Chuyên viên duyệt hoặc từ chối hồ sơ ngựa (nhận DTO request).
     *
     * @param documentId ID hồ sơ cần xét duyệt
     * @param request    Thông tin duyệt (approved, note, reviewerName)
     * @return Thông tin hồ sơ sau khi cập nhật
     */
    HorseDocumentResponse reviewDocument(Integer documentId, ReviewHorseDocumentRequest request);

    HorseDocumentResponse updateExpiryDate(Integer documentId, LocalDate expiryDate);

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
     * Lấy danh sách hồ sơ đang chờ duyệt (status = PENDING).
     *
     * @return Danh sách hồ sơ chờ duyệt
     */
    List<HorseDocumentResponse> getPendingDocuments();
}
