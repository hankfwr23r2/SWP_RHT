package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.incident.IncidentReportRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.IncidentReportingService;
import com.example.racehorse_transport.service.NotificationService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentReportingServiceImpl implements IncidentReportingService {
    private static final Set<IncidentStatus> OPEN = Set.of(IncidentStatus.REPORTED, IncidentStatus.PLAN_PENDING, IncidentStatus.PLAN_ACTIVE);
    private final VehicleTripRepository trips;
    private final HorseLegRepository legs;
    private final CrewAssignmentRepository crew;
    private final IncidentRepository incidents;
    private final StaffRepository staff;
    private final NotificationService notifications;

    @Override
    @Transactional
    public Long report(IncidentReportRequestDto request, String username) {
        VehicleTrip trip = trips.findForUpdate(request.vehicleTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + request.vehicleTripId()));
        Staff reporter = crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(trip.getId(), username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này.")).getStaff();
        IncidentPolicy.requireReportable(trip.getStatus(), request.incidentType(), request.horseLegId() != null);
        HorseLeg leg = null;
        if (request.horseLegId() != null) {
            leg = legs.findById(request.horseLegId()).filter(l -> l.getVehicleTrip() != null && l.getVehicleTrip().getId().equals(trip.getId()))
                    .orElseThrow(() -> new DispatchRuleException("horseLegId", "Con ngựa này không ở trên lượt xe."));
        } else {
            IncidentPolicy.requireNoOpenTripIncident(incidents.existsByVehicleTrip_IdAndHorseLegIsNullAndStatusIn(trip.getId(), OPEN));
        }
        Incident incident = request.toEntity(trip, leg, leg == null ? null : leg.getBookingHorse().getBooking());
        incident.setReportedBy(reporter);
        incident.setStatus(IncidentStatus.REPORTED);
        Incident saved = incidents.saveAndFlush(incident);
        notifyIncidentReported(saved);
        return saved.getId();
    }

    private void notifyIncidentReported(Incident incident) {
        List<Long> coordinators = staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE);
        if (!coordinators.isEmpty()) {
            notifications.notify(coordinators,
                    incident.getBooking() == null ? null : incident.getBooking().getId(),
                    "Sự cố mới",
                    "Có sự cố mới: " + incident.getTitle() + ".",
                    "/coordinator/incidents");
        }
        if (incident.getHorseLeg() != null) {
            Booking booking = incident.getHorseLeg().getBookingHorse().getBooking();
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(),
                    "Sự cố vận chuyển",
                    "Đơn " + booking.getCode() + " có sự cố: " + incident.getTitle() + ".",
                    "/orders/" + booking.getId());
        } else if (incident.getIncidentType() == IncidentType.DELAY) {
            notifyCustomersOfTripDelay(incident.getVehicleTrip().getId());
        }
    }

    private void notifyCustomersOfTripDelay(Long tripId) {
        Set<Long> notifiedBookings = new HashSet<>();
        for (HorseLeg leg : legs.findActiveOnTrip(tripId, Set.of(HorseLegStatus.PLANNED, HorseLegStatus.COMPLETED,
                HorseLegStatus.FAILED, HorseLegStatus.CANCELLED))) {
            Booking booking = leg.getBookingHorse().getBooking();
            Long bookingId = booking.getId();
            if (notifiedBookings.add(bookingId)) {
                notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId,
                        "Xe đang chạy bị chậm", "Xe đang chở ngựa của đơn " + booking.getCode() + " bị chậm.",
                        "/orders/" + bookingId);
            }
        }
    }

}
