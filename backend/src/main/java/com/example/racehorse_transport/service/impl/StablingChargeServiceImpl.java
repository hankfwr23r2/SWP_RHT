package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.StablingClockResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.policy.StablingPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.ExtraChargeService;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.StablingChargeService;
import com.example.racehorse_transport.service.StablingTimelineService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StablingChargeServiceImpl implements StablingChargeService {
    private final BookingRepository bookings;
    private final HorseStayRepository stays;
    private final CostItemRepository costs;
    private final StaffRepository staff;
    private final ExtraChargeService extraCharges;
    private final SystemConfigService configs;
    private final Clock clock;
    private final NotificationService notifications;
    private final StablingTimelineService timeline;
    private final AuditService audit;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String DAILY_FEE = "STABLING_DAILY_FEE";

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void chargeReturnedBalanceHolds(Long bookingId) {
        lockBooking(bookingId);
        for (var handover : stays.findUnbilledBalanceHoldReturns(bookingId)) {
            var stay = handover.getHorseStay();
            chargeThrough(stay, stay.getActualEndAt(), handover.getHandledBy().getUserId());
            audit.record(null, "BALANCE_HOLD_BILLED", "horse_stay", stay.getId(), null, null);
        }
    }

    @Override
    @Transactional
    public int remindOverdueStabling() {
        Instant now = clock.instant();
        List<HorseStay> overdue = stays.findByStayTypeAndActualStartAtIsNotNullAndActualEndAtIsNullAndPlannedEndAtBefore(HorseStayType.STABLING, now);
        var date = now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        var start = date.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        var end = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        int sent = 0;
        for (HorseStay stay : overdue.stream()
                .sorted(Comparator.comparing(stay -> stay.getBookingHorse().getBooking().getId())).toList()) {
            if (stay.getBalanceHoldAt() != null) continue;
            Booking booking = stay.getBookingHorse().getBooking();
            lockBooking(booking.getId());
            String title = "Quá hạn giữ ngựa tại hub";
            if (bookings.hasNotificationBetween(booking.getId(), title, start, end)) continue;
            String horse = stay.getBookingHorse().getHorse().getName();
            String accrued = money(accrued(List.of(stay), now).longValue());
            String link = "/orders/" + booking.getId();
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), title,
                    horse + " đã quá hạn giữ tại " + stay.getLocation().getName() + " từ " + TIME.format(stay.getPlannedEndAt())
                            + ". Phí lưu chuồng và chăm sóc tạm tính " + accrued + " và vẫn tính cho mỗi 24 giờ trọn vẹn. Liên hệ công ty để nhận ngựa.", link);
            sent++;
        }
        return sent;
    }

    private static String money(long amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + " ₫";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requirePaid(HorseStay stay) {
        if (stay.getStayType() != HorseStayType.STABLING) return;
        lockBooking(stay.getBookingHorse().getBooking().getId());
        var charges = costs.findStablingClockCosts(stay.getId());
        if (stay.getFailedDeliveryLeg() != null && (stay.getReadyAt() == null || charges.stream()
                .noneMatch(cost -> cost.getCategory() == CostCategory.REDELIVERY))) {
            throw new DispatchRuleException("payment", "Cần ghi khách sẵn sàng và lập cước giao lại trước khi lên xe.", true);
        }
        if (timeline.readySettled(stay) && unbilledBlocks(stay, clock.instant()) > 0) {
            throw new DispatchRuleException("payment", "Phí giữ đang được chốt. Vui lòng kiểm tra lại trước khi lên xe.", true);
        }
        if (charges.stream().filter(CostPayerPolicy::isAutomaticTransportFee)
                .filter(cost -> cost.getAmount().compareTo(cost.getStablingDepositCredit()) > 0)
                .anyMatch(cost -> cost.getExtraChargePayment() == null
                        || cost.getExtraChargePayment().getStatus() != PaymentStatus.PAID)) {
            throw new DispatchRuleException("payment", "Khách chưa thanh toán phí giữ ngựa. Chưa thể giao ngựa lên xe giao lại.", true);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void chargeAfterReadiness(HorseStay stay, Instant through) {
        if (timeline.readySettled(stay)) chargeThrough(stay, through, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void chargeThrough(HorseStay stay, Instant through, Long actorUserId) {
        if (stay.getStayType() != HorseStayType.STABLING) return;
        var booking = stay.getBookingHorse().getBooking();
        lockBooking(booking.getId());
        var previous = costs.findByHorseStay_BookingHorse_Id(stay.getBookingHorse().getId()).stream()
                .filter(CostPayerPolicy::isScheduledStablingFee).toList();
        // Phiếu lập theo quy trình cũ không có số ngày. Giữ nguyên khoản đã ghi.
        long billedDays = previous.stream().mapToLong(cost -> cost.getStablingDays() == null ? 1 : cost.getStablingDays()).sum();
        for (CostItem cost : previous) {
            if (cost.getExtraChargePayment() == null && cost.getAmount().compareTo(cost.getStablingDepositCredit()) > 0) {
                if (cost.getApprovedAt() == null) approve(cost, actorUserId == null ? null : staff.getReferenceById(actorUserId), clock.instant());
                extraCharges.createStablingCharge(booking.getId(), cost.getId());
            }
        }
        if (stay.getActualEndAt() != null) timeline.chargePlannedStay(stay, through);
        var holdAt = stay.getBalanceHoldAt();
        long newDays = StablingPolicy.blocks(timeline.runs(stay, through), through, hours(), maxBlocks()) - billedDays;
        if (newDays <= 0) return;

        Instant now = clock.instant();
        Staff actor = actorUserId == null ? null : staff.getReferenceById(actorUserId);
        CostItem fee = new CostItem();
        fee.setCategory(CostCategory.STABLING);
        fee.setDescription("Lưu chuồng và chăm sóc tại " + stay.getLocation().getName() + " · " + newDays + " lần 24 giờ");
        fee.setAmount(StablingPolicy.fee(newDays, unitPrice(stay)));
        fee.setStablingDays(newDays);
        fee.setCurrencyCode("VND");
        fee.setPayerType(holdAt == null ? CostPayerType.CONSIGNEE : CostPayerType.CUSTOMER);
        fee.setBooking(booking);
        fee.setHorseStay(stay);
        fee.setIncurredAt(through);
        fee.setRecordedBy(actor);
        approve(fee, actor, now);
        if (holdAt != null && booking.getStatus() == BookingStatus.CANCELLED) {
            var deposit = bookings.paidDepositForStabling(booking.getId());
            fee.setStablingDepositCredit(StablingPolicy.depositCredit(fee.getAmount(), deposit,
                    costs.sumStablingDepositCredit(booking.getId())));
        }
        costs.saveAndFlush(fee);
        if (fee.getAmount().compareTo(fee.getStablingDepositCredit()) > 0) {
            extraCharges.createStablingCharge(booking.getId(), fee.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal accrued(List<HorseStay> held, Instant through) {
        return held.stream().map(stay -> StablingPolicy.fee(
                StablingPolicy.blocks(timeline.runs(stay, through), through, hours(), maxBlocks()), unitPrice(stay)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void warnBeforeNextBlock(List<HorseStay> held, Instant now) {
        for (HorseStay stay : held) {
            StablingPolicy.nextMark(timeline.runs(stay, now), now, hours(), maxBlocks()).ifPresent(mark -> {
                var booking = stay.getBookingHorse().getBooking();
                var from = mark.minus(Duration.ofHours(configs.getInt("STABLING_WARNING_HOURS")));
                String title = "Sắp tính thêm phí giữ · " + stay.getBookingHorse().getHorse().getName();
                if (now.isBefore(from) || !now.isBefore(mark)
                        || bookings.hasNotificationBetween(booking.getId(), title, from, mark)) return;
                notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), title,
                        "Tới " + TIME.format(mark) + " sẽ tính thêm " + money(unitPrice(stay).longValue())
                                + " nếu ngựa vẫn ở " + stay.getLocation().getName() + ".", "/orders/" + booking.getId());
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StablingClockResponseDto clock(
            HorseStay stay, Instant now, boolean canReady, boolean canCollect) {
        var runs = timeline.runs(stay, now);
        var amount = StablingPolicy.fee(StablingPolicy.blocks(runs, now, hours(), maxBlocks()), unitPrice(stay));
        var next = StablingPolicy.nextMark(runs, now, hours(), maxBlocks()).orElse(null);
        boolean running = runs.stream().anyMatch(run -> !run.from().isAfter(now) && (run.to() == null || now.isBefore(run.to())));
        return StablingClockResponseDto.from(stay, running,
                StablingPolicy.elapsed(runs, now).toSeconds(), amount, next, canReady, canCollect);
    }

    private long unbilledBlocks(HorseStay stay, Instant through) {
        long billed = costs.findByHorseStay_BookingHorse_Id(stay.getBookingHorse().getId()).stream()
                .filter(CostPayerPolicy::isScheduledStablingFee)
                .mapToLong(cost -> cost.getStablingDays() == null ? 1 : cost.getStablingDays()).sum();
        return StablingPolicy.blocks(timeline.runs(stay, through), through, hours(), maxBlocks()) - billed;
    }

    private BigDecimal unitPrice(HorseStay stay) {
        return stay.getStablingUnitPrice() != null ? stay.getStablingUnitPrice() : BigDecimal.valueOf(configs.getInt(DAILY_FEE));
    }

    private int maxBlocks() {
        return configs.getInt("STABLING_MAX_BLOCKS");
    }

    private StablingPolicy.Hours hours() {
        return new StablingPolicy.Hours(LocalTime.of(configs.getInt("STABLING_OPEN_HOUR"), 0),
                LocalTime.of(configs.getInt("STABLING_CLOSE_HOUR"), 0), LocalTime.of(configs.getInt("STABLING_MORNING_HOUR"), 0),
                ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private void lockBooking(Long bookingId) {
        bookings.findForUpdate(bookingId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
    }

    private static void approve(CostItem cost, Staff actor, Instant now) {
        cost.setApprovedBy(actor);
        cost.setApprovedAt(now);
    }
}
