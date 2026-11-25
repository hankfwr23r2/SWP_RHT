package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.CollectionCodeResponseDto;
import com.example.racehorse_transport.entity.Booking;

public interface CollectionCodeService {
    void issue(Booking booking);
    boolean verify(Long bookingId, String code);
    CollectionCodeResponseDto read(Long bookingId, String username);
    CollectionCodeResponseDto confirm(Long bookingId, String username);
}
