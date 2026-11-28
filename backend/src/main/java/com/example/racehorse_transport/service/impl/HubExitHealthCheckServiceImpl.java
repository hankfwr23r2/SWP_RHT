package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.HealthCheckTask;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import com.example.racehorse_transport.policy.HealthCheckTaskPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HealthCheckTaskRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.CoordinatorRecipientService;
import com.example.racehorse_transport.service.HubExitHealthCheckService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HubExitHealthCheckServiceImpl implements HubExitHealthCheckService {
    private final HealthCheckTaskRepository tasks;
    private final HorseLegRepository legs;
    private final HorseStayRepository stays;
    private final VehicleTripRepository trips;
    private final CrewAssignmentRepository crew;
    private final CoordinatorRecipientService coordinators;
    private final StaffRepository staff;
    private final SystemConfigService configs;
    private final NotificationService notifications;
    private final Clock clock;

    @Override
    @Transactional
    public void scheduleBoardingChecks() {
        Instant now = clock.instant();
        Instant cutoff = cutoff(now);
        for (Long tripId : legs.findTripsWithHubBoardingsDueBy(cutoff, HealthCheckTaskPolicy.hubExitTripTypes())) {
            // Cùng khóa lượt xe với giao khám thủ công và hoàn tất HUB_OUT.
            trips.findForUpdate(tripId).ifPresent(trip -> scheduleTrip(trip, now, cutoff));
        }
    }

    @Override
    @Transactional
    public void scheduleCollectionChecks() {
        Instant now = clock.instant();
        Instant cutoff = cutoff(now);
        for (Long stayId : stays.findHubCollectionsDueBy(cutoff)) {
            // Cùng khóa ca với hẹn giờ, giao khám thủ công và bàn giao cho người nhận.
            stays.findForUpdate(stayId).ifPresent(stay -> scheduleCollection(stay, now, cutoff));
        }
    }

    private Instant cutoff(Instant now) {
        return now.plus(configs.getInt("PRE_EXIT_CHECK_HOURS"), ChronoUnit.HOURS);
    }

    private void scheduleTrip(VehicleTrip trip, Instant now, Instant cutoff) {
        Staff coordinator = trip.getCoordinator();
        var type = HealthCheckTaskPolicy.hubExitType(trip.getTripType());
        if (coordinator == null || type.isEmpty()) return;

        var pending = legs.findActiveOnTrip(trip.getId(), Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED,
                HorseLegStatus.IN_PROGRESS, HorseLegStatus.COMPLETED));
        if (pending.isEmpty()) return;
        var byHorse = stays.findOpenHubStays(pending.stream().map(leg -> leg.getBookingHorse().getId()).distinct().toList())
                .stream().collect(Collectors.groupingBy(stay -> stay.getBookingHorse().getId()));
        Staff assignee = crew.findByVehicleTrip_IdInOrderById(List.of(trip.getId())).stream()
                .filter(assignment -> assignment.getCrewRole() == CrewRole.ESCORT && assignment.getStatus() != CrewAssignmentStatus.RELEASED)
                .map(CrewAssignment::getStaff).filter(person -> activeWithRole(person, "ESCORT"))
                .findFirst().orElse(coordinator);
        for (HorseLeg leg : pending) {
            var horse = leg.getBookingHorse();
            if (horse.getStatus() != BookingHorseStatus.ACTIVE || byHorse.getOrDefault(horse.getId(), List.of()).stream()
                    .noneMatch(stay -> HealthCheckTaskPolicy.boardsFromHub(leg, stay))) continue;
            Instant dueAt = leg.getBoardTripStop().getPlannedDepartureAt();
            if (!dueAt.isAfter(cutoff)) {
                scheduleCheck(horse, trip, null, type.get(), coordinator, assignee, now, dueAt);
            }
        }
    }

    private void scheduleCollection(HorseStay stay, Instant now, Instant cutoff) {
        var horse = stay.getBookingHorse();
        if (!HealthCheckTaskPolicy.isOpenAtHub(stay) || stay.getBalanceHoldAt() != null
                || horse.getStatus() != BookingHorseStatus.ACTIVE || horse.getBooking().getStatus() != BookingStatus.IN_OPERATION) return;
        var dueAt = collectionTime(stay).filter(at -> !at.isAfter(cutoff));
        if (dueAt.isEmpty()) return;
        var owner = Optional.ofNullable(coordinators.atHub(horse)).filter(person -> activeWithRole(person, "COORDINATOR"))
                .or(() -> staff.findActiveForRole("COORDINATOR").stream().min(
                        Comparator.comparing((Staff person) -> !person.getHomeHubLocation().getId().equals(stay.getLocation().getId()))
                                .thenComparing(Staff::getUserId)));
        owner.ifPresent(coordinator -> scheduleCheck(horse, null, stay, HealthCheckType.BEFORE_DELIVERY,
                coordinator, coordinator, now, dueAt.get()));
    }

    private Optional<Instant> collectionTime(HorseStay stay) {
        boolean self = stay.getBookingHorse().getBooking().getDeliveryMethod() == TransferMethod.SELF;
        var journey = legs.findByBookingHorse_IdOrderBySequenceNumber(stay.getBookingHorse().getId()).stream()
                .filter(leg -> leg.getStatus() != HorseLegStatus.CANCELLED).toList();
        if (journey.isEmpty()) return Optional.empty();

        if (self && HealthCheckTaskPolicy.arrivedAtHub(journey.getLast(), stay)) {
            if (stay.getStayType() == HorseStayType.ASSEMBLY) return Optional.of(stay.getPlannedEndAt());
            if (stay.getStayType() == HorseStayType.STABLING) {
                // Chuyển sang gửi chuồng do trễ hẹn thì ca mới bắt đầu tính từ giờ hẹn cũ.
                return Optional.ofNullable(stay.getCollectionExpectedAt()).or(() -> Optional.of(stay.getPlannedStartAt()));
            }
        }
        if (stay.getStayType() == HorseStayType.STABLING && stay.getCollectionExpectedAt() != null) {
            var last = journey.stream().filter(leg -> leg.getStatus() != HorseLegStatus.PLANNED).reduce((first, next) -> next);
            return last.filter(leg -> leg.getStatus() == HorseLegStatus.FAILED && leg.getVehicleTrip() != null
                            && leg.getVehicleTrip().getEndLocation().getId().equals(stay.getLocation().getId()))
                    .map(leg -> stay.getCollectionExpectedAt());
        }
        return Optional.empty();
    }

    private void scheduleCheck(BookingHorse horse, VehicleTrip trip, HorseStay stay, HealthCheckType type,
                               Staff coordinator, Staff assignee, Instant now, Instant dueAt) {
        var existing = tasks.findExistingForUpdate(horse.getId(), type, trip == null ? null : trip.getId(),
                stay == null ? null : stay.getId());
        if (!existing.isEmpty()) {
            existing.stream().filter(task -> HealthCheckTaskPolicy.OPEN.contains(task.getStatus()) && !dueAt.equals(task.getDueAt()))
                    .forEach(task -> {
                        task.setDueAt(dueAt);
                        task.setOverdueNotifiedAt(null);
                    });
            return;
        }

        HealthCheckTask task = new HealthCheckTask();
        task.setBookingHorse(horse);
        task.setVehicleTrip(trip);
        task.setHorseStay(stay);
        task.setCheckType(type);
        task.setDueAt(dueAt);
        task.setAssignedBy(coordinator);
        task.setAssignedTo(assignee);
        task.setAssignedAt(now);
        task.setStatus(HealthCheckTaskStatus.ASSIGNED);
        tasks.save(task);
        notifications.notify(List.of(assignee.getUserId()), null, "Việc khám mới",
                horse.getHorse().getName() + ": xem hạn khám trước khi rời hub.",
                assignee.getUserId().equals(coordinator.getUserId()) ? "/coordinator/hub" : "/my-trips");
    }

    private static boolean activeWithRole(Staff person, String role) {
        return person.getEmploymentStatus() == EmploymentStatus.ACTIVE && person.getUser().getStatus() == AppUserStatus.ACTIVE
                && role.equals(person.getUser().getRole().getCode());
    }
}
