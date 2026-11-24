package com.example.racehorse_transport.service.impl;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripGroupingServiceImpl implements TripGroupingService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<HorseLegStatus> INACTIVE = Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED);
    // Gợi ý nhanh nên ước lượng: tốc độ trung bình so với tốc độ tối đa. Giờ chính thức tính theo đường thật ở TripScheduleService
    private static final double AVERAGE_SPEED_RATIO = 0.65;

    private final DispatchBookingRepository bookings;
    private final BookingAddressRepository addresses;
    private final BookingHorseRepository bookingHorses;
    private final HorseLegRepository legs;
    private final LocationRepository locations;
    private final VehicleRepository vehicles;
    private final SystemConfigService configs;

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
        HorseLegType legType = pickup ? HorseLegType.PICKUP : HorseLegType.DELIVERY;
        for (Booking booking : found) {
            AddressRole role = pickup ? AddressRole.PICKUP : AddressRole.DELIVERY;
            BookingAddress address = addresses.findByBooking_Id(booking.getId()).stream().filter(a -> a.getAddressType() == role).findFirst().orElse(null);
            List<BookingHorse> horses = bookingHorses.findForDetail(booking.getId()).stream()
                    .filter(h -> h.getStatus() == BookingHorseStatus.ACTIVE)
                    .filter(h -> legs.findByBookingHorse_IdOrderBySequenceNumber(h.getId()).stream()
                            .noneMatch(l -> l.getLegType() == legType && !INACTIVE.contains(l.getStatus())))
                    .toList();
            if (address == null || horses.isEmpty()) continue;
            entries.put(booking.getId(), new Entry(booking, address, horses, booking.getDeparture().getPlannedDepartureAt(), arrivalOf(booking)));
            candidates.add(new Candidate(booking.getId(), point(address.getLatitude().doubleValue(), address.getLongitude().doubleValue()),
                    horses.stream().mapToInt(h -> StallSeatPolicy.seats(h.getStallType().getCode())).sum()));
        }
        if (candidates.isEmpty()) return List.of();

        int capacity = vehicles.findAllByOrderByLicensePlateAsc().stream().filter(v -> v.getStatus() == VehicleStatus.ACTIVE)
                .mapToInt(v -> v.getStandardStallCount() * StallSeatPolicy.seats("STANDARD") + v.getWideStallCount() * StallSeatPolicy.seats("WIDE"))
                .max().orElse(0);
        double speed = configs.getInt("TRUCK_TOP_SPEED_KMH") * AVERAGE_SPEED_RATIO;
        Rules rules = new Rules(configs.getInt("MAX_CONTINUOUS_HOURS"), configs.getInt("MIN_REST_MINUTES"), configs.getInt("MAX_DRIVE_HOURS_PER_DAY"),
                configs.getInt("STOP_BASE_MINUTES"), configs.getInt("STOP_MINUTES_PER_HORSE"));
        String side = pickup ? "PICKUP" : "DELIVERY";
        Window window = new Window(configs.getInt(side + "_WINDOW_START_HOUR"), configs.getInt(side + "_WINDOW_END_HOUR"));
        double maxHours = TripSchedulePolicy.rideLimitSeconds(false, rules) / 3600.0;
        Point hubPoint = point(hub.getLatitude().doubleValue(), hub.getLongitude().doubleValue());
        List<Group> groups = TripGroupingPolicy.group(hubPoint, candidates, capacity, maxHours, speed);
        return groups.stream().map(group -> describe(group, entries, hub, hubPoint, pickup, speed, date, rules, window, maxHours)).toList();
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
        return departure.getPlannedDepartureAt().plus(Duration.ofMinutes(departure.getRoute().getEstimatedDurationMinutes()));
    }

    private static Point point(double lat, double lng) {
        return new Point(lat, lng);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
