package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto.PlannedStopDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto.Warning;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy.StopTime;
import com.example.racehorse_transport.service.PricingRouteService;
import com.example.racehorse_transport.service.TripScheduleDataService;
import com.example.racehorse_transport.service.TripScheduleDataService.PlaceInput;
import com.example.racehorse_transport.service.TripScheduleDataService.TripInput;
import com.example.racehorse_transport.service.TripScheduleService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripScheduleServiceImpl implements TripScheduleService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Số điểm tối đa của đường vẽ mỗi đoạn, đủ để hiện trên bản đồ
    private static final int MAX_PATH_POINTS = 120;

    private final TripScheduleDataService data;
    private final PricingRouteService roads;
    private final Clock clock;

    @Override
    public PlannedTripResponseDto plan(PlannedTripRequestDto request) {
        return compute(request, data.load(request));
    }

    @Override
    public VehicleTripResponseDto create(PlannedTripRequestDto request) {
        if (request.vehicleId() == null) {
            throw new DispatchRuleException("vehicleId", "Vui lòng chọn xe.");
        }
        TripInput input = data.load(request);
        PlannedTripResponseDto plan = compute(request, input);
        if (plan.blocked()) {
            throw new DispatchRuleException("places", message(plan.warnings(), input), true);
        }
        return data.save(request, input, plan);
    }

    private PlannedTripResponseDto compute(PlannedTripRequestDto request, TripInput input) {
        boolean direct = request.tripType() == VehicleTripType.DIRECT;
        List<GeoPointDto> points = input.places().stream().map(PlaceInput::point).toList();
        Instant around = request.startAt() != null ? request.startAt()
                : input.readyAt() != null ? input.readyAt()
                : input.pickupDate() != null ? input.pickupDate().atTime(input.pickupWindow().startHour(), 0).atZone(BUSINESS_ZONE).toInstant()
                : clock.instant();
        List<RoadRouteDto.Leg> legs = roads.legs(points, LocalDateTime.ofInstant(around, BUSINESS_ZONE), input.weightTons(), input.topSpeedKmh());
        List<Long> drives = legs.stream().map(RoadRouteDto.Leg::driveSeconds).toList();
        List<Integer> horses = input.horsesAtStops();

        Instant start = request.startAt();
        if (start == null) {
            start = switch (request.tripType()) {
                case PICKUP, DIRECT -> TripSchedulePolicy.pickupDeparture(requirePickupDate(input), input.pickupWindow(), BUSINESS_ZONE, drives, input.rules());
                case DELIVERY -> TripSchedulePolicy.deliveryDeparture(
                        requireReady(input).plus(TripSchedulePolicy.dwell(horses.getFirst(), input.rules())),
                        input.deliveryWindow(), BUSINESS_ZONE, horses, drives, input.rules());
                default -> throw new DispatchRuleException("startAt", "Vui lòng chọn giờ xuất phát.");
            };
        }
        List<StopTime> times = TripSchedulePolicy.schedule(start, horses, drives, input.rules());
        Set<Warning> warnings = warnings(request.tripType(), input, drives, times, direct);

        List<PlannedStopDto> stops = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            PlaceInput place = input.places().get(i);
            StopTime time = times.get(i);
            RoadRouteDto.Leg leg = i == 0 ? null : legs.get(i - 1);
            stops.add(new PlannedStopDto(place.locationId(), place.bookingAddressId(), place.stopType(), place.name(), place.bookingCode(),
                    place.point(), horses.get(i), time.arrival(), time.departure(),
                    leg == null ? 0 : Math.round(leg.driveSeconds() / 60.0), leg == null ? 0 : Math.round(leg.km() * 10) / 10.0,
                    time.restMinutes(), leg == null ? List.of() : thin(leg.shape())));
        }
        return new PlannedTripResponseDto(times.getFirst().arrival(), times.getLast().departure(),
                Math.round(drives.stream().mapToLong(Long::longValue).sum() / 60.0),
                Math.round(legs.stream().mapToDouble(RoadRouteDto.Leg::km).sum() * 10) / 10.0,
                warnings.stream().anyMatch(Warning::blocking), List.copyOf(warnings), stops);
    }

    private static Set<Warning> warnings(VehicleTripType type, TripInput input, List<Long> drives, List<StopTime> times, boolean direct) {
        Set<Warning> result = EnumSet.noneOf(Warning.class);
        long limit = TripSchedulePolicy.rideLimitSeconds(direct, input.rules());
        if (input.horses().stream().anyMatch(h -> TripSchedulePolicy.rideSeconds(drives, h.board(), h.alight()) > limit)) {
            result.add(Warning.RIDE_TOO_LONG);
        }
        if (type == VehicleTripType.PICKUP && input.hubDeadline() != null && times.getLast().arrival().isAfter(input.hubDeadline())) {
            result.add(Warning.LATE_FOR_HUB);
        }
        long dayLimit = input.rules().maxDriveHoursPerDay() * 3600L;
        List<Interval> intervals = times.stream().map(t -> new Interval(t.arrival(), t.departure())).toList();
        if (TripSchedulePolicy.drivingByDay(List.of(intervals), BUSINESS_ZONE, input.rules()).values().stream().anyMatch(s -> s > dayLimit)) {
            result.add(Warning.DRIVER_DAY_LIMIT);
        }
        for (int i = 0; i < times.size(); i++) {
            var window = switch (input.places().get(i).stopType()) {
                case PICKUP -> input.pickupWindow();
                case DELIVERY -> input.deliveryWindow();
                default -> null;
            };
            if (window != null && !TripSchedulePolicy.inWindow(times.get(i).arrival(), window, BUSINESS_ZONE)) {
                result.add(Warning.OUTSIDE_WINDOW);
            }
        }
        if (type == VehicleTripType.PICKUP && !TripSchedulePolicy.inWindow(times.getLast().arrival(), input.pickupWindow(), BUSINESS_ZONE)) {
            result.add(Warning.OUTSIDE_WINDOW);
        }
        return result;
    }

    private static String message(List<Warning> warnings, TripInput input) {
        if (warnings.contains(Warning.RIDE_TOO_LONG)) {
            return "Có con ngựa ở trên xe quá giới hạn giờ lái một chiều. Bỏ bớt điểm hoặc tách lượt.";
        }
        if (warnings.contains(Warning.LATE_FOR_HUB)) {
            return "Ngựa tới hub sau hạn có mặt của chuyến trục. Chọn giờ xuất phát sớm hơn.";
        }
        return "Lượt xe lái quá " + input.rules().maxDriveHoursPerDay() + " giờ trong một ngày.";
    }

    private static java.time.LocalDate requirePickupDate(TripInput input) {
        if (input.pickupDate() == null) {
            throw new DispatchRuleException("startAt", "Chưa có ngựa để tính ngày đón. Vui lòng chọn giờ xuất phát.");
        }
        return input.pickupDate();
    }

    private static Instant requireReady(TripInput input) {
        if (input.readyAt() == null) {
            throw new DispatchRuleException("startAt", "Chưa biết lúc xe trục tới hub. Vui lòng chọn giờ xuất phát.");
        }
        return input.readyAt();
    }

    private static List<GeoPointDto> thin(List<GeoPointDto> shape) {
        if (shape.size() <= MAX_PATH_POINTS) {
            return shape;
        }
        List<GeoPointDto> result = new ArrayList<>();
        double step = (shape.size() - 1) / (double) (MAX_PATH_POINTS - 1);
        for (int i = 0; i < MAX_PATH_POINTS; i++) {
            result.add(shape.get((int) Math.round(i * step)));
        }
        return result;
    }
}
