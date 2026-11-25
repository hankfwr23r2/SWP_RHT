package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import com.example.racehorse_transport.dto.incident.PlanApproveRequestDto;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.policy.SettlementPolicy;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.service.IncidentCostDecisionService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentCostDecisionServiceImpl implements IncidentCostDecisionService {
    private final CostItemRepository costs;
    private final PartyRoleRepository partyRoles;
    private final Clock clock;
    private final NotificationService notifications;
    private final SystemConfigService config;
    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;

    @Override
    @Transactional(readOnly = true)
    public void requireCustomerOwnership(Long bookingId, String username) {
        bookings.findByIdAndCustomer_User_Username(bookingId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmCause(IncidentPlan plan, PlanApproveRequestDto request, Staff manager) {
        var incident = plan.getIncident();
        boolean systemCause = incident.getIncidentType() == IncidentType.HANDOVER_TIMEOUT;
        var cause = systemCause && incident.getConfirmedCauseType() != null
                ? incident.getConfirmedCauseType() : incident.getCauseType();
        if (!systemCause && request != null && request.confirmedCauseType() != null) {
            cause = request.confirmedCauseType();
        }
        var payer = CostPayerPolicy.payerFor(cause);
        if (CostPayerPolicy.customerSide(payer) && plan.getEstimatedCost() != null && plan.getEstimatedCost().signum() > 0) {
            var booking = incident.getBooking();
            CostPayerPolicy.requireBookingForCustomerSide(payer, booking != null);
            bookings.findForUpdate(booking.getId());
            var spent = costs.sumApprovedWithoutConsulting(booking.getId(), Set.of(
                    CostPayerType.CUSTOMER,
                    CostPayerType.CONSIGNEE));
            CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(payer, plan.getCustomerAgreedAt() != null,
                    plan.getEstimatedCost(), spent, booking.getEmergencySpendingLimit());
        }
        if (systemCause) {
            return;
        }
        incident.setConfirmedCauseType(cause);
        incident.setCauseConfirmedBy(manager);
        incident.setCauseConfirmedAt(clock.instant());
        for (var cost : costs.findByIncident_IdOrderByIncurredAt(incident.getId())) {
            if (cost.getApprovedAt() == null) {
                assignPayer(cost, cause);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assignPayer(CostItem cost, IncidentCauseType cause) {
        var payer = cause == null ? CostPayerType.COMPANY : CostPayerPolicy.payerFor(cause);
        // Hóa đơn tới quá hạn sau khi giao thì công ty chịu (mục 13.5 điểm 2)
        if (CostPayerPolicy.customerSide(payer) && cost.getBooking() != null && SettlementPolicy.elapsed(
                history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(cost.getBooking().getId(), BookingStatus.DELIVERED)
                        .map(BookingStatusHistory::getChangedAt).orElse(null), clock.instant(), config.getInt("LATE_INVOICE_DAYS"))) {
            payer = CostPayerType.COMPANY;
        }
        CostPayerPolicy.requireBookingForCustomerSide(payer, cost.getBooking() != null);
        cost.setPayerType(payer);
        cost.setAgentRecoveryRequired(cause == IncidentCauseType.AGENT_FAULT);
        if (!CostPayerPolicy.countsAgainstEmergencyLimit(cost) || cost.getCustomerResponseDueAt() != null) {
            return;
        }
        var booking = cost.getBooking();
        cost.setCustomerResponseDueAt(clock.instant().plus(config.getInt("CUSTOMER_COST_RESPONSE_MINUTES"),
                ChronoUnit.MINUTES));
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(),
                "Chi phí cần phản hồi", "Vui lòng xem chi phí phát sinh trong chi tiết đơn.", "/orders/" + booking.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyRoleType> validRoles(Long partyId) {
        var today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        return partyRoles.findById_PartyId(partyId).stream()
                .filter(role -> role.getValidFrom() == null || !role.getValidFrom().isAfter(today))
                .filter(role -> role.getValidTo() == null || !role.getValidTo().isBefore(today))
                .map(role -> role.getId().getRoleType()).toList();
    }
}
