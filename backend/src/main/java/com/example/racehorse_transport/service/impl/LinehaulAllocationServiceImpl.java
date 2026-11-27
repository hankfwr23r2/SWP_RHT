package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.HorseLegRequestDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulApplyRequestDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulApplyRequestDto.TruckChoiceDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto.LinehaulHorseDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto.PlateDto;
import com.example.racehorse_transport.dto.dispatch.LinehaulSuggestionResponseDto.TruckDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripCreateRequestDto;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.DepartureVehicle;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;
import com.example.racehorse_transport.entity.enums.TransportMode;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.LinehaulAllocationPolicy;
import com.example.racehorse_transport.policy.LinehaulAllocationPolicy.BookingLoad;
import com.example.racehorse_transport.policy.LinehaulAllocationPolicy.Slot;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.policy.TripGroupingPolicy.Point;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.DepartureVehicleRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.HorseLegService;
import com.example.racehorse_transport.service.LinehaulAllocationService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.TripPlanningService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinehaulAllocationServiceImpl implements LinehaulAllocationService {
    private static final Set<BookingStatus> READY = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    private final DepartureRepository departures;
    private final DepartureVehicleRepository allotments;
    private final BookingHorseRepository horses;
    private final BookingAddressRepository addresses;
    private final VehicleRepository vehicles;
    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final SystemConfigService config;
    private final TripPlanningService tripPlanning;
    private final HorseLegService horseLegs;

    @Override
    @Transactional(readOnly = true)
    public LinehaulSuggestionResponseDto suggest(Long departureId) {
        return suggest(departures.findWithRoute(departureId).orElseThrow(() -> missingDeparture(departureId)));
    }

    @Override
    @Transactional
    public List<Long> apply(Long departureId, LinehaulApplyRequestDto request, String username) {
        // Same lock order as manual trip creation: vehicles before departure.
        var vehicleIds = request.trucks().stream().map(TruckChoiceDto::vehicleId).distinct().sorted().toList();
        if (vehicleIds.size() != request.trucks().size()) throw conflict("Mỗi lượt trục phải chọn một biển số khác nhau.");
        Map<Long, Vehicle> lockedVehicles = new HashMap<>();
        for (Long vehicleId : vehicleIds) {
            lockedVehicles.put(vehicleId, vehicles.findForUpdate(vehicleId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe " + vehicleId)));
        }
        Departure departure = departures.findForUpdate(departureId).orElseThrow(() -> missingDeparture(departureId));
        var suggestion = suggest(departure);
        if (suggestion.missingSeats() > 0 || suggestion.trucks().size() != request.trucks().size()) {
            throw conflict("Gợi ý đã thay đổi hoặc thiếu chỗ. Hãy tải lại gợi ý.");
        }
        Map<TruckDto, Long> selected = new LinkedHashMap<>();
        for (TruckDto truck : suggestion.trucks()) {
            var choice = request.trucks().stream().filter(row -> row.allotmentId() == truck.allotmentId() && row.number() == truck.number())
                    .findFirst().orElseThrow(() -> conflict("Danh sách xe của chuyến đã thay đổi."));
            var expected = truck.horses().stream().map(LinehaulHorseDto::bookingHorseId).collect(Collectors.toSet());
            if (!expected.equals(new HashSet<>(choice.bookingHorseIds())) || expected.size() != choice.bookingHorseIds().size()) {
                throw conflict("Danh sách ngựa của gợi ý đã thay đổi.");
            }
            if (truck.vehicles().stream().noneMatch(vehicle -> vehicle.id() == choice.vehicleId())) {
                throw conflict("Xe được chọn phải đang hoạt động ở hub xuất phát, đúng hạng và đủ chỗ.");
            }
            selected.put(truck, choice.vehicleId());
        }

        int sharedCapacity = selected.entrySet().stream().filter(entry -> entry.getKey().allotmentType() != DepartureAllotmentType.PRIVATE)
                .mapToInt(entry -> capacity(lockedVehicles.get(entry.getValue()))).sum();
        if (sharedCapacity < departure.getBookedStallCount()) {
            throw conflict("Tổng số chỗ trên các xe ghép đã chọn ít hơn số chỗ đã bán.");
        }

        List<Long> created = new ArrayList<>();
        for (var entry : selected.entrySet()) {
            long trip = tripPlanning.createTrip(new VehicleTripCreateRequestDto(
                    VehicleTripType.LINEHAUL, departureId, entry.getValue(), null), username, false);
            var tripStops = stops.findByVehicleTrip_IdOrderBySequenceNumber(trip);
            for (var horse : entry.getKey().horses()) {
                horseLegs.assign(trip, new HorseLegRequestDto(horse.bookingHorseId(),
                        tripStops.getFirst().getId(), tripStops.getLast().getId()), username);
            }
            created.add(trip);
        }
        return created;
    }

    private LinehaulSuggestionResponseDto suggest(Departure departure) {
        DispatchPolicy.requireRunnableDeparture(departure.getStatus(), departure.getRunConfirmedAt() != null);
        if (trips.existsByDeparture_IdAndTripTypeAndStatusNotIn(departure.getId(), VehicleTripType.LINEHAUL,
                Set.of(VehicleTripStatus.CANCELLED))) {
            throw conflict("Chuyến đã có lượt trục. Hãy quản lý các lượt hiện có.");
        }
        Map<Long, List<BookingHorse>> byBooking = horses.findForLinehaul(departure.getId(), BookingHorseStatus.ACTIVE, READY).stream()
                .collect(Collectors.groupingBy(h -> h.getBooking().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, BookingAddress> deliveries = new HashMap<>();
        List<BookingLoad> loads = new ArrayList<>();
        for (var entry : byBooking.entrySet()) {
            var booking = entry.getValue().getFirst().getBooking();
            var fallback = addresses.findByBooking_Id(booking.getId()).stream().filter(a -> a.getAddressType() == AddressRole.DELIVERY)
                    .min(java.util.Comparator.comparing(BookingAddress::getId)).orElse(null);
            List<Point> points = new ArrayList<>();
            for (var horse : entry.getValue()) {
                var address = horse.getDeliveryAddress() == null ? fallback : horse.getDeliveryAddress();
                if (address != null) deliveries.put(horse.getId(), address);
                if (address != null && address.getLatitude() != null && address.getLongitude() != null) {
                    points.add(new Point(address.getLatitude().doubleValue(), address.getLongitude().doubleValue()));
                }
            }
            loads.add(new BookingLoad(booking.getId(), booking.getTransportMode() == TransportMode.PRIVATE,
                    entry.getValue().stream().mapToInt(h -> StallSeatPolicy.seats(h.getStallType().getCode())).sum(),
                    points.size() == entry.getValue().size() ? points : List.of()));
        }
        var fleet = allotments.findAllotments(departure.getId());
        var slots = fleet.stream().flatMap(row -> IntStream.rangeClosed(1, row.getVehicleCount()).mapToObj(number ->
                new Slot(row.getId(), number, row.getBooking() == null ? null : row.getBooking().getId(),
                        row.getVehicleClass().getMaxStallCount()))).toList();
        var plan = LinehaulAllocationPolicy.suggest(loads, slots, departure.getBookedStallCount(),
                config.getInt("LINEHAUL_CLUSTER_RADIUS_KM"));
        var byAllotment = fleet.stream().collect(Collectors.toMap(DepartureVehicle::getId, row -> row));
        var stock = tripPlanning.vehiclesFreeForLinehaul(departure, vehicles.findAllByOrderByLicensePlateAsc().stream()
                .filter(vehicle -> vehicle.getStatus() == VehicleStatus.ACTIVE
                        && vehicle.getHomeHubLocation().getId().equals(departure.getRoute().getOrigin().getId())).toList());
        var result = plan.allocations().stream().map(allocation -> {
            var row = byAllotment.get(allocation.slot().allotmentId());
            var assigned = allocation.loads().stream().flatMap(load -> byBooking.get(load.booking().bookingId()).stream().map(horse -> {
                var address = deliveries.get(horse.getId());
                return LinehaulHorseDto.from(horse, load.cluster(), address == null ? null : address.getAddressLine());
            })).toList();
            var plates = stock.stream().filter(vehicle -> vehicle.getVehicleClass().getId().equals(row.getVehicleClass().getId())
                    && capacity(vehicle) >= allocation.seats()).map(PlateDto::from).toList();
            return TruckDto.from(row, allocation.slot().number(), allocation.seats(), assigned, plates);
        }).toList();
        return new LinehaulSuggestionResponseDto(plan.missingSeats(), result);
    }

    private static int capacity(Vehicle vehicle) {
        return vehicle.getStandardStallCount() * StallSeatPolicy.seats("STANDARD")
                + vehicle.getWideStallCount() * StallSeatPolicy.seats("WIDE");
    }

    private static ResourceNotFoundException missingDeparture(Long id) {
        return new ResourceNotFoundException("Không tìm thấy chuyến " + id);
    }

    private static DispatchRuleException conflict(String message) {
        return new DispatchRuleException("trucks", message, true);
    }
}
