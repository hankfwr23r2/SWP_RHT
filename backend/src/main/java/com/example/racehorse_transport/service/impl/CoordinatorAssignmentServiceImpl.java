package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.CoordinatorAssignmentRequestDto;
import com.example.racehorse_transport.dto.dispatch.CoordinatorTakeOverRequestDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CoordinatorAssignmentPolicy;
import com.example.racehorse_transport.policy.BookingReviewPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.service.NotificationService;
import java.time.Clock;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.racehorse_transport.policy.CoordinatorAssignmentPolicy.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CoordinatorAssignmentServiceImpl implements CoordinatorAssignmentService {
    private final AppUserRepository users;
    private final StaffRepository staff;
    private final DepartureRepository departures;
    private final VehicleTripRepository trips;
    private final IncidentRepository incidents;
    private final CostItemRepository costs;
    private final CoordinatorAssignmentHistoryRepository history;
    private final NotificationService notifications;
    private final Clock clock;

    @Override
    public void assignDeparture(Long id, CoordinatorAssignmentRequestDto request, String username) {
        AppUser manager = requireRole(username, "MANAGER");
        Departure departure = departures.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến."));
        Staff previous = departure.getCoordinator();
        if (previous != null && previous.getUserId().equals(request.coordinatorId())) {
            return;
        }
        var linehaul = trips.findByDeparture_IdAndTripTypeAndStatusNotInOrderById(
                id, VehicleTripType.LINEHAUL, CLOSED_TRIPS);
        Staff next = coordinator(request.coordinatorId());
        boolean transfersTrip = linehaul.stream().map(VehicleTrip::getCoordinator)
                .anyMatch(owner -> owner != null && !owner.getUserId().equals(next.getUserId()));
        String reason = reason(request.reason(), previous, next, transfersTrip);
        departure.setCoordinator(next);
        recordChange(departure, null, previous, next, manager, reason);
        linehaul.forEach(trip -> changeTrip(trip, next, manager, reason));
    }

    @Override
    public void assignTrip(Long id, CoordinatorAssignmentRequestDto request, String username) {
        AppUser manager = requireRole(username, "MANAGER");
        VehicleTrip trip = lockedTrip(id);
        Staff next = coordinator(request.coordinatorId());
        changeTrip(trip, next, manager, reason(request.reason(), trip.getCoordinator(), next, false));
    }

    @Override
    public void takeOver(Long id, CoordinatorTakeOverRequestDto request, String username) {
        AppUser actor = requireRole(username, "COORDINATOR");
        VehicleTrip trip = lockedTrip(id);
        CoordinatorAssignmentPolicy.requireTakeOver(trip.getStatus(),
                openIncidents(id) > 0);
        Staff next = coordinator(actor.getId());
        Staff previous = trip.getCoordinator();
        if (!differs(previous, next)) {
            throw BookingRuleException.conflict("Bạn đã phụ trách lượt xe này.");
        }
        String reason = BookingReviewPolicy.requireReason(request.reason());
        changeTrip(trip, next, actor, reason);
        var recipients = new LinkedHashSet<>(staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE));
        if (previous != null) {
            recipients.add(previous.getUserId());
        }
        notifications.notify(recipients.stream().toList(), null, "Lượt xe đã được nhận thay",
                next.getFullName() + " nhận lượt " + trip.getCode() + ". " + reason, "/coordinator/dispatch");
    }

    @Override
    public VehicleTrip requireTripWrite(Long tripId, String username) {
        AppUser actor = actor(username);
        VehicleTrip trip = lockedTrip(tripId);
        if (!"COORDINATOR".equals(actor.getRole().getCode())) {
            return trip;
        }
        CoordinatorAssignmentPolicy.requireOwner(trip.getCoordinator(), actor.getId());
        if (trip.getCoordinator() == null) {
            changeTrip(trip, coordinator(actor.getId()), actor, null);
        }
        return trip;
    }

    @Override
    public void requireIncidentWrite(Long incidentId, String username) {
        if ("COORDINATOR".equals(actor(username).getRole().getCode())) {
            incidents.findVehicleTripId(incidentId).ifPresent(tripId -> requireTripWrite(tripId, username));
        }
    }

    @Override
    public void requireCostWrite(Long costId, String username) {
        if ("COORDINATOR".equals(actor(username).getRole().getCode())) {
            costs.findIncidentId(costId).ifPresent(incidentId -> requireIncidentWrite(incidentId, username));
            costs.findVehicleTripId(costId).ifPresent(tripId -> requireTripWrite(tripId, username));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long openIncidents(Long tripId) {
        return incidents.countOpenForTrip(tripId, CLOSED_INCIDENTS);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean tripExists(Long tripId) {
        return trips.existsById(tripId);
    }

    @Override
    @Transactional(readOnly = true)
    public Staff currentStaff(String username) {
        return staff.findByUser_Username(username).orElseThrow(
                () -> new org.springframework.security.access.AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<Long> activeManagerIds() {
        return staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
    }

    @Override
    public void assignAtCreation(VehicleTrip trip, String username) {
        AppUser actor = requireRole(username, "COORDINATOR");
        Staff inherited = trip.getDeparture() == null ? null : trip.getDeparture().getCoordinator();
        Staff next = inherited == null ? coordinator(actor.getId()) : coordinator(inherited.getUserId());
        changeTrip(trip, next, actor, null);
    }

    private VehicleTrip lockedTrip(Long id) {
        return trips.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe."));
    }

    private Staff coordinator(Long id) {
        Staff result = staff.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điều phối."));
        CoordinatorAssignmentPolicy.requireAvailable(result);
        return result;
    }

    private AppUser actor(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
    }

    private AppUser requireRole(String username, String role) {
        AppUser actor = actor(username);
        if (!role.equals(actor.getRole().getCode())) {
            throw BookingRuleException.forbidden("Bạn không có quyền thực hiện thao tác này.");
        }
        return actor;
    }

    private static boolean differs(Staff previous, Staff next) {
        return previous == null || !previous.getUserId().equals(next.getUserId());
    }

    private static String reason(String text, Staff previous, Staff next, boolean transfersTrip) {
        if (transfersTrip || (previous != null && differs(previous, next))) {
            return BookingReviewPolicy.requireReason(text);
        }
        return text == null || text.isBlank() ? null : text.strip();
    }

    private void changeTrip(VehicleTrip trip, Staff next, AppUser actor, String reason) {
        Staff previous = trip.getCoordinator();
        if (differs(previous, next)) {
            trip.setCoordinator(next);
            recordChange(null, trip, previous, next, actor, reason);
        }
    }

    private void recordChange(Departure departure, VehicleTrip trip, Staff previous, Staff next, AppUser actor, String reason) {
        var change = new CoordinatorAssignmentHistory();
        change.setDeparture(departure);
        change.setVehicleTrip(trip);
        change.setFromStaff(previous);
        change.setToStaff(next);
        change.setChangedBy(actor);
        change.setReason(reason);
        change.setChangedAt(clock.instant());
        history.save(change);
    }
}
