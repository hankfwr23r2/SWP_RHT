package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.policy.StablingPolicy;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.StablingTimelineService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StablingTimelineServiceImpl implements StablingTimelineService {
    private final HorseStayRepository stays;
    private final CostItemRepository costs;
    private final VehicleTripRepository trips;
    private final SystemConfigService config;
    private final com.example.racehorse_transport.repository.BookingStatusHistoryRepository history;
    private final com.example.racehorse_transport.repository.PaymentRepository payments;
    private final com.example.racehorse_transport.repository.QuotationRepository quotations;
    private final com.example.racehorse_transport.repository.PriceItemRepository priceItems;
    private final com.example.racehorse_transport.service.PaymentReadinessService readiness;
    private final com.example.racehorse_transport.service.ExtraChargeService extras;

    @Override
    @Transactional(readOnly = true)
    public List<StablingPolicy.Run> runs(HorseStay stay, Instant through) {
        return stays.findByBookingHorse_IdInOrderById(List.of(stay.getBookingHorse().getId())).stream()
                .filter(row -> row.getStayType() == HorseStayType.STABLING && row.getActualStartAt() != null)
                .flatMap(row -> run(row, through).stream()).toList();
    }

    private List<StablingPolicy.Run> run(HorseStay stay, Instant through) {
        var deadline = stay.getCollectionExpectedAt() == null ? null : stay.getCollectionExpectedAt()
                .plus(config.getInt("STABLING_COLLECTION_GRACE_HOURS"), ChronoUnit.HOURS);
        var planned = plannedSince(stay);
        var end = stay.getActualEndAt();
        if (planned != null && (end == null || planned.isBefore(end))) end = planned;
        var settlement = readinessSettlement(stay);
        var paid = settlement.paidStop();
        var chosen = stay.getChosenRedeliveryAt();
        if (paid != null && chosen != null) {
            var chargedUntil = paid.isAfter(chosen) ? paid : chosen;
            if (end == null || chargedUntil.isBefore(end)) end = chargedUntil;
        }
        var runs = StablingPolicy.clockRuns(start(stay), end, paid,
                stay.getEarliestRedeliveryAt(), stay.getCollectionReportedAt(), deadline, through);
        return StablingPolicy.excludePause(runs, settlement.companyWait());
    }

    private Instant start(HorseStay stay) {
        if (stay.getBalanceHoldAt() == null) return stay.getPlannedStartAt();
        if (stay.getMissedDeparture() == null) return stay.getBalanceHoldAt();
        return trips.firstActualStartForDeparture(stay.getMissedDeparture().getId())
                .map(actual -> actual.isAfter(stay.getBalanceHoldAt()) ? actual : stay.getBalanceHoldAt()).orElse(null);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void chargePlannedStay(HorseStay stay, Instant through) {
        if (stay.getBalanceHoldAt() != null && !stays.hasLinehaulBoarding(stay.getBookingHorse().getId(), through)) {
            settlePlannedStay(stay, through);
        }
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void chargePlannedStaysOnDeparture(Long tripStopId, Instant departedAt) {
        for (var stay : stays.findPlannedStablingForDeparture(tripStopId)) {
            readiness.lockBooking(stay.getBookingHorse().getBooking().getId());
            settlePlannedStay(stay, departedAt);
        }
    }

    private void settlePlannedStay(HorseStay stay, Instant through) {
        var from = plannedSince(stay);
        if (from == null || !through.isAfter(from) || costs.findByHorseStay_Id(stay.getId()).stream()
                .anyMatch(cost -> cost.getCategory() == com.example.racehorse_transport.entity.enums.CostCategory.PLANNED_STABLING)) return;
        var booking = stay.getBookingHorse().getBooking();
        var departure = booking.getDeparture();
        var quotation = quotations.findByBooking_IdAndStatus(booking.getId(),
                com.example.racehorse_transport.entity.enums.QuotationStatus.ACCEPTED)
                .orElseThrow(() -> new com.example.racehorse_transport.exception.ResourceNotFoundException("Không tìm thấy báo giá."));
        var list = quotation.getPriceList();
        var price = com.example.racehorse_transport.policy.PricingPolicy.plannedStablingUnitPrice(list,
                priceItems.findByPriceList_Id(list.getId()), departure.getRoute().getId());
        var amount = StablingPolicy.plannedFee(from, through, java.time.ZoneId.of("Asia/Ho_Chi_Minh"), price);
        if (amount.signum() <= 0) return;
        var cost = new CostItem();
        cost.setCategory(com.example.racehorse_transport.entity.enums.CostCategory.PLANNED_STABLING);
        cost.setDescription("Lưu chuồng theo kế hoạch sau khi chọn chuyến");
        cost.setAmount(amount);
        cost.setCurrencyCode(quotation.getCurrencyCode());
        cost.setPayerType(com.example.racehorse_transport.entity.enums.CostPayerType.CUSTOMER);
        cost.setBooking(booking);
        cost.setHorseStay(stay);
        cost.setIncurredAt(through);
        cost.setApprovedAt(through);
        extras.createStablingCharge(booking.getId(), costs.saveAndFlush(cost).getId());
    }

    private Instant plannedSince(HorseStay stay) {
        var booking = stay.getBookingHorse().getBooking();
        if (stay.getBalanceHoldAt() == null || stay.getMissedDeparture() == null || booking.getDeparture() == null
                || stay.getMissedDeparture().getId().equals(booking.getDeparture().getId())
                || !readiness.isPaidInFull(booking.getId())) return null;
        var choice = history.findFirstByBooking_IdAndFromStatusAndToStatusOrderByChangedAtDescIdDesc(booking.getId(),
                com.example.racehorse_transport.entity.enums.BookingStatus.SCHEDULE_CHANGE_REQUIRED,
                com.example.racehorse_transport.entity.enums.BookingStatus.CONFIRMED)
                .map(row -> row.getChangedAt()).filter(at -> !at.isBefore(stay.getActualStartAt())).orElse(null);
        if (choice == null) return null;
        return payments.findByBooking_IdOrderById(booking.getId()).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID
                        && payment.getPaymentType() == com.example.racehorse_transport.entity.enums.PaymentType.BALANCE)
                .map(payment -> payment.getPaidAt()).filter(at -> at != null && at.isAfter(choice))
                .max(Instant::compareTo).orElse(choice);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean readySettled(HorseStay stay) {
        return readinessSettlement(stay).paidStop() != null;
    }

    private record Settlement(Instant paidStop, StablingPolicy.Run companyWait) {}

    private Settlement readinessSettlement(HorseStay stay) {
        var ready = stay.getReadyAt();
        if (ready == null) return new Settlement(null, null);
        var relevant = costs.findStablingClockCosts(stay.getId()).stream()
                .filter(CostPayerPolicy::isAutomaticTransportFee)
                .filter(cost -> isRedelivery(cost) || !cost.getIncurredAt().isAfter(ready))
                .filter(cost -> cost.getAmount().compareTo(cost.getStablingDepositCredit()) > 0).toList();
        var issued = relevant.stream().filter(StablingTimelineServiceImpl::isRedelivery)
                .map(CostItem::getIncurredAt).min(Instant::compareTo).orElse(null);
        var heldFees = relevant.stream().filter(cost -> !isRedelivery(cost)).toList();
        var holdPaid = settledAt(heldFees, ready);
        var earliest = stay.getEarliestRedeliveryAt();
        if (issued != null && earliest != null && earliest.isBefore(issued)) issued = earliest;
        var companyWait = issued != null && holdPaid != null && issued.isAfter(holdPaid)
                ? new StablingPolicy.Run(holdPaid, issued) : null;
        return new Settlement(settledAt(relevant, ready), companyWait);
    }

    private static boolean isRedelivery(CostItem cost) {
        return cost.getCategory() == com.example.racehorse_transport.entity.enums.CostCategory.REDELIVERY;
    }

    private static Instant settledAt(List<CostItem> fees, Instant ready) {
        if (fees.stream().anyMatch(cost -> cost.getExtraChargePayment() == null
                || cost.getExtraChargePayment().getStatus() != PaymentStatus.PAID
                || cost.getExtraChargePayment().getPaidAt() == null)) return null;
        return fees.stream().map(CostItem::getExtraChargePayment).map(payment -> payment.getPaidAt())
                .filter(paid -> paid.isAfter(ready)).max(Instant::compareTo).orElse(ready);
    }
}
