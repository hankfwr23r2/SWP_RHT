package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.HorseLegRequestDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto.PlaceDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.TripStopRequestDto;
import com.example.racehorse_transport.dto.dispatch.TripStopResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Window;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.HorseLegService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.TripScheduleDataService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripScheduleDataServiceImpl implements TripScheduleDataService {

    private final BookingAddressRepository addresses;
    private final BookingHorseRepository bookingHorses;
    private final LocationRepository locations;
    private final SystemConfigService configs;
    private final DispatchService dispatch;
    private final HorseLegService horseLegs;

    @Override
    @Transactional(readOnly = true)
    public TripInput load(PlannedTripRequestDto request) {
        if (request.tripType() == VehicleTripType.LINEHAUL) {
            throw new DispatchRuleException("tripType", "Lượt xe trục lấy giờ theo chuyến, không tính ở đây.");
        }
        List<PlaceInput> places = request.places().stream().map(this::place).toList();
        List<BookingHorse> lines = request.bookingHorseIds().stream().distinct().map(id -> bookingHorses.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn " + id))).toList();
        List<HorseInput> horses = lines.stream().map(line -> horse(request.tripType(), line, places)).toList();
        List<Booking> found = lines.stream().map(BookingHorse::getBooking).distinct().toList();
        return new TripInput(places, horses,
                found.stream().map(Booking::getRequestedPickupDate).min(Comparator.naturalOrder()).orElse(null),
                request.tripType() == VehicleTripType.PICKUP ? hubDeadline(found) : null,
                request.tripType() == VehicleTripType.DELIVERY ? readyAt(found) : null,
                rules(), window("PICKUP"), window("DELIVERY"),
                configs.getInt("TRUCK_WEIGHT_TONS"), configs.getInt("TRUCK_TOP_SPEED_KMH"));
    }

    @Override
    @Transactional
    public VehicleTripResponseDto save(PlannedTripRequestDto request, TripInput input, PlannedTripResponseDto plan, String username) {
        VehicleTripResponseDto trip = dispatch.createTrip(new VehicleTripCreateRequestDto(request.tripType(), null, request.vehicleId(),
                plan.stops().stream().map(stop -> new TripStopRequestDto(stop.locationId(), stop.bookingAddressId(), stop.stopType(),
                        stop.arrivalAt(), stop.departureAt())).toList()));
        List<TripStopResponseDto> stops = trip.stops();
        for (HorseInput horse : input.horses()) {
            horseLegs.assign(trip.id(), new HorseLegRequestDto(horse.bookingHorseId(), stops.get(horse.board()).id(), stops.get(horse.alight()).id()), username);
        }
        return dispatch.trip(trip.id());
    }

    private PlaceInput place(PlaceDto place) {
        if ((place.locationId() == null) == (place.bookingAddressId() == null)) {
            throw new DispatchRuleException("places", "Mỗi điểm phải là một điểm mạng lưới hoặc một địa chỉ của đơn.");
        }
        if (place.bookingAddressId() != null) {
            BookingAddress address = addresses.findById(place.bookingAddressId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ của đơn " + place.bookingAddressId()));
            return new PlaceInput(null, address.getId(), address.getAddressType() == AddressRole.PICKUP ? TripStopType.PICKUP : TripStopType.DELIVERY,
                    address.getAddressLine(), address.getBooking().getCode(), point(address.getLatitude(), address.getLongitude()));
        }
        Location location = locations.findById(place.locationId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm " + place.locationId()));
        if (location.getLatitude() == null || location.getLongitude() == null) {
            throw new DispatchRuleException("places", location.getName() + " chưa có tọa độ.");
        }
        TripStopType type = location.getLocationType() == LocationType.REST_STOP ? TripStopType.REST
                : location.getLocationType() == LocationType.BORDER_GATE ? TripStopType.BORDER : TripStopType.HUB;
        return new PlaceInput(location.getId(), null, type, location.getName(), null, point(location.getLatitude(), location.getLongitude()));
    }

    // Lượt đón: lên ở địa chỉ đón, xuống ở điểm cuối. Lượt giao: lên ở điểm đầu, xuống ở địa chỉ giao. Chạy thẳng: địa chỉ đón tới địa chỉ giao
    private HorseInput horse(VehicleTripType type, BookingHorse line, List<PlaceInput> places) {
        Booking booking = line.getBooking();
        List<BookingAddress> own = addresses.findByBooking_Id(booking.getId());
        int pickup = indexOf(places, own, AddressRole.PICKUP);
        int delivery = indexOf(places, own, AddressRole.DELIVERY);
        int board = type == VehicleTripType.PICKUP || type == VehicleTripType.DIRECT ? pickup : 0;
        int alight = type == VehicleTripType.DELIVERY || type == VehicleTripType.DIRECT ? delivery : places.size() - 1;
        if (board < 0 || alight < 0 || board >= alight) {
            throw new DispatchRuleException("places", "Danh sách điểm chưa có nơi đón hoặc nơi giao của đơn " + booking.getCode() + ".");
        }
        return new HorseInput(line.getId(), board, alight);
    }

    private static int indexOf(List<PlaceInput> places, List<BookingAddress> own, AddressRole role) {
        Long id = own.stream().filter(address -> address.getAddressType() == role).map(BookingAddress::getId).findFirst().orElse(null);
        List<Long> ids = new ArrayList<>();
        places.forEach(place -> ids.add(place.bookingAddressId()));
        return id == null ? -1 : ids.indexOf(id);
    }

    private Instant hubDeadline(List<Booking> found) {
        int hours = configs.getInt("HUB_CHECKIN_HOURS");
        return found.stream().map(Booking::getDeparture).filter(Objects::nonNull)
                .map(departure -> departure.getPlannedDepartureAt().minus(Duration.ofHours(hours))).min(Comparator.naturalOrder()).orElse(null);
    }

    private static Instant readyAt(List<Booking> found) {
        return found.stream().map(Booking::getDeparture).filter(Objects::nonNull)
                .map(departure -> departure.getPlannedDepartureAt().plus(Duration.ofMinutes(departure.getRoute().getEstimatedDurationMinutes())))
                .max(Comparator.naturalOrder()).orElse(null);
    }

    private Rules rules() {
        return new Rules(configs.getInt("MAX_CONTINUOUS_HOURS"), configs.getInt("MIN_REST_MINUTES"), configs.getInt("MAX_DRIVE_HOURS_PER_DAY"),
                configs.getInt("STOP_BASE_MINUTES"), configs.getInt("STOP_MINUTES_PER_HORSE"));
    }

    private Window window(String side) {
        return new Window(configs.getInt(side + "_WINDOW_START_HOUR"), configs.getInt(side + "_WINDOW_END_HOUR"));
    }

    private static GeoPointDto point(BigDecimal latitude, BigDecimal longitude) {
        return new GeoPointDto(latitude.doubleValue(), longitude.doubleValue());
    }
}
