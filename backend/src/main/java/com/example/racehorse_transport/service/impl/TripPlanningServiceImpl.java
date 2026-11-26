package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.TripStopRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DepartureDelayPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.repository.AuditLogRepository;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RouteStopRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.TripPlanningService;
import com.example.racehorse_transport.service.VehicleService;
import com.example.racehorse_transport.util.VehicleTripCodes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripPlanningServiceImpl implements TripPlanningService {

    private static final Set<BookingStatus> DISPATCHABLE_BOOKINGS = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    private static final String MANUAL_CREATION = "CREATE_MANUAL_VEHICLE_TRIP";

    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final VehicleService vehicles;
    private final DepartureService departures;
    private final RouteStopRepository routeStops;
    private final LocationRepository locations;
    private final BookingAddressRepository bookingAddresses;
    private final CoordinatorAssignmentService coordinators;
    private final AuditService audit;
    private final AuditLogRepository auditLogs;

    @Override
    @Transactional
    public Long createTrip(VehicleTripCreateRequestDto request, String username, boolean manuallyTimed) {
        Vehicle vehicle = vehicles.lockVehicle(request.vehicleId());
        DispatchPolicy.requireActiveVehicle(vehicle.getStatus());
        VehicleTrip trip = request.tripType() == VehicleTripType.LINEHAUL ? linehaul(request, vehicle) : local(request, vehicle);
        // Kiểm và dựng đủ điểm dừng trước khi ghi gì xuống DB
        List<TripStop> plannedStops = request.tripType() == VehicleTripType.LINEHAUL
                ? linehaulStops(trip)
                : localStops(trip, request.stopsOrEmpty());
        requireVehicleSchedule(vehicle, trip, new Interval(trip.getPlannedStartAt(), trip.getPlannedEndAt()));
        trip.setCode(newCode());
        trip.setStatus(VehicleTripStatus.PLANNED);
        VehicleTrip saved = trips.save(trip);
        coordinators.assignAtCreation(saved, username);
        stops.saveAll(plannedStops);
        if (manuallyTimed && saved.getTripType() != VehicleTripType.LINEHAUL) {
            audit.record(saved.getCoordinator().getUserId(), MANUAL_CREATION, "vehicle_trip", saved.getId(), null, null);
        }
        return saved.getId();
    }

    // Lượt trục: theo chuyến, đi từ điểm đầu tới điểm cuối tuyến, giờ theo giờ xuất phát của chuyến
    private VehicleTrip linehaul(VehicleTripCreateRequestDto request, Vehicle vehicle) {
        if (request.departureId() == null) {
            throw new DispatchRuleException("departureId", "Lượt xe trục phải gắn với một chuyến.");
        }
        Departure departure = departures.lockForLinehaulPlanning(request.departureId());
        DispatchPolicy.requireRunnableDeparture(departure.getStatus(), departure.getRunConfirmedAt() != null);
        List<RouteStop> route = routeStops.findWithLocation(departure.getRoute().getId());
        if (route.size() < 2) {
            throw new DispatchRuleException("departureId", "Tuyến của chuyến chưa có đủ điểm dừng.");
        }
        Instant start = DepartureDelayPolicy.effectiveTime(departure);
        RouteStop last = route.getLast();
        Instant end = DispatchPolicy.routeStopWindow(start, last.getArrivalOffsetMinutes(), last.getDwellMinutes()).end();
        DispatchPolicy.requirePlannedWindow(start, end);
        return request.toEntity(vehicle, departure, departure.getRoute().getOrigin(), departure.getRoute().getDestination(), start, end);
    }

    private List<TripStop> linehaulStops(VehicleTrip trip) {
        Instant start = trip.getPlannedStartAt();
        return routeStops.findWithLocation(trip.getDeparture().getRoute().getId()).stream().map(routeStop -> {
            TripStop stop = new TripStop();
            stop.setVehicleTrip(trip);
            stop.setSequenceNumber(routeStop.getId().getSequenceNumber());
            stop.setLocation(routeStop.getLocation());
            stop.setStopType(TripStopType.valueOf(routeStop.getStopType().name()));
            stop.setRouteStop(routeStop);
            Interval time = DispatchPolicy.routeStopWindow(start, routeStop.getArrivalOffsetMinutes(), routeStop.getDwellMinutes());
            stop.setPlannedArrivalAt(time.start());
            stop.setPlannedDepartureAt(time.end());
            return stop;
        }).toList();
    }

    // Lượt đón, giao, chạy thẳng, chuyển xe: điểm dừng do điều phối lập
    private VehicleTrip local(VehicleTripCreateRequestDto request, Vehicle vehicle) {
        if (request.departureId() != null) {
            throw new DispatchRuleException("departureId", "Chỉ lượt xe trục mới gắn với chuyến.");
        }
        List<TripStopRequestDto> requested = request.stopsOrEmpty();
        if (requested.size() < 2) {
            throw new DispatchRuleException("stops", "Lượt xe cần ít nhất hai điểm dừng.");
        }
        TripStopRequestDto first = requested.getFirst();
        TripStopRequestDto last = requested.getLast();
        DispatchPolicy.requireStartsAndEndsAtNetworkLocation(first.locationId() != null && first.bookingAddressId() == null,
                last.locationId() != null && last.bookingAddressId() == null);
        Interval window = DispatchPolicy.requireStopSchedule(
                requested.stream().map(s -> new Interval(s.plannedArrivalAt(), s.plannedDepartureAt())).toList());
        Location startLocation = findLocation(first.locationId());
        Location endLocation = findLocation(last.locationId());
        DispatchPolicy.requireEndsAtHub(request.tripType(), last.stopType(), endLocation.getLocationType());
        return request.toEntity(vehicle, null, startLocation, endLocation, window.start(), window.end());
    }

    private List<TripStop> localStops(VehicleTrip trip, List<TripStopRequestDto> requested) {
        List<TripStop> result = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            TripStopRequestDto stop = requested.get(i);
            if ((stop.locationId() == null) == (stop.bookingAddressId() == null)) {
                throw new DispatchRuleException("stops", "Điểm dừng " + (i + 1) + " phải là một điểm mạng lưới hoặc một địa chỉ của đơn.");
            }
            if (stop.bookingAddressId() != null) {
                result.add(stop.toEntity(trip, i + 1, null, customerAddress(stop, i + 1)));
            } else {
                if (stop.stopType() == TripStopType.PICKUP || stop.stopType() == TripStopType.DELIVERY || stop.stopType() == TripStopType.QUARANTINE) {
                    throw new DispatchRuleException("stops", "Điểm dừng " + (i + 1) + " ở điểm mạng lưới không dùng loại " + stop.stopType() + ".");
                }
                result.add(stop.toEntity(trip, i + 1, findLocation(stop.locationId()), null));
            }
        }
        return result;
    }

    private BookingAddress customerAddress(TripStopRequestDto stop, int sequence) {
        BookingAddress address = bookingAddresses.findById(stop.bookingAddressId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ của đơn " + stop.bookingAddressId()));
        requireCustomerAddress(address, stop.stopType(), sequence);
        return address;
    }

    @Override
    @Transactional(readOnly = true)
    public TripStop planRestStop(VehicleTrip trip, int sequence, TripStopRequestDto request) {
        var location = findLocation(request.locationId());
        if (request.stopType() != TripStopType.REST || location.getLocationType() != LocationType.REST_STOP
                || location.getStatus() != ActiveStatus.ACTIVE) {
            throw new DispatchRuleException("stops", "Điểm nghỉ phải là trạm nghỉ đang hoạt động.", true);
        }
        return request.toEntity(trip, sequence, location, null);
    }

    private static void requireCustomerAddress(BookingAddress address, TripStopType stopType, int sequence) {
        TripStopType expected = address.getAddressType() == AddressRole.PICKUP ? TripStopType.PICKUP : TripStopType.DELIVERY;
        if (stopType != expected) {
            throw new DispatchRuleException("stops", "Điểm dừng " + sequence + " là địa chỉ " + address.getAddressType() + " nên phải là điểm " + expected + ".");
        }
        if (!DISPATCHABLE_BOOKINGS.contains(address.getBooking().getStatus())) {
            throw new DispatchRuleException("stops", "Đơn " + address.getBooking().getCode() + " đang " + address.getBooking().getStatus() + ", chưa xếp xe được.", true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> manualTripIds(List<Long> ids) {
        return ids.isEmpty() ? Set.of() : auditLogs.findTargetsWithAction("vehicle_trip", ids, MANUAL_CREATION);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireLocalStops(VehicleTrip trip, List<TripStop> planned) {
        var first = planned.getFirst();
        var last = planned.getLast();
        DispatchPolicy.requireStartsAndEndsAtNetworkLocation(first.getLocation() != null && first.getBookingAddress() == null,
                last.getLocation() != null && last.getBookingAddress() == null);
        DispatchPolicy.requireEndsAtHub(trip.getTripType(), last.getStopType(), last.getLocation().getLocationType());
        planned.stream().filter(stop -> stop.getBookingAddress() != null)
                .forEach(stop -> requireCustomerAddress(stop.getBookingAddress(), stop.getStopType(), stop.getSequenceNumber()));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireVehicleSchedule(Vehicle vehicle, VehicleTrip trip, Interval window) {
        var others = otherTrips(vehicle, trip);
        DispatchPolicy.requireNoOverlap("vehicleId", "Xe", window,
                others.stream().map(TripPlanningServiceImpl::interval).toList());
        var proposed = DispatchPolicy.neighbors(others, window);
        requireAtStart(vehicle, trip, proposed.previous(), "Xe");
        if (proposed.next() != null) {
            requireAtStart(vehicle, proposed.next(), trip, "Lượt sau của xe");
        }

        if (trip.getId() == null) return;
        // Khi bỏ vị trí cũ, lượt sau của xe cũ vẫn phải nối được từ lượt liền trước.
        Vehicle oldVehicle = trip.getVehicle();
        boolean sameVehicle = oldVehicle.getId().equals(vehicle.getId());
        var previous = DispatchPolicy.neighbors(sameVehicle ? others : otherTrips(oldVehicle, trip), interval(trip));
        if (previous.next() != null && (!sameVehicle || !previous.equals(proposed))) {
            requireAtStart(oldVehicle, previous.next(), previous.previous(), "Lượt sau của xe cũ");
        }
    }

    private List<VehicleTrip> otherTrips(Vehicle vehicle, VehicleTrip trip) {
        return trips.findByVehicle_IdAndStatusNot(vehicle.getId(), VehicleTripStatus.CANCELLED).stream()
                .filter(other -> !Objects.equals(other.getId(), trip.getId())).toList();
    }

    private static void requireAtStart(Vehicle vehicle, VehicleTrip trip, VehicleTrip previous, String subject) {
        Location expected = previous == null ? vehicle.getHomeHubLocation() : previous.getEndLocation();
        DispatchPolicy.requireStartsWherePreviousEnded("vehicleId", subject, trip.getStartLocation().getId(),
                expected.getId(), expected.getName());
    }

    private static Interval interval(VehicleTrip trip) {
        return new Interval(trip.getPlannedStartAt(), trip.getPlannedEndAt());
    }

    private Location findLocation(Long id) {
        return locations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm " + id));
    }

    private String newCode() {
        String code = VehicleTripCodes.newCode();
        while (trips.existsByCode(code)) {
            code = VehicleTripCodes.newCode();
        }
        return code;
    }
}
