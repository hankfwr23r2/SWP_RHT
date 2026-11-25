package com.example.racehorse_transport.service.impl;

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
        Incident incident = request.incidentId() == null ? null : incidents.findById(request.incidentId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + request.incidentId()));
        VehicleTrip trip = request.vehicleTripId() != null
                ? trips.findById(request.vehicleTripId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + request.vehicleTripId()))
                : incident == null ? null : incident.getVehicleTrip();
        Booking booking = request.bookingId() != null
                ? bookings.findById(request.bookingId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + request.bookingId()))
                : incident == null ? null : incident.getBooking();
        if (incident == null && trip == null && booking == null) {
            throw new DispatchRuleException("incidentId", "Chi phí phải gắn với một sự cố, một lượt xe hoặc một đơn.");
        }
        CostPayerType payer = incident == null ? CostPayerType.COMPANY : CostPayerPolicy.payerFor(incident.getCauseType());
        CostPayerPolicy.requireBookingForCustomerSide(payer, booking != null);
        CostItem cost = request.toEntity(incident, trip, booking);
        cost.setPayerType(payer);
        cost.setRecordedBy(staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")));
        return CostItemResponseDto.from(costs.saveAndFlush(cost));
    }

    @Override
    @Transactional
    public CostItemResponseDto approve(Long costItemId, String username) {
        CostItem cost = costs.findById(costItemId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi phí " + costItemId));
        if (cost.getApprovedAt() != null) {
            throw new DispatchRuleException("status", "Chi phí đã được duyệt.", true);
        }
        Booking booking = cost.getBooking();
        if (CostPayerPolicy.countsAgainstEmergencyLimit(cost)) {
            CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(cost.getPayerType(), false, cost.getAmount(),
                    booking == null ? java.math.BigDecimal.ZERO : costs.sumApprovedWithoutConsulting(booking.getId(), CUSTOMER_SIDE),
                    booking == null ? null : booking.getEmergencySpendingLimit());
        }
        cost.setApprovedBy(staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")));
        cost.setApprovedAt(clock.instant());
        return CostItemResponseDto.from(costs.saveAndFlush(cost));
    }

    @Override
    @Transactional
    public void linkExtraPayment(List<Long> costItemIds, Long paymentId) {
        var payment = payments.findById(paymentId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản thu " + paymentId));
        List<CostItem> found = costs.findAllById(costItemIds);
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
