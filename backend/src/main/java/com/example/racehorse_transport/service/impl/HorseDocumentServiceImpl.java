package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.document.HorseDocumentResponse;
import com.example.racehorse_transport.dto.document.ReviewHorseDocumentRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    // =========================================================================
    // CRUD CƠ BẢN
    // =========================================================================

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
     * Upload hồ sơ (tích hợp Cloud/Local) trả về DTO Response.
     *
     * Luồng xử lý:
     * 1. Kiểm tra con ngựa tồn tại
     * 2. Upload file lên Cloudinary (folder: "horse-documents/{horseId}")
     * 3. Lưu metadata vào bảng HORSE_DOCUMENT với status = PENDING
     * 4. Trả về thông tin hồ sơ vừa tạo
     */
    @Override
    @Transactional
    public HorseDocumentResponse uploadHorseDocument(Integer horseId,
                                                     String documentType,
                                                     MultipartFile file) {
        // 1. Kiểm tra con ngựa tồn tại
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy ngựa với ID: " + horseId));

        // 2. Kiểm tra file hợp lệ
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File hồ sơ không được để trống!");
        }

        // Chỉ chấp nhận ảnh (JPG, PNG) và tài liệu PDF
        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            throw new IllegalArgumentException(
                    "Định dạng file không hợp lệ! Chỉ chấp nhận ảnh (JPG/PNG) hoặc PDF.");
        }

        // Loại giấy tờ mặc định nếu không truyền
        String docType = (documentType != null && !documentType.isBlank())
                ? documentType.toUpperCase()
                : "OTHER";

        // 3. Upload lên Cloudinary, phân loại theo thư mục horse-documents/{horseId}
        String folder = "horse-documents/" + horseId;
        String fileUrl = storageService.uploadFile(file, folder);

        // 4. Tạo và lưu bản ghi HorseDocument
        HorseDocument document = new HorseDocument();
        document.setHorseID(horse);
        document.setDocumentType(docType);
        document.setFileUrl(fileUrl);
        document.setStatus("PENDING");          // Chờ chuyên viên duyệt
        document.setUploadDate(Instant.now());

        HorseDocument saved = horseDocumentRepository.save(document);
        log.info("Uploaded horse document: horseId={}, documentType={}, url={}, status=PENDING",
                horseId, docType, fileUrl);

        return mapToResponse(saved);
    }

    /**
     * Chuyên viên duyệt/từ chối hồ sơ (nhận DTO request).
     *
     * Luồng xử lý:
     * 1. Tìm hồ sơ theo documentId
     * 2. Kiểm tra hồ sơ đang ở trạng thái PENDING (chỉ được duyệt hồ sơ chờ)
     * 3. Cập nhật status = APPROVED hoặc REJECTED kèm ghi chú
     * 4. Lưu và trả về kết quả
     */
    @Override
    @Transactional
    public HorseDocumentResponse reviewDocument(Integer documentId,
                                                ReviewHorseDocumentRequest request) {
        // 1. Tìm hồ sơ
        HorseDocument document = horseDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy hồ sơ với ID: " + documentId));

        // 2. Kiểm tra trạng thái - chỉ được duyệt hồ sơ đang PENDING
        if (!"PENDING".equalsIgnoreCase(document.getStatus())) {
            throw new IllegalStateException(
                    "Hồ sơ này đã được xử lý trước đó! Trạng thái hiện tại: "
                    + document.getStatus() + ". Chỉ có thể duyệt hồ sơ ở trạng thái PENDING.");
        }

        // 3. Cập nhật kết quả duyệt
        Instant now = Instant.now();
        String reviewer = (request.getReviewerName() != null && !request.getReviewerName().isBlank())
                ? request.getReviewerName()
                : "Chuyên viên";

        if (request.isApproved()) {
            document.setStatus("APPROVED");
            log.info("Document ID={} APPROVED by {}", documentId, reviewer);
        } else {
            // Bắt buộc phải có lý do khi từ chối
            if (request.getNote() == null || request.getNote().isBlank()) {
                throw new IllegalArgumentException(
                        "Vui lòng nhập lý do từ chối hồ sơ (trường 'note' không được để trống)!");
            }
            document.setStatus("REJECTED");
            log.info("Document ID={} REJECTED by {}: {}", documentId, reviewer, request.getNote());
        }

        document.setReviewNote(request.getNote());
        document.setReviewerName(reviewer);
        document.setReviewedAt(now);

        HorseDocument saved = horseDocumentRepository.save(document);
        return mapToResponse(saved);
    }

    /**
     * Overload duyệt/từ chối hồ sơ trả về Entity (tương thích ngược).
     */
    @Override
    @Transactional
    public HorseDocument reviewDocument(Integer documentId, boolean isApproved, String note) {
        ReviewHorseDocumentRequest req = new ReviewHorseDocumentRequest();
        req.setApproved(isApproved);
        req.setNote(note);
        HorseDocumentResponse response = reviewDocument(documentId, req);
        return horseDocumentRepository.findById(response.getId()).orElse(null);
    }

    /**
     * Lấy toàn bộ hồ sơ của một con ngựa (mọi trạng thái).
     */
    @Override
    @Transactional(readOnly = true)
    public List<HorseDocumentResponse> getDocumentsByHorse(Integer horseId) {
        if (!horseRepository.existsById(horseId)) {
            throw new IllegalArgumentException("Không tìm thấy ngựa với ID: " + horseId);
        }
        return horseDocumentRepository.findByHorseID_Id(horseId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách hồ sơ đang chờ duyệt (status = PENDING).
     * Dành cho chuyên viên xem queue công việc cần xử lý.
     */
    @Override
    @Transactional(readOnly = true)
    public List<HorseDocumentResponse> getPendingDocuments() {
        return horseDocumentRepository.findByStatus("PENDING")
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private HorseDocumentResponse mapToResponse(HorseDocument doc) {
        String horseName = null;
        if (doc.getHorseID() != null) {
            horseName = doc.getHorseID().getHorseName();
        }

        return HorseDocumentResponse.builder()
                .id(doc.getId())
                .horseId(doc.getHorseID() != null ? doc.getHorseID().getId() : null)
                .horseName(horseName)
                .documentType(doc.getDocumentType())
                .fileUrl(doc.getFileUrl())
                .status(doc.getStatus())
                .uploadDate(doc.getUploadDate())
                .reviewNote(doc.getReviewNote())
                .reviewerName(doc.getReviewerName())
                .reviewedAt(doc.getReviewedAt())
                .build();
    }
}
