package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.incident.CostItemResponseDto;
import com.example.racehorse_transport.dto.payment.PaymentResponseDto;
import com.example.racehorse_transport.dto.payment.SettlementResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.policy.DeliveryComplaintPolicy;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.policy.SettlementPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.service.PaymentService;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.SettlementService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {
    private static final Set<CostPayerType> CUSTOMER_SIDE = Set.of(CostPayerType.CUSTOMER, CostPayerType.CONSIGNEE);

    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final QuotationRepository quotations;
    private final PaymentRepository payments;
    private final CostItemRepository costs;
    private final IncidentRepository incidents;
    private final BookingStatusService statuses;
    private final PaymentService paymentService;
    private final SystemConfigService configs;
    private final Clock clock;

    @Override
    @Transactional
    public PaymentResponseDto createExtraCharge(Long bookingId, List<Long> costItemIds, String username) {
        Long paymentId = paymentService.createExtraChargeForUser(bookingId, costItemIds, username);
        return PaymentResponseDto.from(payments.getReferenceById(paymentId));
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementResponseDto settlement(Long bookingId) {
        var booking = bookings.findById(bookingId).orElseThrow(SettlementServiceImpl::notFound);
        return snapshot(booking).orElseThrow(SettlementServiceImpl::noQuotation).toResponse(booking);
    }

    @Override
    @Transactional
    public SettlementResponseDto complete(Long bookingId, String username) {
        var booking = bookings.findForUpdate(bookingId).orElseThrow(SettlementServiceImpl::notFound);
        var snapshot = snapshot(booking).orElseThrow(SettlementServiceImpl::noQuotation);
        if (booking.getStatus() != BookingStatus.DELIVERED || !snapshot.canClose()) {
            throw BookingRuleException.conflict("Chỉ đóng đơn đã giao, đã lập đủ khoản thu, không còn khoản hoàn chờ và không còn sự cố mở.");
        }
        if (!deliveryWindowAllowsClosure(booking, booking.getDeliveryConfirmedAt() != null)) {
            throw BookingRuleException.conflict("Chỉ đóng đơn khi khách đã xác nhận nhận ngựa hoặc đã hết hạn khiếu nại trên ứng dụng.");
        }
        statuses.transitionForUser(bookingId, BookingStatus.COMPLETED, "Quản lý hoàn tất quyết toán.", username);
        return snapshot.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> autoCloseCandidates() {
        return history.findBookingIdsWithStatusBefore(BookingStatus.DELIVERED, clock.instant().minus(deliveryWindow()));
    }

    @Override
    @Transactional
    public boolean autoClose(Long bookingId) {
        var booking = bookings.findForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getStatus() != BookingStatus.DELIVERED || !deliveryWindowAllowsClosure(booking, false)) {
            return false;
        }
        var snapshot = snapshot(booking);
        if (snapshot.isEmpty() || !snapshot.get().canClose()) {
            return false;
        }
        statuses.transition(bookingId, BookingStatus.COMPLETED, "Hệ thống hoàn tất đơn đã quyết toán sau hạn khiếu nại.", null);
        return true;
    }

    private boolean deliveryWindowAllowsClosure(Booking booking, boolean customerConfirmed) {
        return DeliveryComplaintPolicy.canClose(booking.getStatus(), deliveredAt(booking), clock.instant(),
                deliveryWindow(), customerConfirmed, false);
    }

    private Instant deliveredAt(Booking booking) {
        return history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(booking.getId(), BookingStatus.DELIVERED)
                .map(BookingStatusHistory::getChangedAt)
                .orElse(null);
    }

    private Duration deliveryWindow() {
        return Duration.ofDays(configs.getInt("SETTLEMENT_AUTO_CLOSE_DAYS"));
    }

    private Optional<Snapshot> snapshot(Booking booking) {
        Long bookingId = booking.getId();
        return quotations.findByBooking_IdAndStatus(bookingId, QuotationStatus.ACCEPTED).map(quotation -> {
            var costItems = costs.findByBooking_IdOrderByIncurredAt(bookingId);
            if (costItems.stream().filter(cost -> CostPayerPolicy.customerSide(cost.getPayerType()))
                    .anyMatch(cost -> !quotation.getCurrencyCode().equals(cost.getCurrencyCode()))) {
                throw BookingRuleException.conflict("Chi phí phát sinh dùng tiền tệ khác báo giá.");
            }
            var entries = payments.findByBooking_IdOrderById(bookingId);
            var approvedCosts = costs.sumApprovedForBooking(bookingId, CUSTOMER_SIDE);
            var outstanding = PaymentPolicy.outstanding(quotation, entries, approvedCosts);
            var openIncident = incidents.hasIncidentForBookingOtherThan(bookingId, IncidentStatus.CLOSED);
            return new Snapshot(quotation, costItems, entries, approvedCosts, outstanding, openIncident);
        });
    }

    private record Snapshot(Quotation quotation, List<CostItem> costs, List<Payment> payments, BigDecimal approvedCosts,
                            BigDecimal outstanding, boolean openIncident) {
        boolean canClose() {
            return SettlementPolicy.canClose(PaymentPolicy.outstanding(quotation, payments), openIncident, costs,
                    payments.stream().anyMatch(payment -> payment.getPaymentType() == PaymentType.REFUND
                            && payment.getStatus() == PaymentStatus.PENDING));
        }

        SettlementResponseDto toResponse(Booking booking) {
            var chargeable = costs.stream().filter(cost -> PaymentPolicy.canSelectExtraChargeCost(booking.getStatus(), cost))
                    .map(CostItem::getId).toList();
            return SettlementResponseDto.from(booking, quotation.getCurrencyCode(), quotation.getTotalAmount(),
                    approvedCosts, outstanding, openIncident, canClose(), chargeable, costs.stream().map(CostItemResponseDto::from).toList(),
                    payments.stream().map(PaymentResponseDto::from).toList());
        }
    }

    private static BookingRuleException noQuotation() {
        return BookingRuleException.conflict("Đơn chưa có báo giá đã chấp nhận.");
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn.");
    }
}
