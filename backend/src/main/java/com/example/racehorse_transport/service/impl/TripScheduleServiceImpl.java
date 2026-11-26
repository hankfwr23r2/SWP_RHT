package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto.PlannedStopDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto.Warning;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy.StopTime;
import com.example.racehorse_transport.repository.RouteStopRepository;
import com.example.racehorse_transport.service.PricingRouteService;
import com.example.racehorse_transport.service.TripScheduleDataService;
import com.example.racehorse_transport.service.TripScheduleDataService.PlaceInput;
import com.example.racehorse_transport.service.TripScheduleDataService.HorseInput;
import com.example.racehorse_transport.service.TripScheduleDataService.TripInput;
import com.example.racehorse_transport.service.TripScheduleService;
import com.example.racehorse_transport.service.VehicleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    private final RouteStopRepository routeStops;
    private final VehicleService vehicles;
    private final Clock clock;

    @Override
    public PlannedTripResponseDto plan(PlannedTripRequestDto request) {
        return configuredTransferPlan(request).orElseGet(() -> compute(request, data.load(request)).plan());
    }

    @Override
    public VehicleTripResponseDto create(PlannedTripRequestDto request, String username) {
        if (request.vehicleId() == null) {
            throw new DispatchRuleException("vehicleId", "Vui lòng chọn xe.");
        }
        var configured = configuredTransferPlan(request);
        if (configured.isPresent()) {
            return data.save(request, List.of(), configured.get(), username);
        }
        TripInput input = data.load(request);
        var calculated = compute(request, input);
        PlannedTripResponseDto plan = calculated.plan();
        if (plan.blocked()) {
            throw new DispatchRuleException("places", message(plan.warnings(), input), true);
        }
        return data.save(request, calculated.input().horses(), plan, username);
    }

    private Optional<PlannedTripResponseDto> configuredTransferPlan(PlannedTripRequestDto request) {
        // Lượt về rỗng theo tuyến cấu hình; lượt chuyển có ngựa tính theo bản đồ như cũ
        if (request.tripType() != VehicleTripType.TRANSFER || !request.bookingHorseIds().isEmpty()) {
            return Optional.empty();
        }
        var places = request.places();
        if (places.size() != 2 || places.stream().anyMatch(place -> place.locationId() == null || place.bookingAddressId() != null)) {
            return Optional.empty();
        }
        Long originId = places.getFirst().locationId();
        Long destinationId = places.getLast().locationId();
        List<RouteStop> route = routeStops.findBetween(originId, destinationId, ActiveStatus.ACTIVE);
        if (route.size() < 2 || !originId.equals(route.getFirst().getLocation().getId())
                || !destinationId.equals(route.getLast().getLocation().getId())) {
            return Optional.empty();
        }
        Instant start = transferStart(request);
        Instant previousDeparture = start;
        long totalDriveMinutes = 0;
        List<PlannedStopDto> stops = new ArrayList<>();
        for (RouteStop routeStop : route) {
            var location = routeStop.getLocation();
            Interval time = DispatchPolicy.routeStopWindow(start, routeStop.getArrivalOffsetMinutes(), routeStop.getDwellMinutes());
            long driveMinutes = Duration.between(previousDeparture, time.start()).toMinutes();
            GeoPointDto point = location.getLatitude() == null || location.getLongitude() == null ? null
                    : new GeoPointDto(location.getLatitude().doubleValue(), location.getLongitude().doubleValue());
            stops.add(new PlannedStopDto(location.getId(), null, TripStopType.valueOf(routeStop.getStopType().name()),
                    location.getName(), null, point, 0, time.start(), time.end(), driveMinutes, 0, 0, List.of()));
            totalDriveMinutes += driveMinutes;
            previousDeparture = time.end();
        }
        return Optional.of(new PlannedTripResponseDto(start, previousDeparture, totalDriveMinutes,
                route.getFirst().getRoute().getDistanceKm().doubleValue(), false, List.of(), stops));
    }

    private Instant transferStart(PlannedTripRequestDto request) {
        if (request.startAt() != null) {
            return request.startAt();
        }
        Instant freeFrom = request.vehicleId() == null ? null : vehicles.vehicleForDispatch(request.vehicleId()).freeFrom();
        return freeFrom == null ? clock.instant() : notPast(freeFrom);
    }

    private record Calculation(TripInput input, PlannedTripResponseDto plan) {}

    private record RestCandidate(PlaceInput place, List<RoadRouteDto.Leg> legs) {
        double distance() {
            return legs.stream().mapToDouble(RoadRouteDto.Leg::km).sum();
        }
    }

    private record RestNeed(HorseInput horse, TripSchedulePolicy.RestGap gap) {}

    private Calculation compute(PlannedTripRequestDto request, TripInput input) {
        boolean direct = request.tripType() == VehicleTripType.DIRECT;
        Instant start = request.tripType() == VehicleTripType.TRANSFER ? transferStart(request) : request.startAt();
        Instant around = start != null ? start
                : input.readyAt() != null ? notPast(input.readyAt())
                : input.pickupDate() != null ? input.pickupDate().atTime(input.pickupWindow().startHour(), 0).atZone(BUSINESS_ZONE).toInstant()
                : clock.instant();
        LocalDateTime roadTime = LocalDateTime.ofInstant(around, BUSINESS_ZONE);
        List<RoadRouteDto.Leg> legs = route(input.places(), input, roadTime);
        if (request.tripType() == VehicleTripType.PICKUP || request.tripType() == VehicleTripType.DELIVERY || direct) {
            RestNeed need = restNeed(input, legs, around);
            if (need != null) {
                var stations = data.activeRestStops();
                do {
                    int at = need.gap().toStop();
                    var selected = chooseRest(input, legs, roadTime, stations, need);
                    input = input.insertRest(at, selected.place());
                    var revised = new ArrayList<>(legs);
                    revised.remove(at - 1);
                    revised.addAll(at - 1, selected.legs());
                    legs = revised;
                    need = restNeed(input, legs, around);
                } while (need != null);
            }
        }
        List<Long> drives = legs.stream().map(RoadRouteDto.Leg::driveSeconds).toList();
        List<Integer> horses = input.horsesAtStops();

        if (start == null) {
            start = switch (request.tripType()) {
                case PICKUP, DIRECT -> TripSchedulePolicy.pickupDeparture(requirePickupDate(input), input.pickupWindow(), BUSINESS_ZONE, drives, input.rules());
                case DELIVERY -> TripSchedulePolicy.deliveryDeparture(
                        notPast(requireReady(input)).plus(TripSchedulePolicy.dwell(horses.getFirst(), input.rules())),
                        input.deliveryWindow(), BUSINESS_ZONE, horses, drives, restStops(input), input.rules());
                default -> throw new DispatchRuleException("startAt", "Vui lòng chọn giờ xuất phát.");
            };
        }
        List<StopTime> times = TripSchedulePolicy.schedule(start, horses, drives, restStops(input), input.rules());
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
        return new Calculation(input, new PlannedTripResponseDto(times.getFirst().arrival(), times.getLast().departure(),
                Math.round(drives.stream().mapToLong(Long::longValue).sum() / 60.0),
                Math.round(legs.stream().mapToDouble(RoadRouteDto.Leg::km).sum() * 10) / 10.0,
                warnings.stream().anyMatch(Warning::blocking), List.copyOf(warnings), stops));
    }

    private List<RoadRouteDto.Leg> route(List<PlaceInput> places, TripInput input, LocalDateTime around) {
        return roads.legs(places.stream().map(PlaceInput::point).toList(), around, input.weightTons(), input.topSpeedKmh());
    }

    private static Set<Integer> restStops(TripInput input) {
        return IntStream.range(0, input.places().size()).filter(index -> (input.places().get(index).stopType() == TripStopType.REST || input.places().get(index).stopType() == TripStopType.VITAL_CHECK))
                .boxed().collect(Collectors.toSet());
    }

    private static RestNeed restNeed(TripInput input, List<RoadRouteDto.Leg> legs, Instant around) {
        if (input.rules().horseRestIntervalHours() <= 0) return null;
        var stops = restStops(input);
        var times = TripSchedulePolicy.schedule(around, input.horsesAtStops(),
                legs.stream().map(RoadRouteDto.Leg::driveSeconds).toList(), stops, input.rules());
        return input.horses().stream().map(horse -> new RestNeed(horse,
                        TripSchedulePolicy.firstRestGap(horse.board(), horse.alight(), times, stops, input.rules())))
                .filter(need -> need.gap() != null)
                .min(Comparator.comparingInt((RestNeed need) -> need.gap().toStop())
                        .thenComparingLong(need -> need.gap().secondsAvailable())).orElse(null);
    }

    private RestCandidate chooseRest(TripInput input, List<RoadRouteDto.Leg> legs, LocalDateTime roadTime,
                                     List<PlaceInput> stations, RestNeed need) {
        var from = input.places().get(need.gap().fromStop());
        var to = input.places().get(need.gap().toStop());
        var used = input.places().stream().map(PlaceInput::locationId).collect(Collectors.toSet());
        long original = legs.get(need.gap().fromStop()).driveSeconds();
        return stations.stream().filter(station -> !used.contains(station.locationId()))
                .map(station -> new RestCandidate(station, route(List.of(from, station, to), input, roadTime)))
                .filter(candidate -> candidate.legs().getFirst().driveSeconds() <= need.gap().secondsAvailable()
                        && candidate.legs().getLast().driveSeconds() < original)
                .min(Comparator.comparingDouble(RestCandidate::distance))
                .orElseThrow(() -> new DispatchRuleException("places", "Không có trạm nghỉ phù hợp cho "
                        + need.horse().horseName() + " trên đoạn " + from.name() + " → " + to.name()
                        + " vượt " + input.rules().horseRestIntervalHours() + " giờ liên tục.", true));
    }

    private static Set<Warning> warnings(VehicleTripType type, TripInput input, List<Long> drives, List<StopTime> times, boolean direct) {
        Set<Warning> result = EnumSet.noneOf(Warning.class);
        long limit = TripSchedulePolicy.rideLimitSeconds(direct, input.rules());
        if (input.horses().stream().anyMatch(h ->
                Duration.between(times.get(h.board()).departure(), times.get(h.alight()).arrival()).getSeconds() > limit)) {
            result.add(Warning.RIDE_TOO_LONG);
        }
        if (type == VehicleTripType.PICKUP && input.hubDeadline() != null && times.getLast().arrival().isAfter(input.hubDeadline())) {
            result.add(Warning.LATE_FOR_HUB);
        }
        long dayLimit = input.rules().maxDriveHoursPerDay() * 3600L;
        List<Interval> intervals = times.stream().map(t -> new Interval(t.arrival(), t.departure())).toList();
        if ((input.multiPlaceDelivery() && drives.stream().mapToLong(Long::longValue).sum() > dayLimit)
                || TripSchedulePolicy.drivingByDay(List.of(intervals), BUSINESS_ZONE, input.rules()).values().stream().anyMatch(s -> s > dayLimit)) {
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

    // Giao lại: ngựa đã ở hub từ trước, giờ gợi ý không được ở quá khứ
    private Instant notPast(Instant at) {
        Instant now = clock.instant();
        return at.isBefore(now) ? now : at;
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
