package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.dto.incident.IncidentSeverityRequestDto;
import com.example.racehorse_transport.dto.incident.TripUnlockRequestDto;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.IncidentSeverityService;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentSeverityServiceImpl implements IncidentSeverityService {

    private final IncidentRepository incidents;
    private final VehicleTripRepository trips;
    private final StaffRepository staff;
    private final AuditService audit;
    private final Clock clock;
    private final CoordinatorAssignmentService coordinators;

    @Override
    @Transactional(readOnly = true)
    public Map<IncidentType, IncidentSeverity> defaults() {
        return Arrays.stream(IncidentType.values())
                .collect(Collectors.toMap(Function.identity(), IncidentPolicy::defaultSeverity));
    }

    @Override
    @Transactional
    public void changeSeverity(Long incidentId, IncidentSeverityRequestDto request, String username) {
        coordinators.requireIncidentWrite(incidentId, username);
        Incident incident = incidents.findByIdForSeverityUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + incidentId));
        IncidentSeverity previous = incident.getSeverity();
        if (previous == request.severity()) {
            return;
        }
        Staff actor = currentStaff(username);
        // Đồng bộ với thao tác tới/rời điểm dừng đang giữ khóa lượt xe.
        incidents.findVehicleTripId(incidentId).ifPresent(tripId -> trips.findForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + tripId)));
        request.applyTo(incident);
        audit.record(actor.getUserId(), "CHANGE_INCIDENT_SEVERITY", "incident", incidentId,
                Map.of("severity", previous), Map.of("severity", incident.getSeverity()));
    }

    @Override
    @Transactional
    public void unlockTrip(Long incidentId, TripUnlockRequestDto request, String username) {
        coordinators.requireIncidentWrite(incidentId, username);
        Incident incident = incidents.findForUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + incidentId));
        Long tripId = incidents.findVehicleTripId(incidentId).orElse(null);
        if (tripId == null || !IncidentPolicy.blocksTrip(incident.getSeverity(), incident.getStatus(), incident.getTripUnlockedAt())) {
            throw new DispatchRuleException("incidentId", "Sự cố này không khóa lượt xe.", true);
        }
        Staff actor = currentStaff(username);
        request.applyTo(incident);
        incident.setTripUnlockedBy(actor);
        incident.setTripUnlockedAt(clock.instant());
        audit.record(actor.getUserId(), "UNLOCK_TRIP_FOR_INCIDENT", "incident", incidentId, null,
                Map.of("vehicleTripId", tripId, "reason", incident.getTripUnlockReason(),
                        "unlockedAt", incident.getTripUnlockedAt().toString()));
    }

    private Staff currentStaff(String username) {
        return staff.findByUser_Username(username)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }
}
