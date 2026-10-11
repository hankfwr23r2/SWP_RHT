package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseDocument;
import com.example.racehorse_transport.repository.HorseDocumentRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.service.HorseDocumentService;
import com.example.racehorse_transport.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class HorseDocumentServiceImpl implements HorseDocumentService {

    private final HorseDocumentRepository horseDocumentRepository;
    private final HorseRepository horseRepository;
    private final StorageService storageService;

    @Autowired
    public HorseDocumentServiceImpl(HorseDocumentRepository horseDocumentRepository,
                                    HorseRepository horseRepository,
                                    StorageService storageService) {
        this.horseDocumentRepository = horseDocumentRepository;
        this.horseRepository = horseRepository;
        this.storageService = storageService;
    }

    @Override
    public List<HorseDocument> findAll() {
        return horseDocumentRepository.findAll();
    }

    @Override
    public Optional<HorseDocument> findById(Integer id) {
        return horseDocumentRepository.findById(id);
    }

    @Override
    public HorseDocument save(HorseDocument entity) {
        return horseDocumentRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        horseDocumentRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 2: Hồ sơ (Flow 2)
    // =========================================================================

    /**
     * Dev 2 – Upload hồ sơ ngựa (passport, vaccination, blood_test, ...) lên Cloudinary
     * và lưu metadata vào bảng HORSE_DOCUMENT.
     */
    @Override
    @Transactional
    public HorseDocument uploadHorseDocument(Integer horseId, String documentType,
                                              MultipartFile file, LocalDate expiryDate) {
        if (horseId == null) {
            throw new IllegalArgumentException("HorseID không được để trống!");
        }
        if (documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException("Loại tài liệu không được để trống!");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được để trống!");
        }

        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ngựa có ID: " + horseId));

        // Upload lên Cloudinary vào folder riêng cho hồ sơ ngựa
        String folder = "horse-documents/horse-" + horseId;
        String fileUrl = storageService.uploadFile(file, folder);

        HorseDocument document = new HorseDocument();
        document.setHorseID(horse);
        document.setDocumentType(documentType);
        document.setFileUrl(fileUrl);
        document.setExpiryDate(expiryDate);
        document.setStatus("PENDING");
        document.setUploadDate(Instant.now());

        HorseDocument saved = horseDocumentRepository.save(document);
        log.info("Uploaded horse document: horseId={}, type={}, url={}", horseId, documentType, fileUrl);
        return saved;
    }

    /**
     * Dev 2 – Chuyên viên duyệt hoặc từ chối một hồ sơ ngựa.
     * Khi từ chối (isApproved=false), bắt buộc phải có ghi chú lý do.
     */
    @Override
    @Transactional
    public HorseDocument reviewDocument(Integer documentId, boolean isApproved, String note) {
        if (documentId == null) {
            throw new IllegalArgumentException("DocumentID không được để trống!");
        }
        if (!isApproved && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("Lý do từ chối không được để trống!");
        }

        HorseDocument document = horseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ có ID: " + documentId));

        document.setStatus(isApproved ? "APPROVED" : "REJECTED");
        HorseDocument saved = horseDocumentRepository.save(document);
        log.info("Reviewed horse document ID={}: status={}, note={}", documentId, saved.getStatus(), note);
        return saved;
    }
}

