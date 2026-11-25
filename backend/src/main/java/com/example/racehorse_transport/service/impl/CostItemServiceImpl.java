package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.dto.incident.CostItemRequestDto;
import com.example.racehorse_transport.dto.incident.CostItemResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.CostItemService;
import com.example.racehorse_transport.dto.incident.CostApproveRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.service.IncidentCostDecisionService;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CostItemServiceImpl implements CostItemService {

    private static final Set<CostPayerType> CUSTOMER_SIDE = Set.of(CostPayerType.CUSTOMER, CostPayerType.CONSIGNEE);

    private final CostItemRepository costs;
    private final IncidentRepository incidents;
    private final VehicleTripRepository trips;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final StaffRepository staff;
    private final Clock clock;
    private final CoordinatorAssignmentService coordinators;
    private final IncidentCostDecisionService decisions;

    @Override
    @Transactional(readOnly = true)
    public List<CostItemResponseDto> ofIncident(Long incidentId) {
        return costs.findByIncident_IdOrderByIncurredAt(incidentId).stream().map(CostItemResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CostItemResponseDto> ofBooking(Long bookingId) {
        return costs.findByBooking_IdOrderByIncurredAt(bookingId).stream().map(CostItemResponseDto::from).toList();
    }

    @Override
    @Transactional
    public CostItemResponseDto record(CostItemRequestDto request, String username) {
        if (request.incidentId() != null) {
            coordinators.requireIncidentWrite(request.incidentId(), username);
        }
        if (request.vehicleTripId() != null) {
            coordinators.requireTripWrite(request.vehicleTripId(), username);
        }
        CostPayerPolicy.requireRecordableCategory(request.category());
        Incident incident = request.incidentId() == null ? null : incidents.findForUpdate(request.incidentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + request.incidentId()));
        VehicleTrip trip = request.vehicleTripId() != null
                ? trips.findById(request.vehicleTripId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + request.vehicleTripId()))
                : incident == null ? null : incident.getVehicleTrip();
        Booking booking = request.bookingId() != null
                ? bookings.findById(request.bookingId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + request.bookingId()))
                : incident == null ? null : incident.getBooking();
        if (incident != null && ((incident.getBooking() != null && booking != null
                && !incident.getBooking().getId().equals(booking.getId()))
                || (incident.getVehicleTrip() != null && trip != null && !incident.getVehicleTrip().getId().equals(trip.getId())))) {
            throw new DispatchRuleException("incidentId", "Đơn hoặc lượt xe không khớp với sự cố.");
        }
        if (incident == null && trip == null && booking == null) {
            throw new DispatchRuleException("incidentId", "Chi phí phải gắn với một sự cố, một lượt xe hoặc một đơn.");
        }
        CostItem cost = request.toEntity(incident, trip, booking);
        decisions.assignPayer(cost, incident == null ? null : CostPayerPolicy.effectiveCause(incident));
        cost.setRecordedBy(staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")));
        return CostItemResponseDto.from(costs.saveAndFlush(cost));
    }

    @Override
    @Transactional
    public CostItemResponseDto approve(Long costItemId, CostApproveRequestDto request, String username) {
        costs.findIncidentId(costItemId).ifPresent(incidents::findForUpdate);
        costs.findBookingId(costItemId).ifPresent(bookings::findForUpdate);
        CostItem cost = costs.findForUpdate(costItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi phí."));
        if (cost.getApprovedAt() != null) {
            throw new DispatchRuleException("status", "Chi phí đã được duyệt.", true);
        }
        Booking booking = cost.getBooking();
        if (CostPayerPolicy.customerSide(cost.getPayerType()) && cost.getIncident() != null
                && !CostPayerPolicy.causeConfirmed(cost.getIncident())) {
            throw new DispatchRuleException("causeType", "Quản lý cần chốt nguyên nhân trước khi duyệt chi phí.", true);
        }
        if (CostPayerPolicy.countsAgainstEmergencyLimit(cost)) {
            String reason = request == null ? null : request.emergencyOverrideReason();
            CostPayerPolicy.requireCostResponse(cost, clock.instant(), reason);
            if (Boolean.FALSE.equals(cost.getCustomerAgreed())) {
                cost.setEmergencyOverrideReason(reason.strip());
            }
            CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(cost.getPayerType(), false, cost.getAmount(),
                    booking == null ? java.math.BigDecimal.ZERO : costs.sumApprovedWithoutConsulting(booking.getId(), CUSTOMER_SIDE),
                    booking == null ? null : booking.getEmergencySpendingLimit());
        }
        cost.setApprovedBy(staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")));
        cost.setApprovedAt(clock.instant());
        return CostItemResponseDto.from(costs.saveAndFlush(cost));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CostItemResponseDto> customerCosts(Long bookingId, String username) {
        bookings.findByIdAndCustomer_User_Username(bookingId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        return costs.findByBooking_IdOrderByIncurredAt(bookingId).stream()
                .filter(cost -> CostPayerPolicy.customerSide(cost.getPayerType()))
                .map(CostItemResponseDto::from).toList();
    }

    @Override
    @Transactional
    public CostItemResponseDto respond(Long costItemId, boolean agreed, String username) {
        var cost = lockCost(costItemId);
        var customer = cost.getBooking() == null ? null : cost.getBooking().getCustomer().getUser();
        if (customer == null || !username.equals(customer.getUsername())) {
            throw new ResourceNotFoundException("Không tìm thấy chi phí.");
        }
        requirePendingResponse(cost);
        saveResponse(cost, agreed, customer);
        return CostItemResponseDto.from(cost);
    }

    @Override
    @Transactional
    public CostItemResponseDto recordPhoneConsent(Long costItemId, String note, String username) {
        coordinators.requireCostWrite(costItemId, username);
        var cost = lockCost(costItemId);
        requirePendingResponse(cost);
        var coordinator = staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy nhân viên."));
        cost.setPhoneConsentNote(note.strip());
        saveResponse(cost, true, coordinator.getUser());
        return CostItemResponseDto.from(cost);
    }

    @Override
    @Transactional
    public CostItemResponseDto markRecovered(Long costItemId) {
        var cost = lockCost(costItemId);
        if (!cost.isAgentRecoveryRequired() || cost.getApprovedAt() == null) {
            throw new DispatchRuleException("agentRecovery", "Chi phí chưa được duyệt để đòi lại đại lý.", true);
        }
        if (cost.getAgentRecoveredAt() == null) {
            cost.setAgentRecoveredAt(clock.instant());
        }
        return CostItemResponseDto.from(cost);
    }

    private CostItem lockCost(Long id) {
        costs.findIncidentId(id).ifPresent(incidents::findForUpdate);
        return costs.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi phí."));
    }

    private static void requirePendingResponse(CostItem cost) {
        if (!CostPayerPolicy.customerSide(cost.getPayerType()) || cost.getCustomerResponseDueAt() == null
                || cost.getApprovedAt() != null || cost.getCustomerResponseAt() != null) {
            throw new DispatchRuleException("customerResponse", "Chi phí không chờ khách phản hồi.", true);
        }
    }

    private void saveResponse(CostItem cost, boolean agreed, AppUser actor) {
        cost.setCustomerAgreed(agreed);
        cost.setCustomerResponseBy(actor);
        cost.setCustomerResponseAt(clock.instant());
        cost.setCustomerConsulted(agreed);
    }

    @Override
    @Transactional
    public void linkExtraPayment(List<Long> costItemIds, Long paymentId) {
        var payment = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản thu " + paymentId));
        List<CostItem> found = costItemIds.stream().distinct().sorted()
                .flatMap(id -> costs.findForUpdate(id).stream()).toList();
        if (found.size() != Set.copyOf(costItemIds).size()) {
            throw new ResourceNotFoundException("Không tìm thấy đủ các chi phí cần gắn.");
        }
        for (CostItem cost : found) {
            if (cost.getApprovedAt() == null || cost.getPayerType() == CostPayerType.COMPANY || cost.getExtraChargePayment() != null) {
                throw new DispatchRuleException("costItemIds", "Chỉ gắn khoản thu thêm cho chi phí đã duyệt, do khách chịu và chưa gắn khoản nào.", true);
            }
            cost.setExtraChargePayment(payment);
        }
    }
}
