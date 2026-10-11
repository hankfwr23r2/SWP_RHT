package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.TripDocumentDTO;
import com.example.racehorse_transport.dto.VerifyDocumentRequest;
import com.example.racehorse_transport.entity.TripDocument;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.TripDocumentService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/trip-documents")
public class TripDocumentController {

    private final TripDocumentService tripDocumentService;
    private final ModelMapper modelMapper;

    @Autowired
    public TripDocumentController(TripDocumentService tripDocumentService, ModelMapper modelMapper) {
        this.tripDocumentService = tripDocumentService;
        this.modelMapper = modelMapper;
    }

    // 1. Lấy tất cả giấy tờ trong hệ thống (dành cho quản lý)
    @GetMapping
    public ResponseEntity<ApiResponse<List<TripDocumentDTO>>> getAllDocuments() {
        List<TripDocument> docs = tripDocumentService.findAll();
        List<TripDocumentDTO> dtos = docs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy danh sách tất cả giấy tờ thành công"));
    }

    // 2. Lấy danh sách giấy tờ theo đơn hàng / chuyến đi (để tài xế xem và tick chọn)
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResponse<List<TripDocumentDTO>>> getDocumentsByBooking(@PathVariable Integer bookingId) {
        List<TripDocument> docs = tripDocumentService.findByBookingId(bookingId);
        List<TripDocumentDTO> dtos = docs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy danh sách giấy tờ của chuyến đi thành công"));
    }

    // 3. Xem chi tiết 1 giấy tờ
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TripDocumentDTO>> getDocumentById(@PathVariable Integer id) {
        Optional<TripDocument> docOpt = tripDocumentService.findById(id);
        if (docOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(toDTO(docOpt.get()), "Lấy thông tin giấy tờ thành công"));
        } else {
            return ResponseEntity.status(404).body(ApiResponse.error("Không tìm thấy giấy tờ có ID: " + id));
        }
    }

    // 4. API Dev 4: Tài xế xác nhận danh sách giấy tờ hợp lệ
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<String>> verifyDocuments(@Valid @RequestBody VerifyDocumentRequest request) {
        tripDocumentService.verifyTripDocuments(request.getRouteId(), request.getDocumentIds());
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận kiểm tra giấy tờ chuyến đi thành công", "Thành công"));
    }

    // Helper map entity sang DTO
    private TripDocumentDTO toDTO(TripDocument entity) {
        TripDocumentDTO dto = modelMapper.map(entity, TripDocumentDTO.class);
        if (entity.getBookingID() != null) {
            dto.setBookingID(entity.getBookingID().getId());
        }
        if (entity.getRegulationID() != null) {
            dto.setRegulationID(entity.getRegulationID().getId());
        }
        if (entity.getStaffID() != null) {
            dto.setStaffID(entity.getStaffID().getId());
        }
        return dto;
    }
}
