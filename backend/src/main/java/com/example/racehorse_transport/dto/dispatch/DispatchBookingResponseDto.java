package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Đơn chờ xếp xe ở bảng điều độ: địa chỉ đón và giao, chuyến trục, từng con ngựa với các chặng đã lập
public record DispatchBookingResponseDto(
        Long id, String code, BookingType bookingType, BookingStatus status, LocalDate requestedPickupDate,
        Long departureId, String routeCode, Instant plannedDepartureAt,
        AddressDto pickupAddress, AddressDto deliveryAddress, List<HorseDto> horses) {

    public record AddressDto(Long id, String addressLine, String contactName, String contactPhone) {
        public static AddressDto from(BookingAddress address) {
            return address == null ? null
                    : new AddressDto(address.getId(), address.getAddressLine(), address.getContactName(), address.getContactPhone());
        }
    }

    public record HorseDto(Long bookingHorseId, String horseName, String microchip, String stallTypeCode, Integer seats,
                           BookingHorseStatus status, List<HorseLegResponseDto> legs) {
        public static HorseDto from(BookingHorse line, List<HorseLegResponseDto> legs) {
            return new HorseDto(line.getId(), line.getHorse().getName(), line.getHorse().getMicrochip(), line.getStallType().getCode(),
                    StallSeatPolicy.seats(line.getStallType().getCode()), line.getStatus(), legs);
        }
    }

    public static DispatchBookingResponseDto from(Booking booking, BookingAddress pickup, BookingAddress delivery, List<HorseDto> horses) {
        var departure = booking.getDeparture();
        return new DispatchBookingResponseDto(booking.getId(), booking.getCode(), booking.getBookingType(), booking.getStatus(),
                booking.getRequestedPickupDate(),
                departure == null ? null : departure.getId(), departure == null ? null : departure.getRoute().getCode(),
                departure == null ? null : departure.getPlannedDepartureAt(),
                AddressDto.from(pickup), AddressDto.from(delivery), horses);
    }
}
