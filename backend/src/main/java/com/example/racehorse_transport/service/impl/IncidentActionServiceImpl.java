package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.HealthLogService;
import com.example.racehorse_transport.service.IncidentActionService;
import com.example.racehorse_transport.service.IncidentEvidenceService;
import com.example.racehorse_transport.service.IncidentPlanService;
import com.example.racehorse_transport.service.IncidentReturnService;
import com.example.racehorse_transport.service.IncidentService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentActionServiceImpl implements IncidentActionService {

    private final IncidentPlanService plans;
    private final IncidentService incidentViews;
    private final DispatchService dispatch;
    private final IncidentEvidenceService evidence;
    private final StaffRepository staff;
    private final Clock clock;
    private final HealthLogService healthLogs;
    private final IncidentReturnService returns;

    @Override
    @Transactional
    public IncidentResponseDto swapVehicle(Long incidentId, Long vehicleId, String username) {
        IncidentPlan plan = plans.lockApproved(incidentId, IncidentResolutionType.TRANSFER_VEHICLE, null);
        evidence.requireVehicleSwapRecord(incidentId);
        Long actor = staff.findByUser_Username(username)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")).getUserId();
        dispatch.swapVehicle(IncidentPolicy.requireTrip(plan.getIncident()).getId(), vehicleId, actor,
                "Đã thay xe do sự cố #" + incidentId + ".", clock.instant());
        plan.setStatus(IncidentPlanStatus.DONE);
        return incidentViews.incident(incidentId);
    }

    @Override
    @Transactional
    public IncidentResponseDto returnToHub(Long incidentId, String username) {
        return returns.returnHorses(incidentId, IncidentResolutionType.RETURN_TO_HUB, null, username);
    }

    @Override
    @Transactional
    public IncidentResponseDto cancelLegs(Long incidentId, Long planId, String username) {
        return returns.returnHorses(incidentId, IncidentResolutionType.CANCEL, planId, username);
    }

    @Override
    @Transactional
    public IncidentResponseDto recordVetResult(Long incidentId, Long planId, String note, String username) {
        IncidentPlan plan = plans.lockApproved(incidentId, IncidentResolutionType.VET_TREATMENT, planId);
        if (plan.getHandlerParty() == null) {
            throw new DispatchRuleException("handlerPartyId", "Phương án chưa có đối tác thú y.", true);
        }
        Incident incident = plan.getIncident();
        healthLogs.recordVetResult(IncidentPolicy.requireTrip(incident),
                incident.getHorseLeg() == null ? null : incident.getHorseLeg().getBookingHorse(),
                "Thú y: " + plan.getHandlerParty().getDisplayName() + "\n" + note.strip(), username);
        plan.setStatus(IncidentPlanStatus.DONE);
        return incidentViews.incident(incidentId);
    }
}
