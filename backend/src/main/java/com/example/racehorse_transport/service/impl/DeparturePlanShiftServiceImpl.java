package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.DeparturePlanShiftService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeparturePlanShiftServiceImpl implements DeparturePlanShiftService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final HorseLegRepository legs;
    private final VehicleRepository vehicles;
    private final CrewAssignmentRepository assignments;
    private final StaffRepository staff;
    private final SystemConfigService configs;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Set<Long> lockAffectedTrips(Long departureId) {
        var ids = trips.findIdsForDelayShift(departureId);
        if (ids.isEmpty()) {
            return Set.of();
        }
        var locked = ids.stream().sorted().map(id -> trips.findForUpdate(id).orElseThrow()).toList();
        locked.stream().map(trip -> trip.getVehicle().getId()).distinct().sorted().forEach(vehicles::findForUpdate);
        activeCrew(ids).stream().map(member -> member.getStaff().getUserId()).distinct().sorted().forEach(staff::findForUpdate);
        return Set.copyOf(ids);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void shift(Long departureId, Set<Long> lockedTrips, Duration offset) {
        if (offset.isZero()) {
            return;
        }
        var ids = trips.findIdsForDelayShift(departureId);
        if (!lockedTrips.containsAll(ids)) {
            throw BookingRuleException.conflict("Kế hoạch xe vừa thay đổi. Vui lòng thử lại.");
        }
        if (!ids.isEmpty()) {
            var tripStops = stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(ids).stream()
                    .collect(Collectors.groupingBy(stop -> stop.getVehicleTrip().getId()));
            var tripLegs = legs.findByVehicleTrip_IdIn(ids).stream()
                    .filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED || leg.getStatus() == HorseLegStatus.IN_PROGRESS)
                    .collect(Collectors.groupingBy(leg -> leg.getVehicleTrip().getId()));
            var affected = ids.stream().map(id -> trips.findForUpdate(id).orElseThrow()).toList();
            for (var trip : affected) {
                var stopRows = tripStops.getOrDefault(trip.getId(), List.of());
                var legRows = tripLegs.getOrDefault(trip.getId(), List.of());
                shiftTrip(departureId, trip, stopRows, legRows, offset);
            }
            affected.forEach(this::requireVehicleSchedule);
            activeCrew(ids).stream().collect(Collectors.groupingBy(member -> member.getStaff().getUserId()))
                    .forEach(this::requireCrewSchedule);
        }
    }

    private void shiftTrip(Long departureId, VehicleTrip trip, List<TripStop> stopRows, List<HorseLeg> legRows, Duration offset) {
        boolean linehaul = trip.getTripType() == VehicleTripType.LINEHAUL;
        if ((!linehaul && trip.getActualStartAt() != null)
                || stopRows.stream().anyMatch(stop -> stop.getActualDepartureAt() != null)) {
            throw BookingRuleException.conflict("Lượt " + trip.getCode() + " đã xuất phát. Điều phối cần xử lý lịch lượt xe.");
        }
        if (!linehaul && legRows.stream().anyMatch(leg -> {
            var departure = leg.getBookingHorse().getBooking().getDeparture();
            return departure == null || !departureId.equals(departure.getId());
        })) {
            throw BookingRuleException.conflict("Lượt " + trip.getCode() + " có đơn của chuyến khác. Điều phối cần tách lịch trước khi dời.");
        }
        trip.setPlannedStartAt(trip.getPlannedStartAt().plus(offset));
        trip.setPlannedEndAt(trip.getPlannedEndAt().plus(offset));
        for (TripStop stop : stopRows) {
            stop.setPlannedArrivalAt(stop.getPlannedArrivalAt().plus(offset));
            stop.setPlannedDepartureAt(stop.getPlannedDepartureAt().plus(offset));
        }
        legRows.forEach(leg -> shiftLeg(leg, offset));
    }

    private static void shiftLeg(HorseLeg leg, Duration offset) {
        if (leg.getPlannedStartAt() != null) {
            leg.setPlannedStartAt(leg.getPlannedStartAt().plus(offset));
        }
        if (leg.getPlannedEndAt() != null) {
            leg.setPlannedEndAt(leg.getPlannedEndAt().plus(offset));
        }
    }

    private List<CrewAssignment> activeCrew(List<Long> ids) {
        return assignments.findByVehicleTrip_IdInOrderById(ids).stream()
                .filter(member -> member.getStatus() != CrewAssignmentStatus.RELEASED).toList();
    }

    private void requireVehicleSchedule(VehicleTrip trip) {
        var vehicle = trip.getVehicle();
        var others = trips.findByVehicle_IdAndStatusNot(vehicle.getId(), VehicleTripStatus.CANCELLED).stream()
                .filter(other -> !Objects.equals(other.getId(), trip.getId())).toList();
        DispatchPolicy.requireNoOverlap("revisedDepartureAt", "Xe " + vehicle.getLicensePlate(), interval(trip),
                others.stream().map(DeparturePlanShiftServiceImpl::interval).toList());
        var previousEnd = others.stream().filter(other -> !other.getPlannedEndAt().isAfter(trip.getPlannedStartAt()))
                .max(java.util.Comparator.comparing(VehicleTrip::getPlannedEndAt))
                .map(VehicleTrip::getEndLocation).orElse(vehicle.getHomeHubLocation());
        DispatchPolicy.requireStartsWherePreviousEnded("revisedDepartureAt", "Xe", trip.getStartLocation().getId(),
                previousEnd.getId(), previousEnd.getName());
        others.stream().filter(other -> !other.getPlannedStartAt().isBefore(trip.getPlannedEndAt()))
                .min(java.util.Comparator.comparing(VehicleTrip::getPlannedStartAt))
                .ifPresent(next -> DispatchPolicy.requireStartsWherePreviousEnded("revisedDepartureAt", "Lượt sau của xe",
                        next.getStartLocation().getId(), trip.getEndLocation().getId(), trip.getEndLocation().getName()));
    }

    private void requireCrewSchedule(Long staffId, List<CrewAssignment> changed) {
        var active = assignments.findActiveOfStaff(staffId, CrewAssignmentStatus.RELEASED, VehicleTripStatus.CANCELLED);
        for (var member : changed) {
            var trip = member.getVehicleTrip();
            DispatchPolicy.requireNoOverlap("revisedDepartureAt", member.getStaff().getFullName(), interval(trip), active.stream()
                    .map(CrewAssignment::getVehicleTrip).filter(other -> !other.getId().equals(trip.getId()))
                    .map(DeparturePlanShiftServiceImpl::interval).toList());
        }
        var changedDriverTrips = changed.stream().filter(member -> member.getCrewRole() == CrewRole.DRIVER)
                .map(member -> member.getVehicleTrip().getId()).collect(Collectors.toSet());
        if (!changedDriverTrips.isEmpty()) {
            requireDriverHours(active, changedDriverTrips, changed.getFirst().getStaff().getFullName());
        }
    }

    private void requireDriverHours(List<CrewAssignment> active, Set<Long> changed, String name) {
        Set<Long> ids = active.stream().filter(member -> member.getCrewRole() == CrewRole.DRIVER)
                .map(member -> member.getVehicleTrip().getId()).collect(Collectors.toSet());
        Map<Long, List<Interval>> times = stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(ids).stream()
                .collect(Collectors.groupingBy(stop -> stop.getVehicleTrip().getId(), Collectors.mapping(
                        stop -> new Interval(stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt()), Collectors.toList())));
        Rules rules = new Rules(configs.getInt("MAX_CONTINUOUS_HOURS"), configs.getInt("MIN_REST_MINUTES"),
                configs.getInt("MAX_DRIVE_HOURS_PER_DAY"), 0, 0);
        var total = TripSchedulePolicy.drivingByDay(times.values(), BUSINESS_ZONE, rules);
        var changedWindows = times.entrySet().stream().filter(row -> changed.contains(row.getKey())).map(Map.Entry::getValue).toList();
        total.keySet().retainAll(TripSchedulePolicy.drivingByDay(changedWindows, BUSINESS_ZONE, rules).keySet());
        TripSchedulePolicy.requireDailyDriving("revisedDepartureAt", name, total, rules);
    }

    private static Interval interval(VehicleTrip trip) {
        return new Interval(trip.getPlannedStartAt(), trip.getPlannedEndAt());
    }
}
