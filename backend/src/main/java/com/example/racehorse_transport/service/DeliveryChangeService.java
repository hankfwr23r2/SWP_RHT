package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.entity.Booking;

public interface DeliveryChangeService {
    DeliveryChangeResponseDto preview(Long id, String username, DeliveryChangeRequestDto request);
    DeliveryChangeResponseDto confirm(Long id, String username, DeliveryChangeConfirmRequestDto request);
    String disabledReason(Booking booking);
}
