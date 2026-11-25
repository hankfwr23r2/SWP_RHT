package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.repository.NotificationRecipientRepository;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.client.VnpayClient;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.PaymentMethod;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CostPayerPolicy;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.CostItemService;
import com.example.racehorse_transport.service.ExtraChargeService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExtraChargeServiceImpl implements ExtraChargeService {
    private static final Set<CostPayerType> CUSTOMER_SIDE = Set.of(CostPayerType.CUSTOMER, CostPayerType.CONSIGNEE);

    private final BookingRepository bookings;
    private final QuotationRepository quotations;
    private final CostItemRepository costs;
    private final PaymentRepository payments;
    private final AppUserRepository users;
    private final ObjectProvider<CostItemService> costItemServices;
    private final Clock clock;
    private final SystemConfigService configs;
    private final NotificationService notifications;
    private final NotificationRecipientRepository recipients;

    private static final String OVERDUE_TITLE = "Khoản thu thêm quá hạn";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    @Transactional(readOnly = true)
    public void requireNoOverdue(Long customerId) {
        if (payments.hasOverdue(customerId, PaymentType.EXTRA_CHARGE, PaymentStatus.PENDING, clock.instant())) {
            throw BookingRuleException.conflict("Bạn còn khoản thu thêm quá hạn. Vui lòng thanh toán trước khi gửi đơn mới.");
        }
    }

    @Override
    @Transactional
    public void remindOverdue() {
        var now = clock.instant();
        var startOfDay = LocalDate.ofInstant(now, BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toInstant();
        for (Long customerId : payments.findCustomersWithOverdue(PaymentType.EXTRA_CHARGE, PaymentStatus.PENDING, now)) {
            users.lockForExtraChargeReminder(customerId);
            if (!recipients.hasNoticeSince(customerId, OVERDUE_TITLE, startOfDay)
                    && payments.hasOverdue(customerId, PaymentType.EXTRA_CHARGE, PaymentStatus.PENDING, now)) {
                notifications.notify(List.of(customerId), null, OVERDUE_TITLE,
                        "Bạn có khoản thu thêm quá hạn. Vui lòng kiểm tra và thanh toán trong đơn hàng.", "/orders");
            }
        }
    }

    @Override
    @Transactional
    public Long createExtraChargeForUser(Long bookingId, List<Long> costItemIds, String username) {
        Long actorUserId = users.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy người thao tác.")).getId();
        return createExtraCharge(bookingId, costItemIds, actorUserId);
    }

    @Override
    @Transactional
    public Long createExtraCharge(Long bookingId, List<Long> costItemIds, Long actorUserId) {
        if (actorUserId == null || users.findById(actorUserId)
                .filter(user -> user.getStatus() == AppUserStatus.ACTIVE && "MANAGER".equals(user.getRole().getCode()))
                .isEmpty()) {
            throw new AccessDeniedException("Chỉ quản lý được lập khoản thu thêm.");
        }
        return create(bookingId, costItemIds, false);
    }

    @Override
    @Transactional
    public Long createStablingCharge(Long bookingId, Long costItemId) {
        return create(bookingId, List.of(costItemId), true);
    }

    private Long create(Long bookingId, List<Long> costItemIds, boolean stablingOnly) {
        Booking booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (costItemIds == null || costItemIds.isEmpty() || costItemIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(costItemIds).size() != costItemIds.size()) {
            throw new BookingRuleException("Chọn ít nhất một chi phí hợp lệ và không chọn trùng.");
        }
        var quotation = quotations.findByBooking_IdAndStatus(bookingId, QuotationStatus.ACCEPTED)
                .orElseThrow(() -> BookingRuleException.conflict("Đơn chưa có báo giá đã chấp nhận."));
        var selected = costs.findAllById(costItemIds);
        PaymentPolicy.requireExtraChargeAllowed(booking.getStatus(), selected);
        if (stablingOnly && !selected.stream().allMatch(CostPayerPolicy::isScheduledStablingFee)) {
            throw BookingRuleException.conflict("Chỉ lập khoản thu tự động cho phí giữ ngựa.");
        }
        var amount = PaymentPolicy.extraChargeAmount(bookingId, quotation.getCurrencyCode(), costItemIds.size(), selected);
        if (!"VND".equals(quotation.getCurrencyCode()) || !VnpayClient.isPayable(amount)) {
            throw BookingRuleException.conflict("Khoản thu thêm phải có số tiền thanh toán được qua VNPAY.");
        }
        if (selected.stream().anyMatch(CostPayerPolicy::countsAgainstEmergencyLimit)) {
            var approvedUnconsulted = costs.sumApprovedWithoutConsulting(bookingId, CUSTOMER_SIDE);
            CostPayerPolicy.requireConsultedOrWithinEmergencyLimit(CostPayerType.CUSTOMER, false, BigDecimal.ZERO,
                    approvedUnconsulted, booking.getEmergencySpendingLimit());
        }
        var linker = costItemServices.getIfAvailable();
        if (linker == null) {
            throw BookingRuleException.conflict("Chưa thể gắn khoản thu vào chi phí phát sinh.");
        }

        var payment = new Payment();
        payment.setBooking(booking);
        payment.setQuotation(quotation);
        payment.setPaymentType(PaymentType.EXTRA_CHARGE);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCurrencyCode(quotation.getCurrencyCode());
        payment.setAmount(amount);
        payment.setDueAt(clock.instant().plus(configs.getInt("EXTRA_CHARGE_DUE_DAYS"), ChronoUnit.DAYS));
        Long paymentId = payments.save(payment).getId();
        linker.linkExtraPayment(costItemIds, paymentId);
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Có khoản thu thêm",
                "Đơn " + booking.getCode() + " có khoản thu thêm. Xem hạn và thanh toán trong đơn hàng.", "/orders/" + bookingId);
        return paymentId;
    }
}
