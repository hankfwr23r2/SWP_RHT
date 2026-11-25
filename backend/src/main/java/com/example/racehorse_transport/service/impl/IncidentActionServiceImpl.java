package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentPlanRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.IncidentActionService;
import com.example.racehorse_transport.service.IncidentEvidenceService;
import com.example.racehorse_transport.service.IncidentService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentActionServiceImpl implements IncidentActionService {

    private final IncidentRepository incidents;
    private final IncidentPlanRepository plans;
    private final IncidentService incidentViews;
    private final DispatchService dispatch;
    private final HorseLegRepository legs;
    private final TripStopRepository stops;
    private final IncidentEvidenceService evidence;
    private final StaffRepository staff;
    private final Clock clock;

    @Override
    @Transactional
    public IncidentResponseDto swapVehicle(Long incidentId, Long vehicleId, String username) {
        Incident incident = lockIncident(incidentId);
        IncidentPlan plan = requireApprovedPlan(incidentId, IncidentResolutionType.TRANSFER_VEHICLE);
        evidence.requireVehicleSwapRecord(incidentId);
        Long actor = staff.findByUser_Username(username)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")).getUserId();
        dispatch.swapVehicle(tripOf(incident).getId(), vehicleId, actor,
                "Đã thay xe do sự cố #" + incidentId + ".", clock.instant());
        plan.setStatus(IncidentPlanStatus.DONE);
        return incidentViews.incident(incidentId);
    }

    @Override
    @Transactional
    public IncidentResponseDto returnToHub(Long incidentId, String username) {
        Incident incident = lockIncident(incidentId);
        IncidentPolicy.requireResolutionAvailable(incident.getIncidentType(), IncidentResolutionType.RETURN_TO_HUB);
        requireApprovedPlan(incidentId, IncidentResolutionType.RETURN_TO_HUB);
        VehicleTrip trip = tripOf(incident);
        // Con đang trên xe: chỉ con của sự cố nếu sự cố gắn một con, không thì mọi con
        List<HorseLeg> onBoard = legs.findActiveOnTrip(trip.getId(), Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED)).stream()
                .filter(l -> l.getStatus() == HorseLegStatus.IN_PROGRESS)
                .filter(l -> incident.getHorseLeg() == null || l.getId().equals(incident.getHorseLeg().getId()))
                .toList();
        if (onBoard.isEmpty()) {
            throw new DispatchRuleException("incidentId", "Không có con ngựa nào đang trên xe để đưa về hub.", true);
        }
        int lastAlight = onBoard.stream().mapToInt(l -> l.getAlightTripStop().getSequenceNumber()).min().orElseThrow();
        boolean hubAhead = stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId()).stream()
                .anyMatch(s -> s.getStopType() == TripStopType.HUB && s.getActualArrivalAt() == null && s.getSequenceNumber() > lastAlight);
        if (!hubAhead) {
            throw new DispatchRuleException("incidentId", "Lượt xe không còn điểm hub phía sau các điểm giao để đưa ngựa về.", true);
        }
        Instant now = clock.instant();
        onBoard.forEach(l -> {
            l.setStatus(HorseLegStatus.FAILED);
            l.setActualEndAt(now);
        });
        legs.flush();
        return incidentViews.incident(incidentId);
    }

    private Incident lockIncident(Long incidentId) {
        return incidents.findForUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + incidentId));
    }

    private IncidentPlan requireApprovedPlan(Long incidentId, IncidentResolutionType type) {
        List<IncidentPlan> versions = plans.findByIncident_IdOrderByVersion(incidentId);
        IncidentPlan latest = versions.isEmpty() ? null : versions.getLast();
        if (latest != null && type == IncidentResolutionType.TRANSFER_VEHICLE
                && latest.getResolutionType() == type && latest.getStatus() == IncidentPlanStatus.DONE) {
            throw new DispatchRuleException("incidentId", "Phương án đổi xe đã được thực hiện.", true);
        }
        if (latest == null || latest.getStatus() != IncidentPlanStatus.APPROVED || latest.getResolutionType() != type) {
            throw new DispatchRuleException("incidentId", "Sự cố chưa có phương án " + type + " đã được quản lý duyệt.", true);
        }
        return latest;
    }

    private static VehicleTrip tripOf(Incident incident) {
        VehicleTrip trip = incident.getVehicleTrip() != null ? incident.getVehicleTrip()
                : incident.getHorseLeg() != null ? incident.getHorseLeg().getVehicleTrip() : null;
        if (trip == null) {
            throw new DispatchRuleException("incidentId", "Sự cố không gắn lượt xe.", true);
        }
        return trip;
    }
}
