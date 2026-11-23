package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Province;

public interface BookingRoutingService {
    SubmissionRouting resolve(BookingCreateRequestDto request);

    Departure lockDeparture(BookingCreateRequestDto request, SubmissionRouting routing);

    record SubmissionRouting(AddressInput pickup, AddressInput delivery, Province pickupProvince,
                             Province deliveryProvince, Long pickupHub, Long deliveryHub) {
    }
}
