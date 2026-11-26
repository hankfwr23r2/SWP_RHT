package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.enums.TransportMode;
import com.example.racehorse_transport.policy.DepartureDelayPolicy;

import com.example.racehorse_transport.entity.enums.TransportMode;
import com.example.racehorse_transport.dto.dispatch.TripGroupResponseDto;
import com.example.racehorse_transport.dto.dispatch.TripGroupResponseDto.GroupBookingDto;
import com.example.racehorse_transport.dto.dispatch.TripGroupResponseDto.GroupHorseDto;
import com.example.racehorse_transport.dto.dispatch.TripGroupResponseDto.SuggestedStopDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.policy.TripGroupingPolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
import com.example.racehorse_transport.policy.TripSchedulePolicy.StopTime;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Window;
import com.example.racehorse_transport.policy.TripGroupingPolicy.Candidate;
import com.example.racehorse_transport.policy.TripGroupingPolicy.Group;
import com.example.racehorse_transport.policy.TripGroupingPolicy.Point;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.DispatchBookingRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.TripGroupingService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.service.DeliveryRouteService;
import java.util.LinkedHashMap;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TripGroupingServiceImpl implements TripGroupingService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<HorseLegStatus> INACTIVE = Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED);
    // Gợi ý nhanh nên ước lượng: tốc độ trung bình so với tốc độ tối đa. Giờ chính thức tính theo đường thật ở TripScheduleService
    private static final double AVERAGE_SPEED_RATIO = 0.65;

    private final DispatchBookingRepository bookings;
    private final BookingAddressRepository addresses;
    private final DeliveryRouteService deliveryRoutes;
    private final BookingHorseRepository bookingHorses;
    private final HorseLegRepository legs;
    private final LocationRepository locations;
    private final VehicleRepository vehicles;
    private final SystemConfigService configs;
    private final com.example.racehorse_transport.service.TripScheduleService schedules;

    // Một đơn trong nhóm: địa chỉ cần ghé, các con chưa có chặng loại này, mốc giờ chuyến trục
    private record Entry(Booking booking, BookingAddress address, List<BookingHorse> horses, Instant departureAt, Instant arrivalAt) {}

    @Override
    @Transactional(readOnly = true)
    public List<TripGroupResponseDto> suggest(LocalDate date, Long hubId, VehicleTripType tripType) {
        if (tripType != VehicleTripType.PICKUP && tripType != VehicleTripType.DELIVERY) {
            throw new DispatchRuleException("tripType", "Chỉ gợi ý gom cho lượt đón hoặc lượt giao.");
        }
        Location hub = locations.findById(hubId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hub " + hubId));
        if (hub.getLocationType() != LocationType.HUB || hub.getLatitude() == null || hub.getLongitude() == null) {
            throw new DispatchRuleException("hubId", hub.getName() + " không phải hub có tọa độ.");
        }
        boolean pickup = tripType == VehicleTripType.PICKUP;
        List<Booking> found = pickup
                ? bookings.findForPickupGrouping(Set.of(BookingStatus.CONFIRMED), hubId, date)
                : bookings.findForDeliveryGrouping(Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION), hubId, date.minusDays(3), date).stream()
                        .filter(b -> LocalDate.ofInstant(arrivalOf(b), BUSINESS_ZONE).equals(date)).toList();

        Map<Long, Entry> entries = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        List<List<Entry>> multiPlace = new ArrayList<>();
        HorseLegType legType = pickup ? HorseLegType.PICKUP : HorseLegType.DELIVERY;
        for (Booking booking : found) {
            AddressRole role = pickup ? AddressRole.PICKUP : AddressRole.DELIVERY;
            BookingAddress address = addresses.findByBooking_Id(booking.getId()).stream().filter(a -> a.getAddressType() == role).findFirst().orElse(null);
            List<BookingHorse> active = bookingHorses.findForDetail(booking.getId()).stream()
                    .filter(h -> h.getStatus() == BookingHorseStatus.ACTIVE).toList();
            List<BookingHorse> horses = active.stream().filter(h -> {
                        Set<HorseLegType> planned = legs.findByBookingHorse_IdOrderBySequenceNumber(h.getId()).stream()
                                .filter(l -> !INACTIVE.contains(l.getStatus())).map(HorseLeg::getLegType).collect(Collectors.toSet());
                        // Chưa xếp chặng trục thì con ngựa chưa tới hub giao, chưa gom lượt giao được (quy tắc #7)
                        return !planned.contains(legType) && (pickup || planned.contains(HorseLegType.LINEHAUL));
                    })
                    .toList();
            if (address == null || horses.isEmpty()) continue;
            if (!pickup) {
                long destinationCount = active.stream().map(BookingHorse::getDeliveryAddress)
                        .filter(Objects::nonNull).map(BookingAddress::getId).distinct().count();
                if (destinationCount > 1 && horses.size() != active.size()) continue;
                BookingAddress defaultAddress = address;
                var byAddress = horses.stream().collect(Collectors.groupingBy(
                        horse -> horse.getDeliveryAddress() == null ? defaultAddress : horse.getDeliveryAddress(),
                        LinkedHashMap::new, Collectors.toList()));
                if (byAddress.size() > 1) {
                    multiPlace.add(byAddress.entrySet().stream().map(row -> new Entry(booking, row.getKey(), row.getValue(),
                            DepartureDelayPolicy.effectiveTime(booking.getDeparture()), arrivalOf(booking))).toList());
                    continue;
                }
                address = byAddress.keySet().iterator().next();
            }
            entries.put(booking.getId(), new Entry(booking, address, horses, DepartureDelayPolicy.effectiveTime(booking.getDeparture()), arrivalOf(booking)));
            candidates.add(new Candidate(booking.getId(), point(address.getLatitude().doubleValue(), address.getLongitude().doubleValue()),
                    horses.stream().mapToInt(h -> StallSeatPolicy.seats(h.getStallType().getCode())).sum(),
                    booking.getTransportMode() == TransportMode.PRIVATE));
        }
        if (candidates.isEmpty() && multiPlace.isEmpty()) return List.of();

        int capacity = vehicles.findAllByOrderByLicensePlateAsc().stream().filter(v -> v.getStatus() == VehicleStatus.ACTIVE)
                .mapToInt(v -> v.getStandardStallCount() * StallSeatPolicy.seats("STANDARD") + v.getWideStallCount() * StallSeatPolicy.seats("WIDE"))
                .max().orElse(0);
        double speed = configs.getInt("TRUCK_TOP_SPEED_KMH") * AVERAGE_SPEED_RATIO;
        Rules rules = new Rules(configs.getInt("MAX_CONTINUOUS_HOURS"), configs.getInt("MIN_REST_MINUTES"), configs.getInt("MAX_DRIVE_HOURS_PER_DAY"),
                configs.getInt("STOP_BASE_MINUTES"), configs.getInt("STOP_MINUTES_PER_HORSE"),
                configs.getInt("MAX_TRANSFER_HORSE_HOURS"), configs.getInt("MAX_DIRECT_HORSE_HOURS"),
                configs.getInt("HORSE_REST_INTERVAL_HOURS"));
        String side = pickup ? "PICKUP" : "DELIVERY";
        Window window = new Window(configs.getInt(side + "_WINDOW_START_HOUR"), configs.getInt(side + "_WINDOW_END_HOUR"));
        double maxHours = TripSchedulePolicy.rideLimitSeconds(false, rules) / 3600.0;
        Point hubPoint = point(hub.getLatitude().doubleValue(), hub.getLongitude().doubleValue());
        List<Group> groups = TripGroupingPolicy.group(hubPoint, candidates, capacity, maxHours, speed);
        List<TripGroupResponseDto> result = new ArrayList<>();
        for (Group group : groups) {
            result.add(describe(group, entries, hub, hubPoint, pickup, speed, date, rules, window, maxHours));
        }
        for (List<Entry> delivery : multiPlace) {
            try {
                result.add(describeDelivery(delivery, hub, rules, window));
            } catch (BookingRuleException ex) {
                // A newly unavailable route does not prevent suggestions for the other bookings.
            }
        }
        return result.stream().map(group -> withRestStops(group, tripType, rules)).toList();
    }

    private TripGroupResponseDto withRestStops(TripGroupResponseDto group, VehicleTripType tripType, Rules rules) {
        if (group.hours() < rules.horseRestIntervalHours()) return group;
        var request = new com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto(tripType, null,
                group.stops().stream().map(stop -> new com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto.PlaceDto(
                        stop.locationId(), stop.bookingAddressId())).toList(),
                group.bookings().stream().flatMap(booking -> booking.horses().stream()).map(GroupHorseDto::bookingHorseId).toList(), null);
        try {
            var plan = schedules.plan(request);
            return new TripGroupResponseDto(group.seats(), plan.km(), round(Duration.between(plan.startAt(), plan.endAt()).toSeconds() / 3600.0),
                    plan.blocked(), group.bookings(), plan.stops().stream().map(stop -> new SuggestedStopDto(
                            stop.locationId(), stop.bookingAddressId(), stop.stopType(), stop.arrivalAt(), stop.departureAt())).toList());
        } catch (DispatchRuleException | com.example.racehorse_transport.exception.RoutingUnavailableException exception) {
            return new TripGroupResponseDto(group.seats(), group.routeKm(), group.hours(), true, group.bookings(), group.stops());
        }
    }

    private TripGroupResponseDto describeDelivery(List<Entry> visits, Location hub, Rules rules, Window window) {
        int seats = visits.stream().flatMap(visit -> visit.horses().stream())
                .mapToInt(horse -> StallSeatPolicy.seats(horse.getStallType().getCode())).sum();
        var plan = deliveryRoutes.plan(hub.getId(), visits.stream()
                        .map(visit -> new GeoPointDto(
                                visit.address().getLatitude().doubleValue(), visit.address().getLongitude().doubleValue())).toList(),
                visits.stream().map(visit -> visit.horses().size()).toList(), seats);
        var ordered = plan.order().stream().map(index -> visits.get(index - 1)).toList();
        List<Integer> handled = new ArrayList<>(ordered.stream().map(visit -> visit.horses().size()).toList());
        int horseCount = handled.stream().mapToInt(Integer::intValue).sum();
        handled.addFirst(horseCount);
        handled.addLast(0);
        Instant ready = ordered.getFirst().arrivalAt().plus(TripSchedulePolicy.dwell(horseCount, rules));
        Instant start = TripSchedulePolicy.deliveryDeparture(ready, window, BUSINESS_ZONE, handled, plan.drives(), rules);
        List<StopTime> times = TripSchedulePolicy.schedule(start, handled, plan.drives(), rules);
        List<SuggestedStopDto> stops = new ArrayList<>();
        stops.add(new SuggestedStopDto(hub.getId(), null, TripStopType.HUB, times.getFirst().arrival(), times.getFirst().departure()));
        List<GroupBookingDto> rows = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Entry visit = ordered.get(i);
            StopTime time = times.get(i + 1);
            stops.add(new SuggestedStopDto(null, visit.address().getId(), TripStopType.DELIVERY, time.arrival(), time.departure()));
            rows.add(new GroupBookingDto(visit.booking().getId(), visit.booking().getCode(), visit.address().getId(),
                    visit.address().getAddressLine(), visit.horses().stream().map(horse -> new GroupHorseDto(
                            horse.getId(), horse.getHorse().getName(), StallSeatPolicy.seats(horse.getStallType().getCode()))).toList()));
        }
        stops.add(new SuggestedStopDto(hub.getId(), null, TripStopType.HUB, times.getLast().arrival(), times.getLast().departure()));
        return new TripGroupResponseDto(seats, round(plan.deliveryKm() + plan.returnKm()),
                round(Duration.between(start, times.getLast().arrival()).toSeconds() / 3600.0), false, rows, stops);
    }

    private TripGroupResponseDto describe(Group group, Map<Long, Entry> entries, Location hub, Point hubPoint, boolean pickup, double speed,
                                          LocalDate date, Rules rules, Window window, double maxHours) {
        // Lượt giao đi chiều ngược: hub rồi tới địa chỉ gần hub trước
        List<Candidate> order = pickup ? group.order() : group.order().reversed();
        List<Entry> visits = order.stream().map(c -> entries.get(c.bookingId())).toList();
        List<Point> points = new ArrayList<>();
        points.add(hubPoint);
        order.forEach(c -> points.add(c.point()));
        points.add(hubPoint);

        // Lượt đón: tới địa chỉ đầu lúc mở khung giờ của ngày khách chọn. Lượt giao: sau khi chuyến trục cuối của nhóm tới, trong khung giờ giao
        List<Long> drives = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            drives.add(Math.round(TripGroupingPolicy.km(points.get(i - 1), points.get(i)) / speed * 3600));
        }
        List<Integer> handled = new ArrayList<>(visits.stream().map(e -> e.horses().size()).toList());
        int all = handled.stream().mapToInt(Integer::intValue).sum();
        handled.addFirst(pickup ? 0 : all);
        handled.add(pickup ? all : 0);
        Instant start = pickup ? TripSchedulePolicy.pickupDeparture(date, window, BUSINESS_ZONE, drives, rules)
                : TripSchedulePolicy.deliveryDeparture(visits.stream().map(Entry::arrivalAt).max(Instant::compareTo).orElseThrow()
                        .plus(TripSchedulePolicy.dwell(all, rules)), window, BUSINESS_ZONE, handled, drives, rules);
        List<StopTime> times = TripSchedulePolicy.schedule(start, handled, drives, rules);
        List<SuggestedStopDto> stops = new ArrayList<>();
        int count = points.size();
        stops.add(new SuggestedStopDto(hub.getId(), null, TripStopType.HUB, times.getFirst().arrival(), times.getFirst().departure()));
        for (int i = 0; i < visits.size(); i++) {
            stops.add(new SuggestedStopDto(null, visits.get(i).address().getId(), pickup ? TripStopType.PICKUP : TripStopType.DELIVERY, times.get(i + 1).arrival(), times.get(i + 1).departure()));
        }
        stops.add(new SuggestedStopDto(hub.getId(), null, TripStopType.HUB, times.get(count - 1).arrival(), times.get(count - 1).departure()));

        List<GroupBookingDto> rows = visits.stream().map(e -> new GroupBookingDto(e.booking().getId(), e.booking().getCode(), e.address().getId(),
                e.address().getAddressLine(), e.horses().stream().map(h -> new GroupHorseDto(h.getId(), h.getHorse().getName(),
                        StallSeatPolicy.seats(h.getStallType().getCode()))).toList())).toList();
        int seats = order.stream().mapToInt(Candidate::seats).sum();
        return new TripGroupResponseDto(seats, round(group.routeKm()), round(group.hours()), group.hours() > maxHours, rows, stops);
    }

    // Giờ chuyến trục tới hub cuối: giờ chạy cộng thời gian dự kiến của tuyến
    private static Instant arrivalOf(Booking booking) {
        var departure = booking.getDeparture();
        return DepartureDelayPolicy.effectiveTime(departure).plus(Duration.ofMinutes(departure.getRoute().getEstimatedDurationMinutes()));
    }

    private static Point point(double lat, double lng) {
        return new Point(lat, lng);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
