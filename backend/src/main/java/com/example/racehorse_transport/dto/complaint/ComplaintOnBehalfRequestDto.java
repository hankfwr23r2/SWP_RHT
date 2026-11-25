package com.example.racehorse_transport.dto.complaint;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Incident;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public record ComplaintOnBehalfRequestDto(@NotBlank @Size(max = 4000) String description,
        @Positive Long bookingHorseId, List<MultipartFile> photos) {

    public Incident toEntity(Booking booking, BookingHorse bookingHorse) {
        return new ComplaintCreateRequestDto(description, bookingHorseId, photos).toEntity(booking, bookingHorse);
    }
}
