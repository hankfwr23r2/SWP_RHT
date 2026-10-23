package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.entity.Booking;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

// Đọc chi tiết đơn (Booking.detailsJson) cho mọi service trước khi sửa rồi ghi lại.
// Đọc lỗi thì dừng thao tác: nếu đi tiếp với payload rỗng, lần ghi sau sẽ xóa hết chi tiết đơn.
public final class BookingDetailsJson {

    private BookingDetailsJson() {}

    public static BookingDetailsPayload read(ObjectMapper objectMapper, Booking booking) {
        BookingDetailsPayload payload = new BookingDetailsPayload();
        String json = booking.getDetailsJson() == null ? "" : booking.getDetailsJson().trim();
        if (json.isEmpty()) return payload;
        try {
            if (json.startsWith("[")) {
                // Dữ liệu cũ: detailsJson chỉ là danh sách ngựa
                payload.setHorses(objectMapper.readValue(json, new TypeReference<List<BookingHorseDto>>() {}));
                return payload;
            }
            return objectMapper.readValue(json, BookingDetailsPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể đọc chi tiết đơn " + booking.getId() + ".", ex);
        }
    }
}
