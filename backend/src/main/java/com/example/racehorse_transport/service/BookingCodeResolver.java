package com.example.racehorse_transport.service;

import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Đổi mã đơn (BK-XXXXXXXX) nhận từ ngoài vào thành BookingID nội bộ. Không nhận BookingID số.
@Component
@RequiredArgsConstructor
public class BookingCodeResolver {

    private final BookingRepository bookingRepository;

    public Integer idOf(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Thiếu mã đơn hàng.");
        return bookingRepository.findIdByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + code));
    }
}
