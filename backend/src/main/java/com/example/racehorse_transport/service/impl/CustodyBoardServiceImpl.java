package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.tracking.CustodyRowResponseDto;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.policy.CareCheckPolicy;
import com.example.racehorse_transport.policy.TrackingPolicy;
import com.example.racehorse_transport.policy.TrackingPolicy.CustodyHolder;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.CustodyBoardService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustodyBoardServiceImpl implements CustodyBoardService {

    // Ngựa được nhận khi đơn còn CONFIRMED (chờ trả số dư) hoặc đang vận hành
    private static final Set<BookingStatus> HELD_STATUSES = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    private static final Set<CustodyHolder> HELD = Set.of(CustodyHolder.COMPANY, CustodyHolder.PARTY);

    private final BookingHorseRepository bookingHorses;
    private final HandoverHorseRepository scans;
    private final HorseLegRepository legs;
    private final HorseStayRepository stays;
    private final TripStopRepository stops;
    private final HorseHealthLogRepository healthLogs;
    private final SystemConfigService configs;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CustodyRowResponseDto> board() {
        List<BookingHorse> lines = bookingHorses.findByStatusAndBooking_StatusIn(BookingHorseStatus.ACTIVE, HELD_STATUSES);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<Long> ids = lines.stream().map(BookingHorse::getId).toList();
        Map<Long, List<HandoverHorse>> scansByHorse = scans.findByBookingHorseIds(ids).stream()
                .collect(Collectors.groupingBy(row -> row.getHorseLeg().getBookingHorse().getId()));
        Map<Long, List<HorseLeg>> legsByHorse = legs.findByBookingHorse_IdInOrderByBookingHorse_IdAscSequenceNumberAsc(ids).stream()
                .collect(Collectors.groupingBy(leg -> leg.getBookingHorse().getId()));
        Map<Long, List<HorseStay>> staysByHorse = stays.findByBookingHorse_IdInOrderById(ids).stream()
                .collect(Collectors.groupingBy(stay -> stay.getBookingHorse().getId()));
        int maxHours = configs.getInt("MAX_CONTINUOUS_HOURS");
        Instant now = clock.instant();
        return lines.stream()
                .map(line -> row(line, scansByHorse.getOrDefault(line.getId(), List.of()), legsByHorse.getOrDefault(line.getId(), List.of()),
                        staysByHorse.getOrDefault(line.getId(), List.of()), maxHours, now))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((CustodyRowResponseDto row) -> !row.careOverdue())
                        .thenComparing(CustodyRowResponseDto::nextCheckAt).thenComparing(CustodyRowResponseDto::bookingHorseId))
                .toList();
    }

    private CustodyRowResponseDto row(BookingHorse line, List<HandoverHorse> horseScans, List<HorseLeg> horseLegs, List<HorseStay> horseStays,
                                      int maxHours, Instant now) {
        Handover last = horseScans.stream()
                .filter(scan -> scan.getHandover().getStatus() == HandoverStatus.DONE && Boolean.TRUE.equals(scan.getMicrochipMatched()))
                .map(HandoverHorse::getHandover)
                .max(Comparator.comparing(Handover::getHandedAt).thenComparing(Handover::getId)).orElse(null);
        if (last == null) {
            return null;
        }
        CustodyHolder holder = TrackingPolicy.holderAfter(last.getHandoverType(), last.getToParty() != null);
        if (!HELD.contains(holder)) {
            return null;
        }
        HorseStay stay = horseStays.stream().filter(s -> s.getActualStartAt() != null && s.getActualEndAt() == null).findFirst().orElse(null);
        HorseLeg moving = stay != null ? null : horseLegs.stream().filter(leg -> leg.getStatus() == HorseLegStatus.IN_PROGRESS).findFirst().orElse(null);
        HorseHealthLog log = healthLogs.findFirstByBookingHorse_IdOrderByRecordedAtDesc(line.getId()).orElse(null);
        Instant lastCheck = log == null ? null : log.getRecordedAt();
        Instant next = CareCheckPolicy.nextCheckAt(last.getHandedAt(), lastCheck, stay == null, maxHours);
        TripStop stop = moving == null || moving.getVehicleTrip() == null ? null : lastReached(moving.getVehicleTrip().getId());
        return new CustodyRowResponseDto(line.getId(), line.getHorse().getName(), line.getBooking().getId(), line.getBooking().getCode(),
                holder, holder == CustodyHolder.PARTY ? last.getToParty().getDisplayName() : last.getHandledBy().getFullName(), last.getHandedAt(),
                stay == null ? null : stay.getStayType(), moving == null ? null : moving.getLegType(),
                moving == null || moving.getVehicleTrip() == null ? null : moving.getVehicleTrip().getId(),
                moving == null || moving.getVehicleTrip() == null ? null : moving.getVehicleTrip().getCode(),
                stay != null ? stay.getLocation().getName() : stop != null ? nameOf(stop.getLocation(), stop.getBookingAddress()) : null,
                stay != null ? point(stay.getLocation(), null) : stop != null ? point(stop.getLocation(), stop.getBookingAddress()) : null,
                lastCheck, log == null ? null : log.getHealthStatus(), next, holder == CustodyHolder.COMPANY && CareCheckPolicy.overdue(next, now));
    }

    // Điểm dừng gần nhất xe đã tới
    private TripStop lastReached(Long tripId) {
        return stops.findByVehicleTrip_IdOrderBySequenceNumber(tripId).stream().filter(stop -> stop.getActualArrivalAt() != null)
                .reduce((first, second) -> second).orElse(null);
    }

    private static String nameOf(Location location, BookingAddress address) {
        return location != null ? location.getName() : address.getAddressLine();
    }

    private static GeoPointDto point(Location location, BookingAddress address) {
        BigDecimal latitude = location != null ? location.getLatitude() : address.getLatitude();
        BigDecimal longitude = location != null ? location.getLongitude() : address.getLongitude();
        return latitude == null || longitude == null ? null : new GeoPointDto(latitude.doubleValue(), longitude.doubleValue());
    }
}
