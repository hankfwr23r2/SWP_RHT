package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.NotificationRecipient;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.NotificationRecipientRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// Ghi thông báo và người nhận trên DB thật.
@PostgresIntegrationTest
@Transactional
class NotificationServiceTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationRecipientRepository recipients;
    @Autowired private AppUserRepository users;
    @Autowired private BookingRepository bookings;
    @Autowired private EntityManager em;

    @Test
    void oneNotificationReachesEachRecipientOnceAndStartsUnread() {
        Long customer = users.findByUsername("customer1.dev").orElseThrow().getId();
        Long manager = users.findByUsername("manager.dev").orElseThrow().getId();
        Long bookingId = bookings.findByCode("DEV-DIRECT-001").orElseThrow().getId();
        long before = notifications.count();

        notificationService.notify(List.of(customer, manager, customer), bookingId, "Đơn đã xác nhận", "Đơn DEV-DIRECT-001 đã được xác nhận", "/orders/1");
        em.flush();
        em.clear();

        assertThat(notifications.count()).isEqualTo(before + 1);
        List<NotificationRecipient> unread = recipients.findById_UserIdAndReadAtIsNull(customer);
        assertThat(unread).anySatisfy(r -> {
            assertThat(r.getNotification().getTitle()).isEqualTo("Đơn đã xác nhận");
            assertThat(r.getNotification().getBooking().getId()).isEqualTo(bookingId);
            assertThat(r.getNotification().getLinkUrl()).isEqualTo("/orders/1");
            assertThat(r.getNotification().getCreatedAt()).isNotNull();
        });
        Long notificationId = unread.stream().filter(r -> r.getNotification().getTitle().equals("Đơn đã xác nhận"))
                .findFirst().orElseThrow().getId().getNotificationId();
        assertThat(recipients.findAll()).filteredOn(r -> r.getId().getNotificationId().equals(notificationId))
                .extracting(r -> r.getId().getUserId()).containsExactlyInAnyOrder(customer, manager);
    }

    @Test
    void notificationWithoutBookingIsAllowed() {
        Long manager = users.findByUsername("manager.dev").orElseThrow().getId();

        notificationService.notify(List.of(manager), null, "Bảo trì", "Hệ thống bảo trì lúc 22:00", null);
        em.flush();

        assertThat(recipients.findById_UserIdAndReadAtIsNull(manager))
                .anySatisfy(r -> assertThat(r.getNotification().getBooking()).isNull());
    }

    @Test
    void invalidInputIsRejectedWithoutWriting() {
        Long manager = users.findByUsername("manager.dev").orElseThrow().getId();
        long before = notifications.count();

        assertThatThrownBy(() -> notificationService.notify(List.of(), null, "Tiêu đề", "Nội dung", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notificationService.notify(List.of(manager), null, " ", "Nội dung", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notificationService.notify(List.of(manager), null, "Tiêu đề", "", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> notificationService.notify(List.of(manager, -1L), null, "Tiêu đề", "Nội dung", null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> notificationService.notify(List.of(manager), -1L, "Tiêu đề", "Nội dung", null))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(notifications.count()).isEqualTo(before);
    }
}
