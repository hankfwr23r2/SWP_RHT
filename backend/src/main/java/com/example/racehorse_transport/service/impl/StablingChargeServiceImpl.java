package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.policy.StablingPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.ExtraChargeService;
import com.example.racehorse_transport.service.StablingChargeService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final String DAILY_FEE = "STABLING_DAILY_FEE";

    @Override
    @Transactional
    public int remindOverdueStabling() {
        Instant now = clock.instant();
        List<HorseStay> overdue = stays.findByStayTypeAndActualStartAtIsNotNullAndActualEndAtIsNullAndPlannedEndAtBefore(HorseStayType.STABLING, now);
        List<Long> managers = staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
        for (HorseStay stay : overdue) {
            Booking booking = stay.getBookingHorse().getBooking();
            String horse = stay.getBookingHorse().getHorse().getName();
            String accrued = money(StablingPolicy.fee(stay.getActualStartAt(), now, configs.getInt(DAILY_FEE)).longValue());
            String link = "/orders/" + booking.getId();
            notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), "Quá hạn giữ ngựa tại hub",
                    horse + " đã quá hạn giữ tại " + stay.getLocation().getName() + " từ " + TIME.format(stay.getPlannedEndAt())
                            + ". Phí lưu chuồng và chăm sóc tạm tính " + accrued + " và vẫn tính mỗi ngày. Liên hệ công ty để nhận ngựa.", link);
            if (!managers.isEmpty()) {
                notifications.notify(managers, booking.getId(), "Quá hạn giữ ngựa tại hub",
                        horse + " (đơn " + booking.getCode() + ") quá hạn giữ tại " + stay.getLocation().getName() + ", phí tạm tính " + accrued
                                + ". Quyết định giao lại, trả về người gửi hoặc xử lý theo hợp đồng.", "/manager/orders/" + booking.getId());
            }
        }
        return overdue.size();
    }

    private static String money(long amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + " ₫";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void precharge(HorseLeg replacement, String username) {
        var horse = replacement.getBookingHorse();
        lockBooking(horse.getBooking().getId());
        stays.findFirstByBookingHorse_IdAndActualStartAtIsNotNullAndActualEndAtIsNull(horse.getId())
                .filter(stay -> stay.getStayType() == HorseStayType.STABLING)
                .ifPresent(stay -> {
                    Staff actor = staff.findByUser_Username(username)
                            .orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
                    chargeThrough(stay, replacement.getPlannedStartAt(), actor.getUserId());
                });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requirePaid(HorseStay stay) {
        if (stay.getStayType() != HorseStayType.STABLING) return;
        lockBooking(stay.getBookingHorse().getBooking().getId());
        if (costs.findByHorseStay_Id(stay.getId()).stream().filter(CostPayerPolicy::isScheduledStablingFee)
                .anyMatch(cost -> cost.getExtraChargePayment() == null
                        || cost.getExtraChargePayment().getStatus() != PaymentStatus.PAID)) {
            throw new DispatchRuleException("payment", "Khách chưa thanh toán phí giữ ngựa. Chưa thể giao ngựa lên xe giao lại.", true);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void chargeThrough(HorseStay stay, Instant through, Long actorUserId) {
        if (stay.getStayType() != HorseStayType.STABLING) return;
        var booking = stay.getBookingHorse().getBooking();
        lockBooking(booking.getId());
        var previous = costs.findByHorseStay_Id(stay.getId()).stream().filter(CostPayerPolicy::isScheduledStablingFee).toList();
        // Phiếu lập theo quy trình cũ không có số ngày. Giữ nguyên khoản đã ghi.
        long billedDays = previous.stream().mapToLong(cost -> cost.getStablingDays() == null ? 1 : cost.getStablingDays()).sum();
        for (CostItem cost : previous) {
            if (cost.getExtraChargePayment() == null) {
                if (cost.getApprovedAt() == null) approve(cost, staff.getReferenceById(actorUserId), clock.instant());
                extraCharges.createStablingCharge(booking.getId(), cost.getId());
            }
        }
        long newDays = StablingPolicy.days(stay.getActualStartAt(), through) - billedDays;
        if (newDays <= 0) return;

        Instant now = clock.instant();
        Staff actor = staff.getReferenceById(actorUserId);
        CostItem fee = new CostItem();
        fee.setCategory(CostCategory.STABLING);
        fee.setDescription("Lưu chuồng và chăm sóc tại " + stay.getLocation().getName() + " · " + newDays + " ngày");
        fee.setAmount(StablingPolicy.feeForDays(newDays, configs.getInt(DAILY_FEE)));
        fee.setStablingDays(newDays);
        fee.setCurrencyCode("VND");
        fee.setPayerType(CostPayerType.CONSIGNEE);
        fee.setBooking(booking);
        fee.setHorseStay(stay);
        fee.setIncurredAt(now);
        fee.setRecordedBy(actor);
        approve(fee, actor, now);
        extraCharges.createStablingCharge(booking.getId(), costs.saveAndFlush(fee).getId());
    }

    private void lockBooking(Long bookingId) {
        bookings.findForUpdate(bookingId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
    }

    private static void approve(CostItem cost, Staff actor, Instant now) {
        cost.setApprovedBy(actor);
        cost.setApprovedAt(now);
    }
}
