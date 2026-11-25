package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.IncidentPlanRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.service.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MissedDeliveryServiceImplTest {
    private final Instant now = Instant.parse("2026-12-12T02:00:00Z");
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final IncidentPlanRepository plans = mock(IncidentPlanRepository.class);
    private final CostItemRepository costs = mock(CostItemRepository.class);
    private final Incident incident = new Incident();
    private final IncidentPlan plan = new IncidentPlan();
    private final MissedDeliveryServiceImpl service = new MissedDeliveryServiceImpl(incidents, plans, costs, mock(AuditService.class));

    @BeforeEach
    void setUp() {
        incident.setId(1L);
        incident.setStatus(IncidentStatus.REPORTED);
        plan.setStatus(IncidentPlanStatus.PROPOSED);
        when(incidents.findByHorseLeg_BookingHorse_IdAndIncidentTypeAndStatusNotOrderById(6L, IncidentType.HANDOVER_TIMEOUT,
                IncidentStatus.CLOSED)).thenReturn(List.of(incident));
        when(incidents.findForUpdate(1L)).thenReturn(Optional.of(incident));
        when(plans.findByIncident_IdOrderByVersion(1L)).thenReturn(List.of(plan));
    }

    @Test
    void closesWithoutAPlanOnceTheHorseIsDelivered() {
        service.resolve(6L, now, 9L);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(incident.getResolvedAt()).isEqualTo(now);
        assertThat(plan.getStatus()).isEqualTo(IncidentPlanStatus.SUPERSEDED);
    }

    @Test
    void staysResolvedWhileACostIsNotApproved() {
        var cost = new CostItem();
        cost.setPayerType(CostPayerType.CONSIGNEE);
        when(costs.findByIncident_IdOrderByIncurredAt(1L)).thenReturn(List.of(cost));

        service.resolve(6L, now, 9L);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isEqualTo(now);
    }
}
