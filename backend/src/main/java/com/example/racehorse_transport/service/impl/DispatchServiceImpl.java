package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.policy.DepartureDelayPolicy;

import com.example.racehorse_transport.dto.dispatch.CrewAssignmentResponseDto;
import com.example.racehorse_transport.dto.dispatch.TripStopRequestDto;
import com.example.racehorse_transport.dto.dispatch.TripStopResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCancelRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.DispatchVehicleResponseDto;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RouteStopRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.VehicleService;
import com.example.racehorse_transport.util.VehicleTripCodes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    // Đơn đã cọc (đi đón) hoặc đang vận hành (đi giao) mới được xếp vào lượt xe
    private static final Set<BookingStatus> DISPATCHABLE_BOOKINGS = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    private static final Set<DepartureStatus> RUNNABLE_DEPARTURES = Set.of(DepartureStatus.OPEN, DepartureStatus.CLOSED, DepartureStatus.IN_PROGRESS);

    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final CrewAssignmentRepository crew;
    private final HorseLegRepository legs;
    private final VehicleService vehicles;
    private final DepartureService departures;
    private final RouteStopRepository routeStops;
    private final LocationRepository locations;
    private final BookingAddressRepository bookingAddresses;
    private final NotificationService notifications;

    @Override
    @Transactional(readOnly = true)
    public List<VehicleTripResponseDto> trips(Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw new DispatchRuleException("to", "Khoảng thời gian không hợp lệ.");
        }
        return describe(trips.findOverlapping(from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleTripResponseDto trip(Long id) {
        return describe(List.of(findTrip(id))).getFirst();
    }

    @Override
    @Transactional
    public VehicleTripResponseDto createTrip(VehicleTripCreateRequestDto request) {
        Vehicle vehicle = vehicles.lockVehicle(request.vehicleId());
        DispatchPolicy.requireActiveVehicle(vehicle.getStatus());
        VehicleTrip trip = request.tripType() == VehicleTripType.LINEHAUL ? linehaul(request, vehicle) : local(request, vehicle);
        // Kiểm và dựng đủ điểm dừng trước khi ghi gì xuống DB
        List<TripStop> plannedStops = request.tripType() == VehicleTripType.LINEHAUL
                ? linehaulStops(trip)
                : localStops(trip, request.stopsOrEmpty());
        requireVehicleFree(vehicle, trip);
        trip.setCode(newCode());
        trip.setStatus(VehicleTripStatus.PLANNED);
        VehicleTrip saved = trips.save(trip);
        stops.saveAll(plannedStops);
        return trip(saved.getId());
    }

    @Override
    @Transactional
    public VehicleTripResponseDto cancelTrip(Long id, VehicleTripCancelRequestDto request) {
        VehicleTrip trip = trips.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + id));
        if (trip.getStatus() != VehicleTripStatus.PLANNED && trip.getStatus() != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("status", "Chỉ hủy được lượt xe chưa chạy.", true);
        }
        if (legs.existsByVehicleTrip_IdAndStatusNot(id, HorseLegStatus.CANCELLED)) {
            throw new DispatchRuleException("status", "Lượt xe còn ngựa. Gỡ ngựa khỏi lượt trước khi hủy.", true);
        }
        trip.setStatus(VehicleTripStatus.CANCELLED);
        if (trip.getTripType() == VehicleTripType.LINEHAUL) {
            departures.linehaulCompleted(trip.getDeparture().getId());
        }
        for (CrewAssignment assignment : crew.findByVehicleTrip_IdInOrderById(List.of(id))) {
            if (assignment.getStatus() == CrewAssignmentStatus.RELEASED) continue;
            assignment.setStatus(CrewAssignmentStatus.RELEASED);
            assignment.setNote(request.reason().strip());
            notifications.notify(List.of(assignment.getStaff().getUserId()), null,
                    "Lượt xe đã hủy",
                    "Lượt xe " + trip.getCode() + " đã hủy. Lý do: " + request.reason().strip(),
                    "/my-trips");
        }
        return trip(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DispatchVehicleResponseDto> vehicles() {
        return vehicles.vehiclesForDispatch();
    }

    // Lượt trục: theo chuyến, đi từ điểm đầu tới điểm cuối tuyến, giờ theo giờ xuất phát của chuyến
    private VehicleTrip linehaul(VehicleTripCreateRequestDto request, Vehicle vehicle) {
        if (request.departureId() == null) {
            throw new DispatchRuleException("departureId", "Lượt xe trục phải gắn với một chuyến.");
        }
        Departure departure = departures.lockForLinehaulPlanning(request.departureId());
        if (!RUNNABLE_DEPARTURES.contains(departure.getStatus())) {
            throw new DispatchRuleException("departureId", "Chuyến đang " + departure.getStatus() + ", không lập lượt xe được.", true);
        }
        // Chỉ lập lượt trục khi quản lý đã quyết định chạy chuyến (THONG_BAO_LUONG_B, A6b)
        if (departure.getRunConfirmedAt() == null) {
            throw new DispatchRuleException("departureId", "Chuyến chưa được quản lý xác nhận chạy, chưa lập lượt trục được.", true);
        }
        List<RouteStop> route = routeStops.findWithLocation(departure.getRoute().getId());
        if (route.size() < 2) {
            throw new DispatchRuleException("departureId", "Tuyến của chuyến chưa có đủ điểm dừng.");
        }
        Instant start = DepartureDelayPolicy.effectiveTime(departure);
        RouteStop last = route.getLast();
        Instant end = start.plusSeconds(60L * (last.getArrivalOffsetMinutes() + last.getDwellMinutes()));
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
            Instant arrival = start.plusSeconds(60L * routeStop.getArrivalOffsetMinutes());
            stop.setPlannedArrivalAt(arrival);
            stop.setPlannedDepartureAt(arrival.plusSeconds(60L * routeStop.getDwellMinutes()));
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
        Instant start = first.plannedArrivalAt();
        Instant end = last.plannedDepartureAt();
        DispatchPolicy.requirePlannedWindow(start, end);
        DispatchPolicy.requireStopsInOrder(new Interval(start, end),
                requested.stream().map(s -> new Interval(s.plannedArrivalAt(), s.plannedDepartureAt())).toList());
        Location startLocation = findLocation(first.locationId());
        Location endLocation = findLocation(last.locationId());
        DispatchPolicy.requireEndsAtHub(request.tripType(), last.stopType(), endLocation.getLocationType());
        return request.toEntity(vehicle, null, startLocation, endLocation, start, end);
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
        TripStopType expected = address.getAddressType() == AddressRole.PICKUP ? TripStopType.PICKUP : TripStopType.DELIVERY;
        if (stop.stopType() != expected) {
            throw new DispatchRuleException("stops", "Điểm dừng " + sequence + " là địa chỉ " + address.getAddressType() + " nên phải là điểm " + expected + ".");
        }
        if (!DISPATCHABLE_BOOKINGS.contains(address.getBooking().getStatus())) {
            throw new DispatchRuleException("stops", "Đơn " + address.getBooking().getCode() + " đang " + address.getBooking().getStatus() + ", chưa xếp xe được.", true);
        }
        return address;
    }

    @Override
    @Transactional
    public VehicleTripResponseDto swapVehicle(Long tripId, Long vehicleId, Long actorUserId, String reason, Instant changedAt) {
        VehicleTrip trip = trips.findForUpdate(tripId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + tripId));
        if (trip.getStatus() != VehicleTripStatus.IN_PROGRESS) {
            throw new DispatchRuleException("tripId", "Chỉ đổi xe khi lượt xe đang chạy.", true);
        }
        Long currentId = trip.getVehicle().getId();
        if (vehicleId.equals(currentId)) {
            throw new DispatchRuleException("vehicleId", "Chọn xe khác xe đang chạy.");
        }
        // Khóa theo ID để hai yêu cầu đổi xe không giữ khóa theo thứ tự ngược nhau.
        Vehicle first = vehicles.lockVehicle(Math.min(currentId, vehicleId));
        Vehicle second = vehicles.lockVehicle(Math.max(currentId, vehicleId));
        Vehicle current = currentId.equals(first.getId()) ? first : second;
        Vehicle vehicle = vehicleId.equals(first.getId()) ? first : second;
        DispatchPolicy.requireActiveVehicle(vehicle.getStatus());
        // Xe cứu hộ tới từ nơi khác nên không áp quy tắc bắt đầu ở nơi lượt trước kết thúc; chỉ không trùng giờ phần còn lại của lượt
        Interval rest = new Interval(changedAt, trip.getPlannedEndAt().isAfter(changedAt) ? trip.getPlannedEndAt() : changedAt.plusSeconds(60));
        DispatchPolicy.requireNoOverlap("vehicleId", "Xe", rest, trips.findByVehicle_IdAndStatusNot(vehicle.getId(), VehicleTripStatus.CANCELLED).stream()
                .filter(t -> !t.getId().equals(trip.getId())).map(t -> new Interval(t.getPlannedStartAt(), t.getPlannedEndAt())).toList());
        int capacity = vehicle.getStandardStallCount() * StallSeatPolicy.seats("STANDARD") + vehicle.getWideStallCount() * StallSeatPolicy.seats("WIDE");
        int used = legs.findActiveOnTrip(trip.getId(), Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED, HorseLegStatus.COMPLETED)).stream()
                .filter(l -> l.getStallPosition() != null)
                .mapToInt(l -> l.getStallPosition() + StallSeatPolicy.seats(l.getBookingHorse().getStallType().getCode()) - 1).max().orElse(0);
        if (used > capacity) {
            throw new DispatchRuleException("vehicleId", "Xe " + vehicle.getLicensePlate() + " chỉ có " + capacity + " chỗ, sơ đồ khoang đang dùng tới chỗ " + used + ".", true);
        }
        trip.setVehicle(vehicle);
        vehicles.markForMaintenance(current, actorUserId, reason, changedAt);
        trips.flush();
        return trip(trip.getId());
    }

    // Quy tắc #10, #20: xe không trùng giờ; bắt đầu ở nơi lượt trước kết thúc và lượt sau bắt đầu ở nơi lượt này kết thúc
    private void requireVehicleFree(Vehicle vehicle, VehicleTrip trip) {
        List<VehicleTrip> others = trips.findByVehicle_IdAndStatusNot(vehicle.getId(), VehicleTripStatus.CANCELLED);
        Interval planned = new Interval(trip.getPlannedStartAt(), trip.getPlannedEndAt());
        DispatchPolicy.requireNoOverlap("vehicleId", "Xe", planned,
                others.stream().map(t -> new Interval(t.getPlannedStartAt(), t.getPlannedEndAt())).toList());
        Location expectedStart = others.stream().filter(t -> !t.getPlannedEndAt().isAfter(trip.getPlannedStartAt()))
                .max(Comparator.comparing(VehicleTrip::getPlannedEndAt))
                .map(VehicleTrip::getEndLocation)
                .orElse(vehicle.getHomeHubLocation());
        DispatchPolicy.requireStartsWherePreviousEnded("vehicleId", "Xe", trip.getStartLocation().getId(),
                expectedStart.getId(), expectedStart.getName());
        others.stream().filter(t -> !t.getPlannedStartAt().isBefore(trip.getPlannedEndAt()))
                .min(Comparator.comparing(VehicleTrip::getPlannedStartAt))
                .ifPresent(next -> DispatchPolicy.requireStartsWherePreviousEnded("vehicleId", "Lượt sau của xe",
                        next.getStartLocation().getId(), trip.getEndLocation().getId(), trip.getEndLocation().getName()));
    }

    private List<VehicleTripResponseDto> describe(List<VehicleTrip> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> ids = found.stream().map(VehicleTrip::getId).toList();
        Map<Long, List<TripStopResponseDto>> stopsByTrip = stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(ids).stream()
                .collect(Collectors.groupingBy(s -> s.getVehicleTrip().getId(), Collectors.mapping(TripStopResponseDto::from, Collectors.toList())));
        Map<Long, List<CrewAssignmentResponseDto>> crewByTrip = crew.findByVehicleTrip_IdInOrderById(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getVehicleTrip().getId(), Collectors.mapping(CrewAssignmentResponseDto::from, Collectors.toList())));
        return found.stream().map(t -> VehicleTripResponseDto.from(t, stopsByTrip.getOrDefault(t.getId(), List.of()),
                crewByTrip.getOrDefault(t.getId(), List.of()))).toList();
    }

    private VehicleTrip findTrip(Long id) {
        return trips.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + id));
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
