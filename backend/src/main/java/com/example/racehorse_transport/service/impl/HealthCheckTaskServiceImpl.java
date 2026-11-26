package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.HealthCheckTaskPolicy;
import com.example.racehorse_transport.policy.HealthLogPolicy;
import com.example.racehorse_transport.policy.TrackingPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.HealthCheckTaskService;
import com.example.racehorse_transport.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HealthCheckTaskServiceImpl implements HealthCheckTaskService {
    private final HealthCheckTaskRepository tasks;
    private final StaffRepository staff;
    private final VehicleTripRepository trips;
    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final CrewAssignmentRepository crew;
    private final HandoverHorseRepository scans;
    private final TripStopRepository stops;
    private final NotificationService notifications;
    private final Clock clock;

    @Override
    @Transactional
    public HealthCheckTaskResponseDto assign(HealthCheckTaskCreateRequestDto request, String username) {
        Staff coordinator = actor(username);
        Target target = target(request.bookingHorseId(), request.vehicleTripId(), request.horseStayId(), true);
        Instant now = clock.instant();
        Staff escort = staff.findById(request.assignedTo())
                .filter(person -> eligible(person, target, handlerId(target)))
                .orElseThrow(() -> new DispatchRuleException("assignedTo", "Chọn hộ tống đang làm việc và đang ở cùng nơi với con ngựa.", false));
        HealthCheckTask task = taskToAssign(request, target, coordinator, escort, now);
        task.setAssignedBy(coordinator);
        task.setAssignedTo(escort);
        task.setAssignedAt(now);
        task.setAcceptedAt(null);
        task.setOverdueNotifiedAt(null);
        task.setStatus(HealthCheckTaskStatus.ASSIGNED);
        tasks.save(task);
        notifications.notify(List.of(escort.getUserId()), null, "Việc khám mới",
                target.horse().getHorse().getName() + ": xem hạn và nhận việc ở Lượt xe của tôi.", "/my-trips");
        return writeResponse(task, coordinator.getUserId(), now);
    }

    private HealthCheckTask taskToAssign(HealthCheckTaskCreateRequestDto request, Target target,
                                         Staff coordinator, Staff escort, Instant now) {
        boolean hubExit = target.trip() == null ? request.checkType() == HealthCheckType.BEFORE_DELIVERY
                : HealthCheckTaskPolicy.hubExitType(target.trip().getTripType()).filter(request.checkType()::equals).isPresent();
        if (hubExit) {
            var existing = tasks.findExistingForUpdate(request.bookingHorseId(), request.checkType(),
                    request.vehicleTripId(), request.horseStayId());
            if (!existing.isEmpty()) {
                var task = existing.getFirst();
                if (!Objects.equals(coordinator.getUserId(), task.getAssignedBy().getUserId())) {
                    throw new AccessDeniedException("Chỉ điều phối đã giao mới giao lại được việc khám này.");
                }
                HealthCheckTaskPolicy.requireReassignable(task.getStatus());
                task.setNote(request.note());
                return task;
            }
        }
        HealthCheckTaskPolicy.requireFutureDue(request.dueAt(), now);
        return request.toEntity(target.horse(), target.trip(), target.stay(), escort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealthCheckAssigneeResponseDto> assignees(Long horseId, Long tripId, Long stayId) {
        Target target = target(horseId, tripId, stayId, false);
        Long handlerId = handlerId(target);
        return staff.findAllByOrderByStaffCodeAsc().stream()
                .filter(person -> eligible(person, target, handlerId))
                .map(HealthCheckAssigneeResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealthCheckTaskResponseDto> ofHorse(Long horseId, String username) {
        Long userId = actor(username).getUserId();
        Instant now = clock.instant();
        return tasks.findByBookingHorse_IdOrderByDueAt(horseId).stream()
                .map(task -> response(task, userId, now)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HealthCheckTaskResponseDto> mine(String username) {
        Long userId = actor(username).getUserId();
        Instant now = clock.instant();
        var assigned = tasks.findMine(userId, HealthCheckTaskPolicy.OPEN, HealthCheckTaskPolicy.HUB_EXIT_TYPES);
        if (assigned.isEmpty()) return List.of();
        var horseIds = assigned.stream().map(task -> task.getBookingHorse().getId()).distinct().toList();
        var openStays = stays.findOpenHubStays(horseIds).stream()
                .collect(Collectors.groupingBy(stay -> stay.getBookingHorse().getId()));
        var byHorse = legsOfHorses(horseIds);
        List<HealthCheckTaskResponseDto> result = new ArrayList<>();
        for (HealthCheckTask task : assigned) {
            Long horseId = task.getBookingHorse().getId();
            var hubStay = openStays.getOrDefault(horseId, List.of()).stream()
                    .filter(stay -> matchesHubExit(task, stay, byHorse.getOrDefault(horseId, List.of())))
                    .findFirst();
            if (HealthCheckTaskPolicy.OPEN.contains(task.getStatus()) || hubStay.isPresent()) {
                result.add(response(task, userId, now, hubStay.map(HorseStay::getId).orElse(null)));
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<HealthCheckTaskResponseDto>> atHubStays(List<HorseStay> stays, String username) {
        if (stays.isEmpty()) return Map.of();
        Long userId = actor(username).getUserId();
        Instant now = clock.instant();
        var horseIds = stays.stream().map(stay -> stay.getBookingHorse().getId()).distinct().toList();
        var byHorse = legsOfHorses(horseIds);
        var checks = tasks.findByBookingHorse_IdInAndCheckTypeInAndStatusNotOrderByDueAt(
                        horseIds, HealthCheckTaskPolicy.HUB_EXIT_TYPES, HealthCheckTaskStatus.CANCELLED).stream()
                .collect(Collectors.groupingBy(task -> task.getBookingHorse().getId()));
        return stays.stream().filter(HealthCheckTaskPolicy::isOpenAtHub).collect(Collectors.toMap(HorseStay::getId, stay -> {
            Long horseId = stay.getBookingHorse().getId();
            return checks.getOrDefault(horseId, List.of()).stream()
                    .filter(task -> matchesHubExit(task, stay, byHorse.getOrDefault(horseId, List.of())))
                    .map(task -> response(task, userId, now, stay.getId())).toList();
        }));
    }

    private Map<Long, List<HorseLeg>> legsOfHorses(List<Long> horseIds) {
        return legs.findByBookingHorse_IdInOrderByBookingHorse_IdAscSequenceNumberAsc(horseIds).stream()
                .collect(Collectors.groupingBy(leg -> leg.getBookingHorse().getId()));
    }

    private static boolean matchesHubExit(HealthCheckTask task, HorseStay stay, List<HorseLeg> journey) {
        if (task.getHorseStay() != null) {
            return task.getCheckType() == HealthCheckType.BEFORE_DELIVERY && task.getHorseStay().getId().equals(stay.getId());
        }
        return matchesTripCheckType(task) && journey.stream().anyMatch(leg -> HealthCheckTaskPolicy.boardsFromHub(leg, stay)
                && task.getVehicleTrip().getId().equals(leg.getVehicleTrip().getId()));
    }

    private static boolean matchesTripCheckType(HealthCheckTask task) {
        return HealthCheckTaskPolicy.hubExitType(task.getVehicleTrip().getTripType()).filter(task.getCheckType()::equals).isPresent();
    }

    @Override
    @Transactional
    public HealthCheckTaskResponseDto accept(Long id, String username) {
        Staff escort = actor(username);
        HealthCheckTask task = locked(id);
        if (!Objects.equals(escort.getUserId(), task.getAssignedTo().getUserId()) || !activeEscort(escort)) {
            throw new AccessDeniedException("Chỉ hộ tống được giao mới nhận được việc khám này.");
        }
        HealthCheckTaskPolicy.requireTransition(task.getStatus(), HealthCheckTaskStatus.ACCEPTED);
        Instant now = clock.instant();
        task.setStatus(HealthCheckTaskStatus.ACCEPTED);
        task.setAcceptedAt(now);
        return writeResponse(task, escort.getUserId(), now);
    }

    @Override
    @Transactional
    public HealthCheckTaskResponseDto cancel(Long id, HealthCheckTaskCancelRequestDto request, String username) {
        Long userId = actor(username).getUserId();
        HealthCheckTask task = locked(id);
        if (!Objects.equals(userId, task.getAssignedBy().getUserId())) {
            throw new AccessDeniedException("Chỉ điều phối đã giao mới hủy được việc khám này.");
        }
        HealthCheckTaskPolicy.requireTransition(task.getStatus(), HealthCheckTaskStatus.CANCELLED);
        task.setCancelReason(request.reason().strip());
        task.setStatus(HealthCheckTaskStatus.CANCELLED);
        return writeResponse(task, userId, clock.instant());
    }

    @Override
    @Transactional
    public boolean mayRecordStay(Long escortId, HorseStay stay, Instant recordedAt) {
        return staff.findById(escortId).filter(HealthCheckTaskServiceImpl::activeEscort).isPresent()
                && !openForLog(escortId, stay.getBookingHorse().getId(), stay, null, recordedAt).isEmpty();
    }

    @Override
    @Transactional
    public void completeFrom(HorseHealthLog log) {
        HorseStay stay = log.getHorseStay();
        Long tripId = log.getTripStop() == null ? null : log.getTripStop().getVehicleTrip().getId();
        if (stay == null && tripId == null) return;
        for (HealthCheckTask task : openForLog(log.getRecordedBy().getUserId(), log.getBookingHorse().getId(),
                stay, tripId, log.getRecordedAt())) {
            HealthCheckTaskPolicy.requireTransition(task.getStatus(), HealthCheckTaskStatus.DONE);
            task.setStatus(HealthCheckTaskStatus.DONE);
            task.setDoneAt(log.getRecordedAt());
            task.setHealthLog(log);
        }
    }

    private List<HealthCheckTask> openForLog(Long staffId, Long horseId, HorseStay stay, Long tripId, Instant recordedAt) {
        List<Long> boardingTripIds = stay == null || !HealthCheckTaskPolicy.isOpenAtHub(stay) ? List.of()
                : legs.findByBookingHorse_IdOrderBySequenceNumber(horseId).stream()
                    .filter(leg -> HealthCheckTaskPolicy.boardsFromHub(leg, stay))
                    .map(leg -> leg.getVehicleTrip().getId()).distinct().toList();
        return tasks.findForLog(staffId, horseId, stay == null ? null : stay.getId(), tripId, recordedAt,
                        HealthCheckTaskPolicy.OPEN, HealthCheckTaskPolicy.HUB_EXIT_TYPES, boardingTripIds).stream()
                .filter(task -> stay == null || task.getVehicleTrip() == null || matchesTripCheckType(task)).toList();
    }

    @Override
    @Transactional
    public void notifyOverdue() {
        Instant now = clock.instant();
        for (HealthCheckTask task : tasks.findByOverdueNotifiedAtIsNullAndDueAtBeforeAndStatusInOrderById(now, HealthCheckTaskPolicy.OPEN)) {
            notifications.notify(List.of(task.getAssignedBy().getUserId()), task.getBookingHorse().getBooking().getId(),
                    "Việc khám quá hạn", task.getBookingHorse().getHorse().getName() + " (#" + task.getId() + ")", "/coordinator/tracking");
            task.setOverdueNotifiedAt(now);
        }
    }

    private Staff actor(String username) {
        return staff.findByUser_Username(username)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }

    private HealthCheckTask locked(Long id) {
        return tasks.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy việc khám " + id));
    }

    private HealthCheckTaskResponseDto writeResponse(HealthCheckTask task, Long actor, Instant now) {
        // Fetch response relations separately so the write lock only targets the task row.
        HealthCheckTask details = tasks.findOneById(task.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy việc khám " + task.getId()));
        return response(details, actor, now);
    }

    private static HealthCheckTaskResponseDto response(HealthCheckTask task, Long actor, Instant now) {
        return response(task, actor, now, null);
    }

    private static HealthCheckTaskResponseDto response(HealthCheckTask task, Long actor, Instant now, Long hubStayId) {
        boolean open = HealthCheckTaskPolicy.OPEN.contains(task.getStatus());
        return HealthCheckTaskResponseDto.from(task, HealthCheckTaskPolicy.overdue(task.getStatus(), task.getDueAt(), now),
                open && Objects.equals(actor, task.getAssignedBy().getUserId()),
                open && Objects.equals(actor, task.getAssignedTo().getUserId()) ? hubStayId : null);
    }

    private Target target(Long horseId, Long tripId, Long stayId, boolean lockTarget) {
        HealthLogPolicy.requireOnePlace(tripId != null, stayId != null);
        if (stayId != null) {
            HorseStay stay = (lockTarget ? stays.findForUpdate(stayId) : stays.findById(stayId)).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần lưu trú " + stayId));
            if (!Objects.equals(stay.getBookingHorse().getId(), horseId) || stay.getActualStartAt() == null
                    || stay.getActualEndAt() != null || stay.getLocation().getLocationType() != LocationType.HUB
                    || !HealthLogPolicy.canRecordStay(stay.getLocation().getLocationType(), stay.getStayType(), stay.getOperatorParty() != null)) {
                throw new DispatchRuleException("horseStayId", "Con ngựa không đang lưu trú ở hub này.", true);
            }
            return new Target(stay.getBookingHorse(), null, stay);
        }
        VehicleTrip trip = (lockTarget ? trips.findForUpdate(tripId) : trips.findById(tripId)).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + tripId));
        if (trip.getStatus() == VehicleTripStatus.CANCELLED || trip.getStatus() == VehicleTripStatus.COMPLETED) {
            throw new DispatchRuleException("vehicleTripId", "Lượt xe đã kết thúc hoặc đã hủy.", true);
        }
        BookingHorse horse = legs.findByBookingHorse_IdOrderBySequenceNumber(horseId).stream()
                .filter(leg -> leg.getVehicleTrip() != null && Objects.equals(leg.getVehicleTrip().getId(), tripId))
                .filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED || leg.getStatus() == HorseLegStatus.IN_PROGRESS)
                .map(HorseLeg::getBookingHorse).findFirst()
                .orElseThrow(() -> new DispatchRuleException("vehicleTripId", "Con ngựa không ở lượt xe này.", true));
        return new Target(horse, trip, null);
    }

    private Long handlerId(Target target) {
        if (target.stay() == null) return null;
        Handover handover = latestCompanyHandovers(List.of(target.horse().getId())).get(target.horse().getId());
        return handover == null ? null : handover.getHandledBy().getUserId();
    }

    private boolean eligible(Staff escort, Target target, Long handlerId) {
        if (!activeEscort(escort)) return false;
        var assignments = crew.findActiveOfStaff(escort.getUserId(), CrewAssignmentStatus.RELEASED, VehicleTripStatus.CANCELLED);
        if (target.trip() != null) {
            return assignments.stream().anyMatch(a -> a.getCrewRole() == CrewRole.ESCORT
                    && Objects.equals(a.getVehicleTrip().getId(), target.trip().getId()));
        }
        if (Objects.equals(handlerId, escort.getUserId())) return true;
        var running = assignments.stream().map(CrewAssignment::getVehicleTrip)
                .filter(trip -> trip.getStatus() == VehicleTripStatus.IN_PROGRESS).toList();
        Long hubId = target.stay().getLocation().getId();
        if (!running.isEmpty()) {
            return running.size() == 1 && stops.findFirstByVehicleTrip_IdAndActualArrivalAtIsNotNullOrderBySequenceNumberDesc(
                    running.getFirst().getId()).filter(stop -> stop.getActualDepartureAt() == null && stop.getLocation() != null)
                    .map(stop -> Objects.equals(stop.getLocation().getId(), hubId)).orElse(false);
        }
        Location inferred = assignments.stream().map(CrewAssignment::getVehicleTrip)
                .filter(trip -> trip.getStatus() == VehicleTripStatus.COMPLETED && trip.getActualEndAt() != null)
                .max(Comparator.comparing(VehicleTrip::getActualEndAt).thenComparing(VehicleTrip::getId))
                .map(VehicleTrip::getEndLocation).orElseGet(escort::getHomeHubLocation);
        return Objects.equals(inferred.getId(), hubId);
    }

    private static boolean activeEscort(Staff staff) {
        return staff.getEmploymentStatus() == EmploymentStatus.ACTIVE && staff.getUser().getStatus() == AppUserStatus.ACTIVE
                && "ESCORT".equals(staff.getUser().getRole().getCode());
    }

    private record Target(BookingHorse horse, VehicleTrip trip, HorseStay stay) {}

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Handover> latestCompanyHandovers(List<Long> horseIds) {
        if (horseIds.isEmpty()) return Map.of();
        return scans.findByBookingHorseIds(horseIds).stream()
                .filter(scan -> Boolean.TRUE.equals(scan.getMicrochipMatched()))
                .filter(scan -> {
                    Handover handover = scan.getHandover();
                    return handover.getStatus() == HandoverStatus.DONE
                            && TrackingPolicy.holderAfter(handover.getHandoverType(), handover.getToParty() != null)
                                    == TrackingPolicy.CustodyHolder.COMPANY;
                })
                .collect(Collectors.toMap(scan -> scan.getHorseLeg().getBookingHorse().getId(), HandoverHorse::getHandover,
                        BinaryOperator.maxBy(Comparator.comparing(Handover::getHandedAt).thenComparing(Handover::getId))));
    }

}
