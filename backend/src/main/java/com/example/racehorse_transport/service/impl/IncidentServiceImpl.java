package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.incident.IncidentPlanRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentReportRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto.PlanDto;
import com.example.racehorse_transport.dto.incident.PlanRejectRequestDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentPlanRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.IncidentService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private static final Set<IncidentStatus> OPEN = Set.of(IncidentStatus.REPORTED, IncidentStatus.PLAN_PENDING, IncidentStatus.PLAN_ACTIVE);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final IncidentRepository incidents;
    private final IncidentPlanRepository plans;
    private final VehicleTripRepository trips;
    private final HorseLegRepository legs;
    private final CrewAssignmentRepository crew;
    private final StaffRepository staff;
    private final PartyRepository parties;
    private final PartyRoleRepository partyRoles;
    private final SystemConfigService configs;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponseDto> open() {
        return describe(incidents.findByStatusInOrderByReportedAtDesc(OPEN));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponseDto> ofTrip(Long vehicleTripId) {
        return describe(incidents.findByVehicleTrip_IdOrderByReportedAtDesc(vehicleTripId));
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponseDto incident(Long id) {
        return describe(List.of(incidents.findOneById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + id)))).getFirst();
    }

    @Override
    @Transactional
    public IncidentResponseDto report(IncidentReportRequestDto request, String username) {
        VehicleTrip trip = trips.findForUpdate(request.vehicleTripId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + request.vehicleTripId()));
        Staff reporter = crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(trip.getId(), username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này.")).getStaff();
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
        return incident(incidents.saveAndFlush(incident).getId());
    }

    @Override
    @Transactional
    public IncidentResponseDto propose(Long incidentId, IncidentPlanRequestDto request, String username) {
        Incident incident = openIncident(incidentId);
        Party handler = request.handlerPartyId() == null ? null : parties.findById(request.handlerPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đối tác " + request.handlerPartyId()));
        IncidentPolicy.requireHandler(request.resolutionType(), handler != null, handler == null ? List.of() : validRoles(handler.getId()));
        List<IncidentPlan> existing = plans.findByIncident_IdOrderByVersion(incidentId);
        // Phương án đang chờ duyệt bị thay bởi phương án mới
        existing.stream().filter(p -> p.getStatus() == IncidentPlanStatus.PROPOSED).forEach(p -> {
            p.setStatus(IncidentPlanStatus.SUPERSEDED);
            p.setDecidedBy(currentStaff(username));
            p.setDecidedAt(clock.instant());
        });
        IncidentPlan plan = request.toEntity(incident, handler);
        plan.setVersion(existing.stream().mapToInt(IncidentPlan::getVersion).max().orElse(0) + 1);
        plan.setStatus(IncidentPlanStatus.PROPOSED);
        plan.setProposedBy(currentStaff(username));
        plans.saveAndFlush(plan);
        if (incident.getStatus() == IncidentStatus.REPORTED) {
            incident.setStatus(IncidentStatus.PLAN_PENDING);
        }
        return incident(incidentId);
    }

    @Override
    @Transactional
    public IncidentResponseDto approve(Long planId, String username) {
        IncidentPlan plan = decidablePlan(planId);
        Incident incident = openIncident(plan.getIncident().getId());
        // Mỗi sự cố chỉ một phương án hiệu lực: phương án đã duyệt trước đó bị thay
        plans.findByIncident_IdOrderByVersion(incident.getId()).stream().filter(p -> p.getStatus() == IncidentPlanStatus.APPROVED)
                .forEach(p -> p.setStatus(IncidentPlanStatus.SUPERSEDED));
        plans.flush();
        plan.setStatus(IncidentPlanStatus.APPROVED);
        plan.setDecidedBy(currentStaff(username));
        plan.setDecidedAt(clock.instant());
        incident.setStatus(IncidentStatus.PLAN_ACTIVE);
        return incident(incident.getId());
    }

    @Override
    @Transactional
    public IncidentResponseDto reject(Long planId, PlanRejectRequestDto request, String username) {
        IncidentPolicy.requireRejectionReason(request.reason());
        IncidentPlan plan = decidablePlan(planId);
        plan.setStatus(IncidentPlanStatus.REJECTED);
        plan.setRejectionReason(request.reason().strip());
        plan.setDecidedBy(currentStaff(username));
        plan.setDecidedAt(clock.instant());
        return incident(plan.getIncident().getId());
    }

    @Override
    @Transactional
    public IncidentResponseDto confirmFit(Long incidentId, String username) {
        Incident incident = openIncident(incidentId);
        Staff member = crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(incident.getVehicleTrip().getId(), username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này.")).getStaff();
        IncidentPolicy.requireCanConfirmFit(incident.getStatus(), incident.getReportedAt(), clock.instant(), configs.getInt("MIN_REST_MINUTES"));
        incident.setFitConfirmedBy(member);
        incident.setFitConfirmedAt(clock.instant());
        return incident(incidentId);
    }

    @Override
    @Transactional
    public IncidentResponseDto resolve(Long incidentId, String username) {
        Incident incident = openIncident(incidentId);
        boolean concernsHorse = incident.getHorseLeg() != null || incident.getIncidentType() == IncidentType.HEALTH;
        IncidentPolicy.requireResolvable(incident.getStatus(), concernsHorse, incident.getFitConfirmedAt() != null);
        plans.findByIncident_IdOrderByVersion(incidentId).stream().filter(p -> p.getStatus() == IncidentPlanStatus.APPROVED)
                .forEach(p -> p.setStatus(IncidentPlanStatus.DONE));
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(clock.instant());
        return incident(incidentId);
    }

    @Override
    @Transactional
    public IncidentResponseDto close(Long incidentId, String username) {
        Incident incident = incidents.findForUpdate(incidentId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + incidentId));
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new DispatchRuleException("status", "Chỉ đóng sự cố đã xử lý xong.", true);
        }
        incident.setStatus(IncidentStatus.CLOSED);
        return incident(incidentId);
    }

    private Incident openIncident(Long id) {
        Incident incident = incidents.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + id));
        if (!IncidentPolicy.isOpen(incident.getStatus())) {
            throw new DispatchRuleException("status", "Sự cố đã xử lý xong hoặc đã đóng.", true);
        }
        return incident;
    }

    private IncidentPlan decidablePlan(Long planId) {
        IncidentPlan plan = plans.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phương án " + planId));
        int latest = plans.findByIncident_IdOrderByVersion(plan.getIncident().getId()).stream().mapToInt(IncidentPlan::getVersion).max().orElse(0);
        IncidentPolicy.requireDecidable(plan.getStatus(), plan.getVersion() == latest);
        return plan;
    }

    // Vai trò còn hiệu lực hôm nay của đối tác (quy tắc #26)
    private List<PartyRoleType> validRoles(Long partyId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
        return partyRoles.findById_PartyId(partyId).stream()
                .filter(r -> (r.getValidFrom() == null || !r.getValidFrom().isAfter(today)) && (r.getValidTo() == null || !r.getValidTo().isBefore(today)))
                .map(r -> r.getId().getRoleType()).toList();
    }

    private Staff currentStaff(String username) {
        return staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }

    private List<IncidentResponseDto> describe(List<Incident> found) {
        if (found.isEmpty()) return List.of();
        Map<Long, List<PlanDto>> byIncident = plans.findByIncident_IdInOrderByIncident_IdAscVersionAsc(found.stream().map(Incident::getId).toList()).stream()
                .collect(Collectors.groupingBy(p -> p.getIncident().getId(), Collectors.mapping(PlanDto::from, Collectors.toList())));
        return found.stream().map(i -> IncidentResponseDto.from(i, byIncident.getOrDefault(i.getId(), List.of()))).toList();
    }
}
