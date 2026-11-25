package com.example.racehorse_transport.service.impl;

import static com.example.racehorse_transport.service.IncidentEvidenceService.FIT_CHECK_PHOTO;

import com.example.racehorse_transport.dto.incident.PlanApproveRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentPlanRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentReportRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto.PlanDto;
import com.example.racehorse_transport.dto.incident.PlanRejectRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.policy.DeliveryEtaPolicy;
import com.example.racehorse_transport.policy.DeliveryFaultPolicy;
import com.example.racehorse_transport.entity.enums.IncidentType;
import java.time.Duration;
import java.time.Instant;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.IncidentPlanRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.service.IncidentCostDecisionService;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.IncidentService;
import com.example.racehorse_transport.service.IncidentReportingService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
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
    private static final String MIN_REST_MINUTES = "MIN_REST_MINUTES";

    private final IncidentRepository incidents;
    private final IncidentPlanRepository plans;
    private final IncidentReportingService reporting;
    private final CrewAssignmentRepository crew;
    private final StaffRepository staff;
    private final PartyRepository parties;
    private final IncidentCostDecisionService decisions;
    private final SystemConfigService configs;
    private final NotificationService notifications;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponseDto> open() {
        return describe(incidents.findForBoard(OPEN, IncidentType.HANDOVER_TIMEOUT,
                clock.instant().minus(DeliveryFaultPolicy.AMENDMENT_PERIOD)));
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
        return incident(reporting.report(request, username));
    }

    @Override
    @Transactional
    public IncidentResponseDto propose(Long incidentId, IncidentPlanRequestDto request, String username) {
        Incident incident = openIncident(incidentId);
        IncidentPolicy.requireResolutionAvailable(incident.getIncidentType(), request.resolutionType());
        Party handler = request.handlerPartyId() == null ? null : parties.findById(request.handlerPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đối tác " + request.handlerPartyId()));
        IncidentPolicy.requireHandler(request.resolutionType(), handler != null, handler == null ? List.of() : decisions.validRoles(handler.getId()));
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
        List<Long> managers = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
        if (!managers.isEmpty()) {
            notifications.notify(managers, incident.getBooking() == null ? null : incident.getBooking().getId(),
                    "Phương án sự cố chờ duyệt",
                    "Sự cố " + incident.getTitle() + " có phương án mới chờ duyệt.",
                    "/manager/incidents");
        }
        return incident(incidentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanDto> plansForCustomer(Long bookingId, String username) {
        decisions.requireCustomerOwnership(bookingId, username);
        return incidents.findByBooking_IdOrderByReportedAtDesc(bookingId).stream()
                .filter(incident -> IncidentPolicy.isOpen(incident.getStatus()))
                .flatMap(incident -> plans.findByIncident_IdOrderByVersion(incident.getId()).stream())
                .filter(plan -> plan.getStatus() == IncidentPlanStatus.PROPOSED && plan.getEstimatedCost() != null
                        && plan.getEstimatedCost().signum() > 0)
                .map(PlanDto::from).toList();
    }

    @Override
    @Transactional
    public void acceptPlan(Long planId, String username) {
        Long incidentId = plans.findIncidentId(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phương án."));
        var incident = incidents.findForUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phương án."));
        var booking = incident.getBooking();
        if (booking == null || !username.equals(booking.getCustomer().getUser().getUsername())) {
            throw new ResourceNotFoundException("Không tìm thấy phương án.");
        }
        var plan = decidablePlan(planId);
        if (plan.getEstimatedCost() == null || plan.getEstimatedCost().signum() <= 0) {
            throw new DispatchRuleException("estimatedCost", "Phương án chưa có chi phí dự kiến.", true);
        }
        if (plan.getCustomerAgreedAt() == null) {
            plan.setCustomerAgreedAt(clock.instant());
            plan.setCustomerAgreedBy(booking.getCustomer().getUser());
        }
    }

    @Override
    @Transactional
    public IncidentResponseDto approve(Long planId, PlanApproveRequestDto request, String username) {
        IncidentPlan plan = decidablePlan(planId);
        Incident incident = plan.getIncident();
        decisions.confirmCause(plan, request, currentStaff(username));
        // Mỗi sự cố chỉ một phương án hiệu lực: phương án đã duyệt trước đó bị thay
        plans.findByIncident_IdOrderByVersion(incident.getId()).stream().filter(p -> p.getStatus() == IncidentPlanStatus.APPROVED)
                .forEach(p -> p.setStatus(IncidentPlanStatus.SUPERSEDED));
        plans.flush();
        plan.setStatus(IncidentPlanStatus.APPROVED);
        plan.setDecidedBy(currentStaff(username));
        plan.setDecidedAt(clock.instant());
        incident.setStatus(IncidentStatus.PLAN_ACTIVE);
        notifyPlanDecision(plan, incident, true);
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
        notifyPlanDecision(plan, plan.getIncident(), false);
        return incident(plan.getIncident().getId());
    }

    @Override
    @Transactional
    public IncidentResponseDto startRest(Long incidentId, String username) {
        Incident incident = openIncident(incidentId);
        requireEscort(incident, currentStaff(username), "Chỉ hộ tống của lượt xe được bắt đầu cho ngựa nghỉ.");
        IncidentPolicy.requireFitConfirmation(incident.getIncidentType(), incident.getHorseLeg() != null);
        IncidentPolicy.requireCanStartRest(incident.getStatus(), incident.getRestStartedAt(), incident.getFitConfirmedAt() != null);
        incident.setRestStartedAt(clock.instant());
        return incident(incidentId);
    }

    @Override
    @Transactional
    public IncidentResponseDto confirmFit(Long incidentId, String username) {
        Incident incident = openIncident(incidentId);
        Staff member = currentStaff(username);
        boolean onBehalf = "COORDINATOR".equals(member.getUser().getRole().getCode());
        if (onBehalf) {
            if (!incidents.hasDocumentType(incidentId, "VET_REPORT")) {
                throw new DispatchRuleException("documentType", "Cần tải kết quả thú y trước khi xác nhận thay.", true);
            }
        } else {
            requireEscort(incident, member, "Chỉ hộ tống của lượt xe được xác nhận ngựa đủ sức đi tiếp.");
        }
        IncidentPolicy.requireFitConfirmation(incident.getIncidentType(), incident.getHorseLeg() != null);
        Instant now = clock.instant();
        // Điều phối xác nhận thay (có kết quả thú y) khi tổ lái không còn trên lượt: chưa bấm bắt đầu nghỉ thì đếm từ lúc báo
        Instant restFrom = incident.getRestStartedAt() != null || !onBehalf ? incident.getRestStartedAt() : incident.getReportedAt();
        IncidentPolicy.requireCanConfirmFit(incident.getStatus(), restFrom, now, configs.getInt(MIN_REST_MINUTES));
        if (!onBehalf && !incidents.hasDocumentType(incidentId, FIT_CHECK_PHOTO)) {
            throw new DispatchRuleException("documentType", "Cần ảnh con ngựa trước khi xác nhận đủ sức.", true);
        }
        incident.setFitConfirmedBy(member);
        incident.setFitConfirmedAt(now);
        return incident(incidentId);
    }

    @Override
    @Transactional
    public IncidentResponseDto resolve(Long incidentId, String username) {
        Incident incident = openIncident(incidentId);
        boolean concernsHorse = IncidentPolicy.requiresFit(incident.getIncidentType(), incident.getHorseLeg() != null);
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
        plans.findIncidentId(planId).ifPresent(this::openIncident);
        IncidentPlan plan = plans.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phương án " + planId));
        int latest = plans.findByIncident_IdOrderByVersion(plan.getIncident().getId()).stream().mapToInt(IncidentPlan::getVersion).max().orElse(0);
        IncidentPolicy.requireDecidable(plan.getStatus(), plan.getVersion() == latest);
        return plan;
    }

    private Staff currentStaff(String username) {
        return staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }

    private void requireEscort(Incident incident, Staff member, String message) {
        var trip = tripOf(incident);
        if (!"ESCORT".equals(member.getUser().getRole().getCode()) || trip == null
                || crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(
                        trip.getId(), member.getUser().getUsername(), CrewAssignmentStatus.RELEASED)
                        .filter(assignment -> assignment.getCrewRole() == CrewRole.ESCORT).isEmpty()) {
            throw new AccessDeniedException(message);
        }
    }

    private VehicleTrip tripOf(Incident incident) {
        return incident.getVehicleTrip() != null ? incident.getVehicleTrip()
                : incident.getHorseLeg() == null ? null : incident.getHorseLeg().getVehicleTrip();
    }

    private void notifyPlanDecision(IncidentPlan plan, Incident incident, boolean approved) {
        VehicleTrip trip = tripOf(incident);
        List<Long> recipients = crew.findByVehicleTrip_IdInOrderById(trip == null ? List.of() : List.of(trip.getId())).stream()
                .filter(a -> a.getStatus() != CrewAssignmentStatus.RELEASED)
                .map(a -> a.getStaff().getUserId())
                .distinct()
                .toList();
        recipients = recipients.isEmpty() ? List.of() : recipients;
        if (!recipients.isEmpty()) {
            notifications.notify(recipients, null,
                    approved ? "Phương án sự cố được duyệt" : "Phương án sự cố bị từ chối",
                    approved ? "Phương án cho sự cố " + incident.getTitle() + " đã được duyệt."
                            : "Phương án cho sự cố " + incident.getTitle() + " đã bị từ chối.",
                    trip == null ? "/coordinator/incidents" : "/my-trips/" + trip.getId());
        }
        List<Long> coordinators = staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE);
        if (!coordinators.isEmpty()) {
            notifications.notify(coordinators, incident.getBooking() == null ? null : incident.getBooking().getId(),
                    approved ? "Phương án sự cố được duyệt" : "Phương án sự cố bị từ chối",
                    (approved ? "Đã duyệt" : "Đã từ chối") + " phương án cho sự cố " + incident.getTitle() + ".",
                    "/coordinator/incidents");
        }
        if (approved && incident.getHorseLeg() != null) {
            Booking booking = incident.getHorseLeg().getBookingHorse().getBooking();
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(),
                    "Phương án sự cố được duyệt",
                    "Phương án xử lý sự cố của đơn " + booking.getCode() + " đã được duyệt.",
                    "/orders/" + booking.getId());
        }
    }

    private List<IncidentResponseDto> describe(List<Incident> found) {
        if (found.isEmpty()) return List.of();
        Map<Long, List<PlanDto>> byIncident = plans.findByIncident_IdInOrderByIncident_IdAscVersionAsc(found.stream().map(Incident::getId).toList()).stream()
                .collect(Collectors.groupingBy(p -> p.getIncident().getId(), Collectors.mapping(PlanDto::from, Collectors.toList())));
        Instant now = clock.instant();
        int minRestMinutes = configs.getInt(MIN_REST_MINUTES);
        return found.stream().map(i -> IncidentResponseDto.from(i,
                IncidentPolicy.requiresFit(i.getIncidentType(), i.getHorseLeg() != null),
                IncidentPolicy.fitEligibleAt(i.getRestStartedAt(), minRestMinutes),
                byIncident.getOrDefault(i.getId(), List.of()), deliveryFailure(i, now))).toList();
    }

    private IncidentResponseDto.DeliveryFailureDto deliveryFailure(Incident incident, Instant now) {
        if (incident.getIncidentType() != IncidentType.HANDOVER_TIMEOUT || incident.getHorseLeg() == null) return null;
        var stop = incident.getHorseLeg().getAlightTripStop();
        if (stop == null) return null;
        var told = DeliveryEtaPolicy.reference(stop.getToldArrivalAt(), stop.getPlannedArrivalAt());
        var actual = stop.getActualArrivalAt();
        Long delay = told == null || actual == null ? null : Duration.between(told, actual).toMinutes();
        var failedAt = incident.getHorseLeg().getActualEndAt();
        return new IncidentResponseDto.DeliveryFailureDto(told, actual, delay,
                DeliveryFaultPolicy.canAmend(incident.getIncidentType(), failedAt == null ? incident.getReportedAt() : failedAt, now));
    }
}
