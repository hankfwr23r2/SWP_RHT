package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import java.time.Instant;

// pickupMethod, deliveryMethod: khách tự chở tới hub hoặc tự nhận ở hub (SELF) thì giao nhận ngay tại hub
public record HorseStayResponseDto(Long id, Long bookingHorseId, String bookingCode, String horseName, HorseStayType stayType,
                                   Long locationId, String locationName, Instant plannedEndAt, Instant actualStartAt, Instant actualEndAt,
                                   Instant plannedStartAt, TransferMethod pickupMethod, TransferMethod deliveryMethod,
                                   Instant balanceHoldAt, boolean returnForBalance) {

    public static HorseStayResponseDto from(HorseStay stay) {
        return from(stay, stay.getBalanceHoldAt() != null);
    }

    public static HorseStayResponseDto from(HorseStay stay, boolean returnForBalance) {
        var line = stay.getBookingHorse();
        return new HorseStayResponseDto(stay.getId(), line.getId(), line.getBooking().getCode(), line.getHorse().getName(), stay.getStayType(),
                stay.getLocation().getId(), stay.getLocation().getName(), stay.getPlannedEndAt(), stay.getActualStartAt(), stay.getActualEndAt(),
                stay.getPlannedStartAt(), line.getBooking().getPickupMethod(), line.getBooking().getDeliveryMethod(),
                stay.getBalanceHoldAt(), returnForBalance);
    }
}
