package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.dto.incident.HandoverCauseRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DeliveryFaultPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.DeliveryFaultService;
import com.example.racehorse_transport.service.IncidentService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryFaultServiceImpl implements DeliveryFaultService {
    private final IncidentRepository incidents;
    private final BookingRepository bookings;
    private final HorseStayRepository stays;
    private final CostItemRepository costs;
    private final SystemConfigService configs;
    private final AuditService audit;
    private final NotificationService notifications;
    private final IncidentService incidentReads;
    private final Clock clock;
    private final CoordinatorAssignmentService coordinators;

    @Override
    @Transactional
    public IncidentResponseDto amendCause(Long id, HandoverCauseRequestDto request, String username) {
        coordinators.requireIncidentWrite(id, username);
        var actor = coordinators.currentStaff(username);
        var cause = request.cause().incidentCause();
        if (!DeliveryFaultPolicy.canSelect(actor.getUser().getRole().getCode(), cause)) {
            throw new AccessDeniedException("Bạn không có quyền chọn kết luận này.");
        }
        var incident = incidents.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + id));
        var now = clock.instant();
        var leg = incident.getHorseLeg();
        var failedAt = leg != null && leg.getActualEndAt() != null ? leg.getActualEndAt() : incident.getReportedAt();
        DeliveryFaultPolicy.requireAmendable(incident.getIncidentType(), failedAt, now);
        var previous = incident.getConfirmedCauseType() == null ? incident.getCauseType() : incident.getConfirmedCauseType();
        if (cause == previous) return incidentReads.incident(id);

        var booking = incident.getBooking();
        bookings.findForUpdate(booking.getId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (leg != null) updateStay(booking.getId(), leg, cause);
        incident.setConfirmedCauseType(cause);
        incident.setCauseConfirmedAt(now);
        incident.setCauseConfirmedBy(actor);
        audit.record(actor.getUserId(), "AMEND_HANDOVER_CAUSE", "incident", id, Map.of("cause", previous),
                Map.of("cause", cause, "reason", request.reason().strip()));
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), "Cập nhật kết luận giao hụt",
                incident.getTitle() + ". " + customerCause(cause) + ". " + request.reason().strip(), "/orders/" + booking.getId());
        return incidentReads.incident(id);
    }

    private void updateStay(Long bookingId, HorseLeg leg, IncidentCauseType cause) {
        var returnStays = stays.findReturnStaysForUpdate(leg.getBookingHorse().getId(), leg.getId()).stream()
                .filter(stay -> stay.getLocation().getLocationType() == LocationType.HUB)
                .toList();
        if (returnStays.stream().anyMatch(stay -> costs.existsByHorseStay_IdAndCategory(stay.getId(), CostCategory.STABLING))) {
            throw new DispatchRuleException("cause", "Lưu trú này đã phát sinh phí giữ ngựa.", true);
        }
        if (costs.findByBooking_IdOrderByIncurredAt(bookingId).stream().anyMatch(cost ->
                cost.getCategory() == CostCategory.REDELIVERY && !cost.getIncurredAt().isBefore(leg.getActualEndAt()))) {
            throw new DispatchRuleException("cause", "Đã lập cước giao lại cho lần giao hụt này.", true);
        }
        returnStays.stream().filter(stay -> stay.getActualEndAt() == null)
                .filter(stay -> stay.getStayType() == HorseStayType.ASSEMBLY || stay.getStayType() == HorseStayType.STABLING)
                .findFirst().ifPresent(stay -> convert(stay, leg, cause));
    }

    private void convert(HorseStay stay, HorseLeg leg, IncidentCauseType cause) {
        boolean billable = cause == IncidentCauseType.RECIPIENT_ABSENT;
        var target = billable ? HorseStayType.STABLING : HorseStayType.ASSEMBLY;
        if (stay.getStayType() == target) return;
        stay.setStayType(target);
        stay.setFailedDeliveryLeg(billable ? leg : null);
        stay.setStablingUnitPrice(billable ? BigDecimal.valueOf(configs.getInt("STABLING_DAILY_FEE")) : null);
        if (billable) {
            stay.setPlannedEndAt(stay.getActualStartAt().plus(Duration.ofDays(configs.getInt("STABLING_MAX_DAYS"))));
        }
    }

    private String customerCause(IncidentCauseType cause) {
        return switch (cause) {
            case RECIPIENT_ABSENT -> "Người nhận vắng mặt";
            case COMPANY_FAULT -> "Lỗi công ty";
            case FORCE_MAJEURE -> "Bất khả kháng";
            default -> throw new DispatchRuleException("cause", "Kết luận giao hụt không hợp lệ.");
        };
    }
}
