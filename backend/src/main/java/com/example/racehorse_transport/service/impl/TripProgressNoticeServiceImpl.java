package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.policy.CustomerNoticePolicy;
import com.example.racehorse_transport.policy.DeliveryEtaPolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.service.CoordinatorRecipientService;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.TripProgressNoticeService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class TripProgressNoticeServiceImpl implements TripProgressNoticeService {
    private static final Set<VehicleTripType> SUPPORTED = Set.of(VehicleTripType.DELIVERY, VehicleTripType.DIRECT);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter PLANNED_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZONE);
    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final HorseLegRepository legs;
    private final CoordinatorRecipientService coordinatorRecipients;
    private final NotificationService notifications;
    private final SystemConfigService configs;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void planChanged(VehicleTrip trip, List<CrewAssignment> crew, List<HorseLeg> tripLegs) {
        String summary = "Lượt xe " + trip.getCode() + " dùng xe " + trip.getVehicle().getLicensePlate() + ".";
        String start = " Dự kiến bắt đầu lúc " + PLANNED_TIME.format(trip.getPlannedStartAt()) + ".";
        var crewIds = crew.stream().map(member -> member.getStaff().getUserId()).distinct().toList();
        if (!crewIds.isEmpty()) {
            notifications.notify(crewIds, null, "Kế hoạch lượt xe đã thay đổi",
                    summary + start + " Mở Lượt xe của tôi để xem giờ từng điểm.", "/my-trips");
        }
        var byBooking = tripLegs.stream().collect(Collectors.groupingBy(leg -> leg.getBookingHorse().getBooking().getId()));
        byBooking.forEach((bookingId, own) -> {
            var booking = own.getFirst().getBookingHorse().getBooking();
            var windows = own.stream().map(CustomerNoticePolicy::expectedTimes).filter(times -> !times.isEmpty())
                    .map(CustomerNoticePolicy::describeTimes).distinct().toList();
            String details = windows.isEmpty() ? start : " Xe dự kiến " + String.join(". Xe dự kiến ", windows) + ".";
            notifications.notify(List.of(booking.getCustomer().getUserId()), bookingId, "Kế hoạch vận chuyển đã thay đổi",
                    "Đơn " + booking.getCode() + ". " + summary + details, "/orders/" + bookingId);
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyLinehaulDeparted(VehicleTrip trip) {
        var oneLegPerBooking = new LinkedHashMap<Long, HorseLeg>();
        for (HorseLeg leg : legs.findActiveOnTrip(trip.getId(),
                Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED, HorseLegStatus.COMPLETED))) {
            oneLegPerBooking.putIfAbsent(leg.getBookingHorse().getBooking().getId(), leg);
        }
        oneLegPerBooking.forEach((bookingId, leg) -> {
            var booking = leg.getBookingHorse().getBooking();
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId,
                    "Xe trục đã xuất phát", "Xe trục của đơn " + booking.getCode() + " đã xuất phát.",
                    "/orders/" + bookingId);
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyEarlyStart(VehicleTrip trip, String reason) {
        var recipients = coordinatorRecipients.forTrip(trip);
        if (!recipients.isEmpty()) {
            notifications.notify(recipients, null, "Lượt xe xuất phát sớm", trip.getCode() + ". " + reason, "/coordinator/dispatch");
        }
    }

    @Override
    @Transactional
    public void notifyEarlyOvernightDeparture(TripStop stop, String earlyReason) {
        var recipients = coordinatorRecipients.forTrip(stop.getVehicleTrip());
        if (recipients.isEmpty()) return;
        notifications.notify(recipients, null, "Rời điểm nghỉ đêm sớm",
                "Lượt " + stop.getVehicleTrip().getCode() + " rời " + label(stop) + " trước khi đủ giờ nghỉ đêm. "
                        + "Tổ lái ghi lý do “" + earlyReason.strip() + "”.", null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void departed(TripStop stop) {
        var trip = stop.getVehicleTrip();
        if (SUPPORTED.contains(trip.getTripType())) {
            refresh(stop, stop.getActualDepartureAt(), stop.getActualDepartureAt(),
                    stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId()), configs.getInt(DeliveryEtaPolicy.NOTICE_THRESHOLD));
        }
    }

    @Override
    public void checkHeldTrips() {
        for (Long id : trips.findHeldTripIds(VehicleTripStatus.IN_PROGRESS, SUPPORTED)) {
            try {
                transactions.executeWithoutResult(status -> checkHeldTrip(id));
            } catch (RuntimeException exception) {
                log.error("Không thể cập nhật giờ giao của lượt xe {}", id, exception);
            }
        }
    }

    private void checkHeldTrip(Long id) {
        var trip = trips.findForUpdate(id).orElse(null);
        if (trip == null || trip.getStatus() != VehicleTripStatus.IN_PROGRESS) return;
        var ordered = stops.findByVehicleTrip_IdOrderBySequenceNumber(id);
        var now = clock.instant();
        int threshold = configs.getInt(DeliveryEtaPolicy.NOTICE_THRESHOLD);
        if (!ordered.isEmpty() && ordered.getFirst().getActualDepartureAt() != null) {
            ordered.stream().filter(stop -> stop.getActualArrivalAt() != null && stop.getActualDepartureAt() == null)
                    .filter(stop -> DeliveryEtaPolicy.held(stop.getActualArrivalAt(), stop.getPlannedArrivalAt(),
                            stop.getPlannedDepartureAt(), now, threshold))
                    .findFirst().ifPresent(stop -> refresh(stop, now, now, ordered, threshold));
        }
    }

    private void refresh(TripStop current, Instant departure, Instant now, List<TripStop> ordered, int threshold) {
        var messages = new LinkedHashMap<Long, List<String>>();
        var deliveries = new ArrayList<TripStop>();
        var remaining = new LinkedHashMap<Long, TripStop>();
        int tolerance = configs.getInt(DeliveryEtaPolicy.TOLERANCE);
        for (TripStop stop : ordered) {
            if (stop.getSequenceNumber() <= current.getSequenceNumber() || stop.getActualArrivalAt() != null) continue;
            Instant expected = DeliveryEtaPolicy.arrival(departure, current.getPlannedDepartureAt(), stop.getPlannedArrivalAt());
            stop.setExpectedArrivalAt(expected);
            remaining.put(stop.getId(), stop);
            if (stop.getStopType() != TripStopType.DELIVERY) continue;
            deliveries.add(stop);
            var reference = DeliveryEtaPolicy.reference(stop.getToldArrivalAt(), stop.getPlannedArrivalAt());
            if (DeliveryEtaPolicy.changed(reference, expected, threshold)) {
                stop.setToldArrivalAt(expected);
                stop.setToldAt(now);
                var booking = stop.getBookingAddress().getBooking();
                messages.computeIfAbsent(booking.getId(), ignored -> new ArrayList<>())
                        .add(label(stop) + " · " + CustomerNoticePolicy.deliveryWindowText(DeliveryEtaPolicy.window(expected, tolerance)));
            } else if (stop.getToldArrivalAt() == null) {
                stop.setToldArrivalAt(reference);
                stop.setToldAt(now);
            }
        }
        var sent = new java.util.HashSet<Long>();
        for (TripStop stop : deliveries) {
            var booking = stop.getBookingAddress().getBooking();
            var updates = messages.get(booking.getId());
            if (updates != null && sent.add(booking.getId())) {
                notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(),
                        "Giờ dự kiến thay đổi", String.join("\n", updates), "/orders/" + booking.getId());
            }
        }
        warnCoordinators(current.getVehicleTrip(), deliveries, remaining, now);
    }

    private void warnCoordinators(VehicleTrip trip, List<TripStop> deliveries, Map<Long, TripStop> remaining, Instant now) {
        if (remaining.isEmpty()) return;
        if (trip.getDeliveryWindowWarnedAt() == null && !deliveries.isEmpty()) {
            int endHour = configs.getInt("DELIVERY_WINDOW_END_HOUR");
            var late = deliveries.stream().filter(stop -> DeliveryEtaPolicy.afterDeliveryEnd(
                    stop.getPlannedArrivalAt(), stop.getExpectedArrivalAt(), endHour, ZONE)).map(this::label).toList();
            if (warn(trip, "Vượt giờ giao", late)) trip.setDeliveryWindowWarnedAt(now);
        }
        if (trip.getDeliveryRideWarnedAt() == null) {
            var rules = new TripSchedulePolicy.Rules(0, 0, configs.getInt("MAX_DRIVE_HOURS_PER_DAY"), 0, 0);
            var limit = Duration.ofSeconds(TripSchedulePolicy.rideLimitSeconds(trip.getTripType() == VehicleTripType.DIRECT, rules));
            var late = legs.findActiveOnTrip(trip.getId(), Set.of(HorseLegStatus.PLANNED, HorseLegStatus.FAILED,
                            HorseLegStatus.CANCELLED, HorseLegStatus.COMPLETED)).stream()
                    .filter(leg -> leg.getActualStartAt() != null && leg.getAlightTripStop() != null)
                    .map(leg -> overdueAlight(leg, remaining, limit)).flatMap(java.util.Optional::stream)
                    .map(this::label).distinct().toList();
            if (warn(trip, "Vượt thời gian ngựa ngồi xe", late)) trip.setDeliveryRideWarnedAt(now);
        }
    }

    private java.util.Optional<TripStop> overdueAlight(HorseLeg leg, Map<Long, TripStop> remaining, Duration limit) {
        return java.util.Optional.ofNullable(remaining.get(leg.getAlightTripStop().getId()))
                .filter(stop -> Duration.between(leg.getActualStartAt(), stop.getExpectedArrivalAt()).compareTo(limit) > 0);
    }

    private boolean warn(VehicleTrip trip, String title, List<String> points) {
        if (points.isEmpty()) return false;
        var recipients = coordinatorRecipients.forTrip(trip);
        if (recipients.isEmpty()) return false;
        notifications.notify(recipients, null, title,
                "Lượt " + trip.getCode() + ", " + String.join(", ", points)
                        + ". Cân nhắc đưa các con còn lại về hub giao để giao lại hôm sau.", null);
        return true;
    }

    private String label(TripStop stop) {
        var name = stop.getBookingAddress() == null ? stop.getLocation().getName() : stop.getBookingAddress().getAddressLine();
        return "Điểm " + stop.getSequenceNumber() + " · " + name;
    }
}
