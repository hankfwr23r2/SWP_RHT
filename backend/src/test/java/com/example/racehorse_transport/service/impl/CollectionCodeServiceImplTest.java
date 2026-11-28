package com.example.racehorse_transport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.client.MailClient;
import com.example.racehorse_transport.dto.operation.CollectionCodeResponseDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.service.SystemConfigService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CollectionCodeServiceImplTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final MailClient mail = mock(MailClient.class);
    private final CollectionCodeServiceImpl service = new CollectionCodeServiceImpl(bookings, config, mail,
            mock(org.springframework.transaction.PlatformTransactionManager.class));
    private final Booking booking = new Booking();

    @BeforeEach
    void setUp() {
        AppUser user = new AppUser();
        user.setUsername("owner");
        Customer customer = new Customer();
        customer.setUser(user);
        booking.setCustomer(customer);
        booking.setStatus(BookingStatus.IN_OPERATION);
        when(bookings.findForUpdate(1L)).thenReturn(Optional.of(booking));
        when(bookings.findByIdAndCustomer_User_Username(1L, "owner")).thenReturn(Optional.of(booking));
        when(config.getInt("COLLECTION_CODE_MAX_ATTEMPTS")).thenReturn(2);
    }

    @Test
    void issuesSixDigitsOnceAndOnlyOwnerCanReadThem() {
        service.issue(booking);
        String code = service.read(1L, "owner").collectionCode();
        assertThat(code).matches("[0-9]{6}");
        service.issue(booking);
        assertThat(booking.getCollectionCode()).isEqualTo(code);
        assertThatThrownBy(() -> service.read(1L, "other")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(CollectionCodeResponseDto.from(booking).toString()).doesNotContain(code);
        verifyNoInteractions(mail);
    }

    @Test
    void sendsEmailOnlyAfterTheBookingTransactionCommits() {
        booking.setCode("ORDER-1");
        booking.getCustomer().getUser().setEmail("owner@example.test");
        when(mail.isConfigured()).thenReturn(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.issue(booking);
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertThat(callbacks).hasSize(1);
            callbacks.forEach(TransactionSynchronization::afterCommit);
            verify(mail).send("owner@example.test", "Mã nhận ngựa ORDER-1",
                    "Mã nhận ngựa của bạn là " + booking.getCollectionCode() + ".");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void wrongAttemptsReachConfiguredLimitAndOwnerCanConfirmInstead() {
        booking.setCollectionCode("012345");
        assertThat(service.verify(1L, "012345")).isTrue();
        assertThat(service.verify(1L, "111111")).isFalse();
        assertThat(service.verify(1L, "222222")).isFalse();
        assertThat(service.verify(1L, "012345")).isFalse();
        assertThat(booking.getCollectionFailedAttempts()).isEqualTo(2);
        assertThatThrownBy(() -> service.confirm(1L, "other")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.confirm(1L, "owner").customerConfirmed()).isTrue();
        assertThat(service.verify(1L, null)).isTrue();
    }

    @Test
    void countsAWrongCodeOnlyAfterTheHandoverTransactionEnds() {
        booking.setCollectionCode("012345");
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.verify(1L, "111111")).isFalse();
            assertThat(booking.getCollectionFailedAttempts()).isZero();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(booking.getCollectionFailedAttempts()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
