package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.MailClient;
import com.example.racehorse_transport.dto.operation.CollectionCodeResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.service.CollectionCodeService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.security.SecureRandom;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CollectionCodeServiceImpl implements CollectionCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final BookingRepository bookings;
    private final SystemConfigService config;
    private final MailClient mail;
    private final PlatformTransactionManager transactions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(Booking booking) {
        if (booking.getCollectionCode() != null) return;
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        booking.setCollectionCode(code);
        String email = booking.getCustomer().getUser().getEmail();
        String bookingCode = booking.getCode();
        if (email == null || !mail.isConfigured()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    mail.send(email, "Mã nhận ngựa " + bookingCode, "Mã nhận ngựa của bạn là " + code + ".");
                } catch (MailException ignored) {
                    // Lỗi gửi thư không được làm lộ mã. Khách vẫn xem được mã trong đơn.
                }
            }
        });
    }

    @Override
    @Transactional
    public boolean verify(Long bookingId, String code) {
        Booking booking = locked(bookingId);
        if (booking.isCollectionConfirmed()) return true;
        if (booking.getStatus() != BookingStatus.IN_OPERATION || code == null
                || booking.getCollectionFailedAttempts() >= config.getInt("COLLECTION_CODE_MAX_ATTEMPTS")) return false;
        if (code.equals(booking.getCollectionCode())) return true;
        countFailureAfterRollback(bookingId);
        return false;
    }

    // Thao tác giao bị hủy cùng giao dịch, nên lần nhập sai được ghi sau khi giao dịch đó đã nhả khóa đơn
    private void countFailureAfterRollback(Long bookingId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            countFailure(bookingId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                var separate = new TransactionTemplate(transactions);
                separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                separate.executeWithoutResult(ignored -> countFailure(bookingId));
            }
        });
    }

    private void countFailure(Long bookingId) {
        Booking booking = locked(bookingId);
        booking.setCollectionFailedAttempts(booking.getCollectionFailedAttempts() + 1);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionCodeResponseDto read(Long bookingId, String username) {
        return CollectionCodeResponseDto.from(bookings.findByIdAndCustomer_User_Username(bookingId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn.")));
    }

    @Override
    @Transactional
    public CollectionCodeResponseDto confirm(Long bookingId, String username) {
        Booking booking = locked(bookingId);
        if (!booking.getCustomer().getUser().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Không tìm thấy đơn.");
        }
        if (booking.getStatus() != BookingStatus.IN_OPERATION) {
            throw new DispatchRuleException("status", "Chỉ xác nhận nhận ngựa khi đơn đang vận chuyển.", true);
        }
        booking.setCollectionConfirmed(true);
        return CollectionCodeResponseDto.from(booking);
    }

    private Booking locked(Long bookingId) {
        return bookings.findForUpdate(bookingId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
    }
}
