package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.BookingResponse;

/** Removes internal expense/audit data from a customer booking response only. */
public final class CustomerBookingPrivacy {
    private CustomerBookingPrivacy() {}

    public static BookingResponse apply(BookingResponse response) {
        response.setSettlement(null);
        response.setHistory(null);
        response.setIntake(null);
        response.setPlan(null);
        if (response.getPhotoEvidence() != null) {
            var visible = new java.util.LinkedHashMap<>(response.getPhotoEvidence());
            visible.entrySet().removeIf(entry ->
                    java.util.Set.of("EXPENSE", "TOLL").contains(entry.getValue().purpose()));
            response.setPhotoEvidence(visible);
        }
        if (response.getTrips() != null) {
            response.getTrips().forEach(trip -> {
                trip.setTolls(null);
                trip.setDriverPack(null);
                trip.setAcks(null);
            });
        }
        return response;
    }
}
