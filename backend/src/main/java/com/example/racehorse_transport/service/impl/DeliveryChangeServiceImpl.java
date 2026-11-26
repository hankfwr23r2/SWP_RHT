package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.QuotationPolicy;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DeliveryChangeServiceImpl implements DeliveryChangeService {
    private final DeliveryChangeDataServiceImpl data;
    private final DeliveryRouteService routes;
    private final QuotationDraftService quotations;
    private final PaymentService payments;
    private final AuditService audit;
    private final StaffRepository staff;
    private final NotificationService notifications;
    private final PlatformTransactionManager transactions;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public DeliveryChangeResponseDto preview(Long id, String username, DeliveryChangeRequestDto request) {
        return change(id, username, request, null);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public DeliveryChangeResponseDto confirm(Long id, String username, DeliveryChangeConfirmRequestDto request) {
        return change(id, username, request.selection(), request.expectedTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public String disabledReason(Booking booking) {
        return data.reason(booking);
    }

    private DeliveryChangeResponseDto change(Long id, String username, DeliveryChangeRequestDto request, BigDecimal expected) {
        var read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        var snapshot = read.execute(tx -> {
            var loaded = data.load(id, username, request, false).snapshot();
            data.detachReadSnapshot();
            return loaded;
        });
        var route = routes.pricing(snapshot.hubId(), snapshot.destinations());
        var arrival = routes.arrivalTimes(route.plan(), snapshot.destinations(), snapshot.readyAt()).getLast();
        var date = LocalDate.ofInstant(arrival, QuotationPolicy.BUSINESS_ZONE);

        boolean confirm = expected != null;
        var transaction = confirm ? new TransactionTemplate(transactions) : read;
        return transaction.execute(tx -> {
            var current = data.load(id, username, request, confirm);
            if (!snapshot.equals(current.snapshot())) {
                throw BookingRuleException.conflict("Đơn hoặc địa chỉ đã thay đổi. Vui lòng xem trước lại.");
            }
            var change = quotations.replaceDelivery(current.previous(), current.active(), current.payments(), route, date, expected);
            if (confirm) {
                request.applyTo(current.target(), current.source());
                payments.reconcileBalance(change.quotation());
                notifyAndAudit(current, change.quotation().getId());
            }
            return change.preview();
        });
    }

    private record AuditSnapshot(BookingDetailResponseDto.AddressSnapshot address, Long quotationId) {}

    private void notifyAndAudit(DeliveryChangeDataServiceImpl.Data current, Long quotationId) {
        var booking = current.booking();
        var before = new AuditSnapshot(current.snapshot().previous(), current.previous().getId());
        var after = new AuditSnapshot(BookingDetailResponseDto.AddressSnapshot.from(current.target()), quotationId);
        audit.record(booking.getCustomer().getUserId(), DeliveryChangeDataServiceImpl.ACTION, "booking", booking.getId(), before, after);

        var recipients = new HashSet<>(staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE));
        recipients.addAll(staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE));
        if (booking.getSpecialist() != null) recipients.add(booking.getSpecialist().getUser().getId());
        if (!recipients.isEmpty()) {
            notifications.notify(List.copyOf(recipients), booking.getId(), "Khách đã đổi nơi giao",
                    "Nơi giao và báo giá của đơn " + booking.getCode() + " đã được cập nhật.", null);
        }
    }
}
