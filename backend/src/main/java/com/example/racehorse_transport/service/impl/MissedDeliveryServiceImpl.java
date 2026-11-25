package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.policy.SettlementPolicy;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.IncidentPlanRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.MissedDeliveryService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MissedDeliveryServiceImpl implements MissedDeliveryService {

    private final IncidentRepository incidents;
    private final IncidentPlanRepository plans;
    private final CostItemRepository costs;
    private final AuditService audit;

    @Override
    @Transactional(readOnly = true)
    public boolean isRecipientAbsent(Long horseLegId) {
        return incidents.existsByHorseLeg_IdAndIncidentTypeAndConfirmedCauseType(horseLegId, IncidentType.HANDOVER_TIMEOUT,
                IncidentCauseType.RECIPIENT_ABSENT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolve(Long bookingHorseId, Instant at, Long actorUserId) {
        incidents.findByHorseLeg_BookingHorse_IdAndIncidentTypeAndStatusNotOrderById(bookingHorseId, IncidentType.HANDOVER_TIMEOUT,
                        IncidentStatus.CLOSED).stream()
                .flatMap(row -> incidents.findForUpdate(row.getId()).stream())
                .forEach(incident -> close(incident, at, actorUserId));
    }

    // Còn chi phí chưa duyệt thì chỉ chuyển Đã xử lý, duyệt xong mới đóng
    private void close(Incident incident, Instant at, Long actorUserId) {
        if (IncidentPolicy.isOpen(incident.getStatus())) {
            plans.findByIncident_IdOrderByVersion(incident.getId()).forEach(plan -> {
                if (plan.getStatus() == IncidentPlanStatus.APPROVED) {
                    plan.setStatus(IncidentPolicy.requiresExecution(plan.getResolutionType())
                            ? IncidentPlanStatus.SUPERSEDED : IncidentPlanStatus.DONE);
                } else if (plan.getStatus() == IncidentPlanStatus.PROPOSED) {
                    plan.setStatus(IncidentPlanStatus.SUPERSEDED);
                }
            });
            incident.setResolvedAt(at);
            incident.setStatus(IncidentStatus.RESOLVED);
        }
        boolean costsSettled = costs.findByIncident_IdOrderByIncurredAt(incident.getId()).stream()
                .allMatch(cost -> cost.getApprovedAt() != null || SettlementPolicy.isSettled(cost));
        if (costsSettled) {
            incident.setStatus(IncidentStatus.CLOSED);
        }
        audit.record(actorUserId, "MISSED_DELIVERY_RESOLVED", "incident", incident.getId(), null, null);
    }
}
