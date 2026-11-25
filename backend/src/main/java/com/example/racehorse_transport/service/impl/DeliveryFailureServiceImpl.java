package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.DeliveryFailureService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.repository.TripStopRepository;

@Service
@RequiredArgsConstructor
public class DeliveryFailureServiceImpl implements DeliveryFailureService {

    private static final String FREE_WAIT = "HANDOVER_FREE_WAIT_MINUTES";

    private final HandoverRepository handovers;
    private final HorseLegRepository legs;
    private final CrewAssignmentRepository crew;
    private final IncidentRepository incidents;
    private final StaffRepository staff;
    private final NotificationService notifications;
    private final SystemConfigService configs;
    private final Clock clock;
    private final TripStopRepository stops;

    @Override
    @Transactional
    public Long startWaiting(Long handoverId, String username) {
        Handover handover = lockOnCrew(handoverId, username);
        TripExecutionPolicy.requireCanStartWaiting(handover.getHandoverType(), handover.getStatus() == HandoverStatus.PENDING, handover.getWaitStartedAt() != null);
        handover.setWaitStartedAt(clock.instant());
        return handover.getTripStop().getVehicleTrip().getId();
    }

    @Override
    @Transactional
    public Long fail(Long handoverId, String username) {
        Handover handover = lockOnCrew(handoverId, username);
        if (handover.getStatus() != HandoverStatus.PENDING) {
            throw new DispatchRuleException("handoverId", "Lần giao nhận đã kết thúc.", true);
        }
        TripExecutionPolicy.requireFreeWaitOver(handover.getWaitStartedAt(), clock.instant(), waitMinutes(handover));
        markFailed(handover);
        return handover.getTripStop().getVehicleTrip().getId();
    }

    @Override
    @Transactional
    public int timeoutExpired() {
        Instant now = clock.instant();
        int shortestWait = Math.min(configs.getInt(FREE_WAIT), configs.getInt("INTERMEDIATE_WAIT_MINUTES"));
        List<Handover> expired = handovers.findWaitingSince(HandoverStatus.PENDING, now.minusSeconds(60L * shortestWait))
                .stream().filter(handover -> !now.isBefore(handover.getWaitStartedAt().plusSeconds(60L * waitMinutes(handover))))
                .toList();
        expired.forEach(this::markFailed);
        return expired.size();
    }

    private int waitMinutes(Handover handover) {
        var current = handover.getTripStop();
        boolean intermediate = stops.findByVehicleTrip_IdOrderBySequenceNumber(current.getVehicleTrip().getId()).stream()
                .anyMatch(stop -> stop.getSequenceNumber() > current.getSequenceNumber()
                        && stop.getStopType() == TripStopType.DELIVERY);
        return configs.getInt(intermediate ? "INTERMEDIATE_WAIT_MINUTES" : FREE_WAIT);
    }

    // Con chưa giao được: chặng FAILED; mỗi con một sự cố HANDOVER_TIMEOUT (người nhận vắng nên chi phí do người nhận chịu); báo điều phối
    private void markFailed(Handover handover) {
        Instant now = clock.instant();
        List<HorseLeg> undelivered = legs.findByAlightTripStop_IdAndStatusIn(handover.getTripStop().getId(), Set.of(HorseLegStatus.IN_PROGRESS));
        for (HorseLeg leg : undelivered) {
            leg.setStatus(HorseLegStatus.FAILED);
            leg.setActualEndAt(now);
            Incident incident = new Incident();
            incident.setVehicleTrip(handover.getTripStop().getVehicleTrip());
            incident.setHorseLeg(leg);
            incident.setBooking(leg.getBookingHorse().getBooking());
            incident.setIncidentType(IncidentType.HANDOVER_TIMEOUT);
            incident.setCauseType(IncidentCauseType.RECIPIENT_ABSENT);
            incident.setConfirmedCauseType(IncidentCauseType.RECIPIENT_ABSENT);
            incident.setCauseConfirmedAt(now);
            incident.setTitle("Không gặp người nhận: " + leg.getBookingHorse().getHorse().getName());
            incident.setDescription("Đã chờ hết thời gian chờ miễn phí tại điểm giao, không gặp người nhận.");
            incident.setReportedBy(handover.getHandledBy());
            incident.setStatus(IncidentStatus.REPORTED);
            incidents.save(incident);
        }
        handover.setStatus(HandoverStatus.TIMEOUT);
        handover.setNote("Quá giờ chờ người nhận");
        notifyCoordinators(handover, undelivered);
    }

    private void notifyCoordinators(Handover handover, List<HorseLeg> undelivered) {
        if (undelivered.isEmpty()) return;
        List<Long> coordinators = staff.findAllByOrderByStaffCodeAsc().stream()
                .filter(s -> "COORDINATOR".equals(s.getUser().getRole().getCode()) && s.getEmploymentStatus() == EmploymentStatus.ACTIVE
                        && s.getUser().getStatus() == AppUserStatus.ACTIVE)
                .map(Staff::getUserId).toList();
        if (coordinators.isEmpty()) return;
        var booking = undelivered.getFirst().getBookingHorse().getBooking();
        notifications.notify(coordinators, booking.getId(), "Giao ngựa không thành",
                "Lượt xe " + handover.getTripStop().getVehicleTrip().getCode() + " không gặp người nhận của đơn " + booking.getCode()
                        + ". Chọn gửi chuồng hoặc đưa về hub để giao lại.", null);
    }

    private Handover lockOnCrew(Long handoverId, String username) {
        Handover handover = handovers.findForUpdate(handoverId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần giao nhận " + handoverId));
        var trip = handover.getTripStop().getVehicleTrip();
        crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(trip.getId(), username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này."));
        if (trip.getStatus() != VehicleTripStatus.IN_PROGRESS) {
            throw new DispatchRuleException("status", "Lượt xe chưa xuất phát hoặc đã kết thúc.", true);
        }
        return handover;
    }
}
