package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.HorseDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface HorseDocumentService {
    List<HorseDocument> findAll();
    Optional<HorseDocument> findById(Integer id);
    HorseDocument save(HorseDocument entity);
    void deleteById(Integer id);

    /**
     * Dev 2 – Upload hồ sơ ngựa lên Cloudinary và lưu metadata vào DB.
     *
     * @param horseId      ID con ngựa cần gắn hồ sơ
     * @param documentType Loại tài liệu (passport, vaccination, blood_test, …)
     * @param file         File cần upload (ảnh/PDF)
     * @param expiryDate   Ngày hết hạn tài liệu (nullable)
     * @return HorseDocument đã lưu
     */
    HorseDocument uploadHorseDocument(Integer horseId, String documentType,
                                      MultipartFile file, java.time.LocalDate expiryDate);

    /**
     * Dev 2 – Chuyên viên duyệt hoặc từ chối một hồ sơ ngựa.
     *
     * @param documentId ID hồ sơ cần duyệt
     * @param isApproved true = duyệt đạt, false = từ chối
     * @param note       Ghi chú / lý do (bắt buộc khi từ chối)
     * @return HorseDocument đã cập nhật trạng thái
     */
    HorseDocument reviewDocument(Integer documentId, boolean isApproved, String note);
}
