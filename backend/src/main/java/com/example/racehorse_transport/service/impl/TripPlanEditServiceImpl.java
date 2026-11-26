package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.VehicleTripUpdateRequestDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.TripStopRequestDto;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.service.CrewPositionService;
import com.example.racehorse_transport.service.HorseLegService;
import com.example.racehorse_transport.service.StallPlanService;
import com.example.racehorse_transport.service.TripPlanEditService;
import com.example.racehorse_transport.service.TripPlanningService;
import com.example.racehorse_transport.service.TripScheduleService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.TripProgressNoticeService;
import com.example.racehorse_transport.service.VehicleService;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.Duration;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripPlanEditServiceImpl implements TripPlanEditService {

    private final CoordinatorAssignmentService coordinators;
    private final VehicleService vehicles;
    private final TripStopRepository stops;
    private final TripPlanningService planning;
    private final CrewPositionService crewPositions;
    private final HorseLegService horseLegs;
    private final StallPlanService stallPlans;
    private final TripProgressNoticeService notices;
    private final ObjectProvider<TripScheduleService> schedules;
    private final SystemConfigService configs;

    @Override
    @Transactional
    public void updateTrip(Long tripId, VehicleTripUpdateRequestDto request, String username) {
        VehicleTrip trip = coordinators.requireAssignedTripWrite(tripId, username);
        boolean changeTimes = request.plannedStartAt() != null || request.stops() != null;
        DispatchPolicy.requireTripPlanEditable(trip.getStatus(), trip.getActualStartAt() != null, trip.getTripType(), changeTimes);
        if (!changeTimes && request.vehicleId() == null) {
            throw new DispatchRuleException("vehicleId", "Chọn xe hoặc giờ mới cho lượt xe.");
        }
        if (request.plannedStartAt() != null && request.stops() != null) {
            throw new DispatchRuleException("stops", "Chọn dời cả lượt hoặc sửa giờ từng điểm dừng.");
        }

        List<TripStop> planned = stops.findByVehicleTrip_IdOrderBySequenceNumber(tripId);
        if (planned.stream().anyMatch(stop -> stop.getActualArrivalAt() != null || stop.getActualDepartureAt() != null)) {
            throw new DispatchRuleException("status", "Lượt xe đã ghi nhận giờ thực tế tại điểm dừng.", true);
        }
        List<Interval> currentTimes = planned.stream().map(TripPlanEditServiceImpl::interval).toList();
        List<Interval> revisedTimes = proposedTimes(trip, planned, currentTimes, request);
        Long currentVehicle = trip.getVehicle().getId();
        Long vehicleId = request.vehicleId() == null ? currentVehicle : request.vehicleId();
        boolean vehicleChanged = !vehicleId.equals(currentVehicle);
        if (!vehicleChanged && revisedTimes.equals(currentTimes)) return;

        Interval previous = new Interval(trip.getPlannedStartAt(), trip.getPlannedEndAt());
        boolean linehaul = trip.getTripType() == VehicleTripType.LINEHAUL;
        if (changeTimes && Set.of(VehicleTripType.PICKUP, VehicleTripType.DELIVERY, VehicleTripType.DIRECT).contains(trip.getTripType())) {
            var restPlan = replanWithRest(trip, planned, revisedTimes);
            if (restPlan != null) {
                planned = applyRestPlan(trip, planned, restPlan.stops());
                revisedTimes = restPlan.stops().stream().map(stop -> new Interval(stop.arrivalAt(), stop.departureAt())).toList();
            }
        }
        Interval window = linehaul ? previous : DispatchPolicy.requireStopSchedule(revisedTimes);
        Vehicle vehicle = lockVehicles(currentVehicle, vehicleId);
        DispatchPolicy.requireActiveVehicle(vehicle.getStatus());
        planning.requireVehicleSchedule(vehicle, trip, window);

        request.applyTo(trip, vehicle, window.start(), window.end());
        for (int i = 0; i < planned.size(); i++) {
            planned.get(i).setPlannedArrivalAt(revisedTimes.get(i).start());
            planned.get(i).setPlannedDepartureAt(revisedTimes.get(i).end());
        }
        var crew = crewPositions.revalidateTrip(trip, previous, revisedTimes);
        var legs = horseLegs.replanTrip(trip, planned);
        if (linehaul) {
            var departure = trip.getDeparture();
            DispatchPolicy.requireRunnableDeparture(departure.getStatus(), departure.getRunConfirmedAt() != null);
        } else {
            planning.requireLocalStops(trip, planned);
        }
        if (vehicleChanged) stallPlans.repack(trip, legs);
        notices.planChanged(trip, crew, legs);
    }

    private PlannedTripResponseDto replanWithRest(VehicleTrip trip, List<TripStop> planned, List<Interval> revised) {
        DispatchPolicy.requireStopSchedule(revised);
        var horses = horseLegs.horsesOnTrip(trip.getId()).stream().map(horse -> horse.leg())
                .filter(leg -> leg.status() == HorseLegStatus.PLANNED).toList();
        if (horses.isEmpty()) return null;
        int minRest = configs.getInt("MIN_REST_MINUTES");
        var rules = new TripSchedulePolicy.Rules(0, minRest, 0, 0, 0, 0, 0, configs.getInt("HORSE_REST_INTERVAL_HOURS"));
        var rests = IntStream.range(0, planned.size()).filter(index -> planned.get(index).getStopType() == TripStopType.REST
                && Duration.between(revised.get(index).start(), revised.get(index).end()).toMinutes() >= minRest)
                .boxed().collect(Collectors.toSet());
        var indexById = IntStream.range(0, planned.size()).boxed().collect(Collectors.toMap(index -> planned.get(index).getId(), index -> index));
        var times = revised.stream().map(time -> new TripSchedulePolicy.StopTime(time.start(), time.end(), 0)).toList();
        if (horses.stream().noneMatch(leg -> TripSchedulePolicy.firstRestGap(indexById.get(leg.boardTripStopId()),
                indexById.get(leg.alightTripStopId()), times, rests, rules) != null)) return null;
        var places = planned.stream().map(TripPlanEditServiceImpl::place).toList();
        // The planner also uses dispatch to create trips. Resolve it only for edits that need new road times.
        var plan = schedules.getObject().plan(new PlannedTripRequestDto(trip.getTripType(), null, places,
                horses.stream().map(leg -> leg.bookingHorseId()).toList(), revised.getFirst().end()));
        if (plan.blocked()) {
            throw new DispatchRuleException("stops", "Kế hoạch có trạm nghỉ vượt giới hạn giờ của lượt xe.", true);
        }
        return plan;
    }

    private List<TripStop> applyRestPlan(VehicleTrip trip, List<TripStop> previous, List<PlannedTripResponseDto.PlannedStopDto> revised) {
        if (previous.size() == revised.size()) return previous;
        int offset = previous.getLast().getSequenceNumber() + revised.size();
        for (TripStop stop : previous) {
            stop.setSequenceNumber(stop.getSequenceNumber() + offset);
        }
        stops.flush();

        List<TripStop> ordered = new ArrayList<>();
        int next = 0;
        for (var stop : revised) {
            int sequence = ordered.size() + 1;
            var proposed = new PlannedTripRequestDto.PlaceDto(stop.locationId(), stop.bookingAddressId());
            if (next < previous.size() && Objects.equals(place(previous.get(next)), proposed)) {
                TripStop existing = previous.get(next++);
                existing.setSequenceNumber(sequence);
                ordered.add(existing);
            } else {
                ordered.add(stops.save(planning.planRestStop(trip, sequence, new TripStopRequestDto(
                        stop.locationId(), null, stop.stopType(), stop.arrivalAt(), stop.departureAt()))));
            }
        }
        return ordered;
    }

    private static PlannedTripRequestDto.PlaceDto place(TripStop stop) {
        return new PlannedTripRequestDto.PlaceDto(stop.getLocation() == null ? null : stop.getLocation().getId(),
                stop.getBookingAddress() == null ? null : stop.getBookingAddress().getId());
    }

    private List<Interval> proposedTimes(VehicleTrip trip, List<TripStop> planned, List<Interval> currentTimes,
                                         VehicleTripUpdateRequestDto request) {
        if (request.plannedStartAt() != null) {
            return DispatchPolicy.shiftStops(currentTimes, trip.getPlannedStartAt(), request.plannedStartAt());
        }
        if (request.stops() == null) return currentTimes;
        if (!planning.manualTripIds(List.of(trip.getId())).contains(trip.getId())) {
            throw new DispatchRuleException("stops", "Chỉ lượt lập bằng giờ nhập tay được sửa giờ từng điểm dừng.", true);
        }
        var stopIds = planned.stream().map(TripStop::getId).collect(Collectors.toSet());
        Map<Long, Interval> updates = new HashMap<>();
        for (int i = 0; i < request.stops().size(); i++) {
            var stop = request.stops().get(i);
            if (!stopIds.contains(stop.stopId())) {
                throw new DispatchRuleException("stops[" + i + "].stopId", "Điểm dừng không thuộc lượt xe này.");
            }
            if (updates.putIfAbsent(stop.stopId(), new Interval(stop.plannedArrivalAt(), stop.plannedDepartureAt())) != null) {
                throw new DispatchRuleException("stops[" + i + "].stopId", "Mỗi điểm dừng chỉ được gửi một lần.");
            }
        }
        return planned.stream().map(stop -> updates.getOrDefault(stop.getId(), interval(stop))).toList();
    }

    // Giữ cùng thứ tự khóa với đổi xe do sự cố.
    private Vehicle lockVehicles(Long currentId, Long vehicleId) {
        Vehicle first = vehicles.lockVehicle(Math.min(currentId, vehicleId));
        if (currentId.equals(vehicleId)) return first;
        Vehicle second = vehicles.lockVehicle(Math.max(currentId, vehicleId));
        return vehicleId.equals(first.getId()) ? first : second;
    }

    private static Interval interval(TripStop stop) {
        return new Interval(stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt());
    }
}
