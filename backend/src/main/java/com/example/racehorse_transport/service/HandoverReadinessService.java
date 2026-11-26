package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;

public interface HandoverReadinessService {
    void requireConfirmedInsurance(BookingHorse horse);

    void requireReadyToReceiveAtHub(BookingHorse horse);

    void requireDocumentsForPickup(Booking booking);

    void requireHubExitCheck(HorseLeg leg);

    void requireHubCollectionCheck(HorseStay stay);
}
