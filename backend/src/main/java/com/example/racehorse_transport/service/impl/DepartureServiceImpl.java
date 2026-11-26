package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.enums.DepartureAvailability;
import com.example.racehorse_transport.dto.departure.DepartureDetailResponseDto;
import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;
import com.example.racehorse_transport.service.DepartureFleetService;
import com.example.racehorse_transport.policy.DepartureFleetPolicy;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.dto.departure.DepartureCreateRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureMergeRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.DepartureSchedule;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.ScheduleStatus;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingStatusPolicy;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.DepartureCapacityPolicy;
import com.example.racehorse_transport.policy.DepartureDecisionPolicy;
import com.example.racehorse_transport.policy.DepartureGenerationPolicy;
import com.example.racehorse_transport.policy.DepartureMergePolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.DepartureScheduleRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartureServiceImpl implements DepartureService {

    // Giờ chạy của lịch tính theo giờ Việt Nam
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final DepartureRepository departures;
    private final DepartureScheduleRepository schedules;
    private final RouteRepository routes;
    private final BookingRepository bookings;
    private final AppUserRepository users;
    private final BookingStatusService bookingStatus;
    private final SystemConfigService config;
    private final Clock clock;
    private final VehicleTripRepository vehicleTrips;
    private final DepartureFleetService fleet;

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Departure lockForLinehaulPlanning(Long departureId) {
        return departures.findForUpdate(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + departureId));
    }

    @Override
    @Transactional
    public void linehaulDeparted(Long departureId) {
        Departure departure = departures.findForUpdate(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + departureId));
        departure.setStatus(DepartureCapacityPolicy.afterLinehaulDeparture(departure.getStatus()));
    }

    @Override
    @Transactional
    public void linehaulCompleted(Long departureId) {
        Departure departure = departures.findForUpdate(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + departureId));
        boolean unfinished = vehicleTrips.existsByDeparture_IdAndTripTypeAndStatusNotIn(departureId,
                VehicleTripType.LINEHAUL, Set.of(VehicleTripStatus.COMPLETED, VehicleTripStatus.CANCELLED));
        departure.setStatus(DepartureCapacityPolicy.afterLinehaulCompletion(departure.getStatus(), unfinished));
    }

    @Override
    @Transactional
    public void reserve(Long departureId, int stalls) {
        Departure departure = departures.findForUpdate(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + departureId));
        int shortage = stalls - DepartureCapacityPolicy.available(departure.getStallCapacity(), departure.getBookedStallCount());
        var selected = shortage > 0 && departure.getStatus() == DepartureStatus.OPEN
                && clock.instant().isBefore(departure.getBookingCutoffAt())
                ? DepartureFleetPolicy.selectSpareVehicles(shortage, fleet.freeStock(departure, true))
                : Map.<DepartureFleetPolicy.Stock, Integer>of();
        int capacity = departure.getStallCapacity();
        for (var entry : selected.entrySet()) {
            capacity = Math.addExact(capacity, DepartureFleetPolicy.capacity(entry.getValue(), entry.getKey().stalls()));
        }
        DepartureCapacityPolicy.reasonNotReservable(departure.getStatus(), departure.getBookingCutoffAt(), clock.instant(),
                        capacity, departure.getBookedStallCount(), stalls)
                .ifPresent(reason -> {
                    throw new IllegalStateException(reason);
                });
        if (!selected.isEmpty()) {
            selected.forEach((stock, count) -> fleet.attach(departure, fleet.vehicleClass(stock.vehicleClassId()),
                    count, DepartureAllotmentType.SPARE, null));
            departure.setStallCapacity(capacity);
            fleet.notifyReinforcement(departure, selected.values().stream().mapToInt(Integer::intValue).sum());
        }
        departure.setBookedStallCount(departure.getBookedStallCount() + stalls);
    }

    @Override
    @Transactional
    public void release(Long departureId, int stalls) {
        Departure departure = departures.findForUpdate(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + departureId));
        departure.setBookedStallCount(DepartureCapacityPolicy.bookedAfterRelease(departure.getBookedStallCount(), stalls));
    }

    @Override
    @Transactional
    public int generateFromSchedules() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
        int weeks = config.getInt("DEPARTURE_GENERATION_WEEKS");
        Map<Long, Set<Short>> weekdaysBySchedule = schedules.findScheduleDays().stream()
                .collect(Collectors.groupingBy(day -> day.getId().getDepartureScheduleId(),
                        Collectors.mapping(day -> day.getId().getDayOfWeek(), Collectors.toSet())));

        int created = 0;
        for (DepartureSchedule schedule : schedules.findRunnable(ScheduleStatus.ACTIVE, ActiveStatus.ACTIVE)) {
            List<LocalDate> dates = DepartureGenerationPolicy.datesToGenerate(today, weeks, schedule.getValidFrom(),
                    schedule.getValidTo(), weekdaysBySchedule.getOrDefault(schedule.getId(), Set.of()));
            for (LocalDate date : dates) {
                Instant planned = DepartureGenerationPolicy.plannedDepartureAt(date, schedule.getDepartureTime(), BUSINESS_ZONE);
                created += departures.insertIfAbsent(schedule.getId(), schedule.getRoute().getId(), date, planned,
                        DepartureGenerationPolicy.bookingCutoffAt(planned, schedule.getBookingCutoffHours()),
                        DepartureFleetPolicy.capacity(schedule.getBaseVehicleCount(), schedule.getBaseVehicleClass().getMaxStallCount()),
                        schedule.getBaseVehicleClass().getId(), schedule.getBaseVehicleCount(), clock.instant());
            }
        }
        if (created > 0) {
            log.info("Đã sinh {} chuyến mới từ lịch chạy", created);
        }
        return created;
    }

    @Override
    @Transactional
    public int closePastCutoff() {
        // Cùng điều kiện với DepartureCapacityPolicy.shouldClose, chạy một lệnh cho mọi chuyến
        return departures.changeStatusWhenCutoffPassed(DepartureStatus.OPEN, DepartureStatus.CLOSED, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartureResponseDto> departures(Long routeId, DepartureStatus status, LocalDate fromDate, LocalDate toDate) {
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        return departures.search(routeId, status, fromDate, toDate).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public DepartureResponseDto createAdHoc(DepartureCreateRequestDto request, String username) {
        Route route = routes.findWithEndpoints(request.getRouteId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tuyến " + request.getRouteId()));
        if (route.getStatus() != ActiveStatus.ACTIVE) {
            throw new IllegalArgumentException("Tuyến " + route.getCode() + " đang ngừng chạy.");
        }
        Instant planned = DepartureGenerationPolicy.plannedDepartureAt(request.getDepartureDate(), request.getDepartureTime(), BUSINESS_ZONE);
        if (!planned.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Giờ xuất phát phải ở tương lai.");
        }
        Departure departure = request.toEntity(route, planned,
                DepartureGenerationPolicy.bookingCutoffAt(planned, request.getBookingCutoffHours()));
        departure.setBookedStallCount(0);
        departure.setStatus(DepartureStatus.OPEN);
        AppUser actor = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản " + username));
        var vehicleClass = fleet.vehicleClass(request.getVehicleClassId());
        departure.setStallCapacity(DepartureFleetPolicy.capacity(request.getVehicleCount(), vehicleClass.getMaxStallCount()));
        Departure saved = departures.save(departure);
        fleet.attach(saved, vehicleClass, request.getVehicleCount(), DepartureAllotmentType.BASE, actor);
        return toDto(saved);
    }

    @Override
    @Transactional
    public DepartureResponseDto cancel(Long departureId, String reason, String actorUsername) {
        return cancelForUser(departureId, reason, actorUsername, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartureResponseDto> mergeTargets(Long departureId) {
        Departure source = departures.findWithRoute(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + departureId));
        if (!DepartureMergePolicy.canMergeSource(source)) {
            return List.of();
        }
        LocalDate holdThrough = priceHoldThrough(source);
        Instant now = clock.instant();
        return departures.search(source.getRoute().getId(), null, source.getDepartureDate(), holdThrough).stream()
                .filter(target -> DepartureMergePolicy.reasonNotMergeable(source, target, holdThrough, now, fleet.sellableCapacity(target)).isEmpty())
                .map(this::toDto).toList();
    }

    @Override
    @Transactional
    public DepartureResponseDto merge(Long departureId, DepartureMergeRequestDto request, String actorUsername) {
        return cancelForUser(departureId, request.reason(), actorUsername, request);
    }

    private DepartureResponseDto cancelForUser(Long departureId, String reason, String actorUsername, DepartureMergeRequestDto merge) {
        AppUser actor = users.findByUsername(actorUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản " + actorUsername));
        cancelDeparture(departureId, reason, actor.getId(), merge);
        return toDto(departures.findWithRoute(departureId).orElseThrow());
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void cancelForDelay(Long departureId, String reason) {
        cancelDeparture(departureId, reason, null, null);
    }

    private void cancelDeparture(Long departureId, String reason, Long actorId, DepartureMergeRequestDto merge) {
        // Khóa đơn trước, chuyến sau. Trong mỗi nhóm khóa theo id tăng dần như luồng đổi chuyến.
        Set<Long> locked = new HashSet<>(bookings.findIdsByDeparture(departureId));
        locked.stream().sorted().forEach(bookings::findForUpdate);
        Long targetId = merge == null ? null : merge.targetDepartureId();
        Departure target = targetId != null && targetId < departureId ? lockDeparture(targetId) : null;
        Departure departure = lockDeparture(departureId);
        if (!DepartureCapacityPolicy.canCancel(departure.getStatus())) {
            throw new IllegalStateException("Chuyến đã chạy hoặc đã hủy, không hủy được.");
        }
        List<Booking> onDeparture = bookings.findByDeparture_Id(departureId);
        if (onDeparture.stream().anyMatch(booking -> !locked.contains(booking.getId()))) {
            throw new IllegalStateException("Chuyến vừa có thêm đơn. Tải lại rồi hủy lại.");
        }
        onDeparture.stream().filter(booking -> DepartureDecisionPolicy.blocksCancellation(booking.getStatus())
                        || bookings.hasReceivedHorses(booking.getId())).findFirst()
                .ifPresent(booking -> {
                    throw new IllegalStateException("Đơn " + booking.getCode() + " đã nhận ngựa nên không hủy chuyến. Xử lý bằng sự cố.");
                });
        String bookingReason = "Chuyến ngày " + departure.getDepartureDate() + " bị hủy: " + reason.trim();
        if (merge != null) {
            if (target == null) {
                target = departureId.equals(targetId) ? departure : lockDeparture(targetId);
            }
            DepartureMergePolicy.reasonNotMergeable(departure, target, priceHoldThrough(departure), clock.instant(), fleet.sellableCapacity(target))
                    .ifPresent(problem -> { throw BookingRuleException.conflict(problem); });
            merge.applyTo(departure, target);
            bookingReason = "Dồn sang chuyến ngày " + target.getDepartureDate() + ": " + reason.trim();
        }
        departure.setStatus(DepartureStatus.CANCELLED);

        for (Booking booking : onDeparture) {
            if (BookingStatusPolicy.canTransition(booking.getStatus(), BookingStatus.SCHEDULE_CHANGE_REQUIRED)) {
                bookingStatus.requireScheduleChange(booking.getId(), departure, ScheduleChangeCause.COMPANY, bookingReason, actorId);
            } else {
                bookingStatus.clearPlannedTransport(booking.getId());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long sellableCapacity(Departure departure) {
        return fleet.sellableCapacity(departure);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartureAvailability availability(Departure departure, long spareCapacity) {
        return fleet.availability(departure, spareCapacity);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartureDetailResponseDto detail(Long departureId) {
        var departure = departures.findWithRoute(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + departureId));
        return new DepartureDetailResponseDto(toDto(departure), fleet.vehicles(departureId));
    }

    @Override
    @Transactional
    public DepartureDetailResponseDto removeVehicle(Long departureId, Long allotmentId, String reason, String username) {
        var actor = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản " + username));
        Departure departure = lockDeparture(departureId);
        var allotments = fleet.allotments(departureId);
        var removed = allotments.stream().filter(allotment -> allotment.getId().equals(allotmentId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy xe đã gắn " + allotmentId));
        int removedCapacity = DepartureFleetPolicy.capacity(removed.getVehicleCount(), removed.getVehicleClass().getMaxStallCount());
        departure.setStallCapacity(DepartureFleetPolicy.capacityAfterRemoval(departure.getStatus(), allotments.size(),
                departure.getStallCapacity(), removedCapacity, departure.getBookedStallCount()));
        fleet.remove(removed, actor.getId(), reason);
        return new DepartureDetailResponseDto(toDto(departure), fleet.vehicles(departureId));
    }

    private Departure lockDeparture(Long id) {
        return departures.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + id));
    }

    private LocalDate priceHoldThrough(Departure source) {
        return BookingSubmissionPolicy.priceHoldThrough(source.getDepartureDate(), config.getInt("PRICE_HOLD_DAYS"));
    }

    private DepartureResponseDto toDto(Departure departure) {
        return DepartureResponseDto.from(departure,
                DepartureCapacityPolicy.available(departure.getStallCapacity(), departure.getBookedStallCount()));
    }
}
