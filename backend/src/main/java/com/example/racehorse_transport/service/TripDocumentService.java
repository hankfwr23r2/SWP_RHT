package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.TripDocument;

import java.util.List;
import java.util.Optional;

public interface TripDocumentService {
    List<TripDocument> findAll();

    Optional<TripDocument> findById(Integer id);

    TripDocument save(TripDocument entity);

    void deleteById(Integer id);

    // Dev 4: Lấy danh sách giấy tờ theo đơn hàng/chuyến đi
    List<TripDocument> findByBookingId(Integer bookingId);

    // Dev 4: Xác nhận danh sách giấy tờ hợp lệ
    boolean verifyTripDocuments(Integer routeId, List<Integer> documentIds);
}
