package com.example.racehorse_transport.dto.complaint;

import com.example.racehorse_transport.entity.Booking;
import java.time.Instant;

public record DeliveryComplaintResponseDto(Long bookingId, Instant confirmedAt, Instant deliveredAt,
        boolean customerCanRespond, boolean managerCanSubmit, ComplaintResponseDto complaint) {

    public static DeliveryComplaintResponseDto from(Booking booking, Instant deliveredAt,
            boolean customerCanRespond, boolean managerCanSubmit, ComplaintResponseDto complaint) {
        return new DeliveryComplaintResponseDto(booking.getId(), booking.getDeliveryConfirmedAt(), deliveredAt,
                customerCanRespond, managerCanSubmit, complaint);
    }
}
