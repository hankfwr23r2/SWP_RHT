package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailResponseDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import java.util.List;

public interface BookingQueryService {

    List<BookingResponseDto> list(String username);

    BookingDetailResponseDto get(String username, Long id);
}
