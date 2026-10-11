package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.TripDocument;

import com.example.racehorse_transport.repository.TripDocumentRepository;
import com.example.racehorse_transport.service.TripDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TripDocumentServiceImpl implements TripDocumentService {

    private final TripDocumentRepository tripDocumentRepository;

    @Autowired
    public TripDocumentServiceImpl(TripDocumentRepository tripDocumentRepository) {
        this.tripDocumentRepository = tripDocumentRepository;
    }

    @Override
    public List<TripDocument> findAll() {
        return tripDocumentRepository.findAll();
    }

    @Override
    public Optional<TripDocument> findById(Integer id) {
        return tripDocumentRepository.findById(id);
    }

    @Override
    public TripDocument save(TripDocument entity) {
        return tripDocumentRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        tripDocumentRepository.deleteById(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean verifyTripDocuments(Integer routeId, List<Integer> documentIds) {
        // 1. Kiểm tra danh sách ID truyền lên có rỗng không
        if (documentIds == null || documentIds.isEmpty()) {
            throw new IllegalArgumentException("Danh sách giấy tờ xác nhận không được để trống!");
        }
        // 2. Tìm tất cả các giấy tờ theo danh sách ID
        List<TripDocument> documents = tripDocumentRepository.findAllById(documentIds);
        if (documents.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy giấy tờ nào hợp lệ với danh sách ID đã gửi!");
        }
        // 3. (Kiểm tra an toàn): Đảm bảo giấy tờ thuộc đúng chuyến đi/booking này
        for (TripDocument doc : documents) {
            // Đổi trạng thái sang VERIFIED
            doc.setStatus("VERIFIED");

            // Cập nhật ngày kiểm tra (nếu cần)
            doc.setIssueDate(java.time.LocalDate.now());
        }
        // 4. Lưu tất cả thay đổi xuống Database
        tripDocumentRepository.saveAll(documents);

        return true;
    }

    @Override
    public List<TripDocument> findByBookingId(Integer bookingId) {
        if (bookingId == null) {
            throw new IllegalArgumentException("Booking ID không được để trống!");
        }
        return tripDocumentRepository.findByBookingId(bookingId);
    }

}
