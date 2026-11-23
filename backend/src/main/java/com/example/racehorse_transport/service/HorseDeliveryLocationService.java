package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;

public interface HorseDeliveryLocationService {

    void recordTripDelivery(Booking booking, BookingDetailsPayloadDto payload, VehicleTripDto trip,
            String actor, String proofImageUrl, long arrivedAt);

    // Người nhận không tới: ngựa của chuyến được gửi tại chuồng, vị trí ngựa là chuồng đó
    void recordTripStabling(Booking booking, BookingDetailsPayloadDto payload, VehicleTripDto trip,
            String actor, String proofImageUrl, long arrivedAt, String stablePlace);

    void recordBookingHandover(Booking booking, BookingDetailsPayloadDto payload,
            String actor, String proofImageUrl, long arrivedAt);
}
