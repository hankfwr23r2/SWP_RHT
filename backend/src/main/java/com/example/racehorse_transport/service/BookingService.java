package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface BookingService {

    BookingResponseDto submit(String username, String ipAddress, BookingCreateRequestDto request);

    BookingResponseDto submit(String username, String ipAddress, BookingCreateRequestDto request,
            List<MultipartFile> insuranceFiles);
}
