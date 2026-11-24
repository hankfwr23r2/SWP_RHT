package com.example.racehorse_transport.service;

import java.util.Set;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.dto.booking.BookingSummaryResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDetailResponseDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import java.util.List;

public interface BookingQueryService {

    List<BookingResponseDto> reviewQueue();

    // Mới nhất trước, mỗi trang tối đa PAGE_SIZE dòng
    List<BookingSummaryResponseDto> summaries(Set<BookingStatus> statuses, int page);

    BookingDetailResponseDto reviewDetail(Long id);

    List<BookingResponseDto> list(String username);

    BookingDetailResponseDto get(String username, Long id);
}
