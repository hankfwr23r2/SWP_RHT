package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;

public interface HandoverReadinessService {
    void requireConfirmedInsurance(BookingHorse horse);

    void requireReadyToReceiveAtHub(BookingHorse horse);

    void requireDocumentsForPickup(Booking booking);

}
