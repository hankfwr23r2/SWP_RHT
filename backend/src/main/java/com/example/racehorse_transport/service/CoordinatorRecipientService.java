package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Staff;
import java.util.List;

public interface CoordinatorRecipientService {
    List<Long> forTrip(VehicleTrip trip);
    List<Long> forHorse(BookingHorse horse, VehicleTrip trip);
    Staff atHub(BookingHorse horse);
    Staff staffByUsername(String username);
    List<Long> managers();
    List<Long> forHubBooking(Long bookingId);
}
