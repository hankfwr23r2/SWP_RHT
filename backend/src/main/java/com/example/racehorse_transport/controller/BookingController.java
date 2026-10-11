package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.BookingDTO;
import com.example.racehorse_transport.dto.CompleteDeliveryRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.BookingService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final ModelMapper modelMapper;

    @Autowired
    public BookingController(BookingService bookingService, ModelMapper modelMapper) {
        this.bookingService = bookingService;
        this.modelMapper = modelMapper;
    }

    // 1. Lấy tất cả đơn hàng
    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingDTO>>> getAllBookings() {
        List<Booking> bookings = bookingService.findAll();
        List<BookingDTO> dtos = bookings.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy danh sách đơn hàng thành công"));
    }

    // 2. Xem chi tiết đơn hàng
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingDTO>> getBookingById(@PathVariable Integer id) {
        Optional<Booking> bookingOpt = bookingService.findById(id);
        if (bookingOpt.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(toDTO(bookingOpt.get()), "Lấy thông tin đơn hàng thành công"));
        } else {
            return ResponseEntity.status(404).body(ApiResponse.error("Không tìm thấy đơn hàng ID: " + id));
        }
    }

    // 3. Dev 4: Bàn giao, ký nhận và kết thúc đơn hàng (Flow 6)
    @PostMapping("/{id}/complete-delivery")
    public ResponseEntity<ApiResponse<String>> completeDelivery(
            @PathVariable Integer id,
            @Valid @RequestBody CompleteDeliveryRequest request) {
        bookingService.completeBookingDelivery(
                id,
                request.getRecipientName(),
                request.getRecipientSignature(),
                request.getNote()
        );
        return ResponseEntity.ok(ApiResponse.success("Bàn giao đơn hàng thành công! Đơn hàng đã chuyển sang trạng thái COMPLETED.", "Thành công"));
    }

    private BookingDTO toDTO(Booking entity) {
        BookingDTO dto = modelMapper.map(entity, BookingDTO.class);
        if (entity.getCustomerID() != null) {
            dto.setCustomerID(entity.getCustomerID().getId());
        }
        if (entity.getRouteID() != null) {
            dto.setRouteID(entity.getRouteID().getId());
        }
        return dto;
    }
}