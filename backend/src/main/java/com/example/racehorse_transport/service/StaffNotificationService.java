package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;

public interface StaffNotificationService {

    void toManagers(Booking booking, String title, String message, String link);

    void toAssignedSpecialist(Booking booking, BookingDetailsPayloadDto payload, String title, String message, String link);

    void toAssignedCoordinator(Booking booking, BookingDetailsPayloadDto payload, String title, String message, String link);

    void toCrew(Booking booking, BookingDetailsPayloadDto payload, String title, String message);

    void toTripCrew(Booking booking, VehicleTripDto trip, String title, String message);

    void bookingCancelled(Booking booking, BookingDetailsPayloadDto payload, String reason);

    void horsesToReturn(Booking booking, BookingDetailsPayloadDto payload, String reason);

    void toCustomer(Booking booking, String title, String message);
}
