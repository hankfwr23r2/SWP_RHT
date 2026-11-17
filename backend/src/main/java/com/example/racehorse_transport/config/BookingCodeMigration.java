package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.util.BookingCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Đơn có từ trước khi có mã ngẫu nhiên: gán mã, đổi vận đơn VD-<năm>-<số> theo mã mới,
// và sửa thông báo trong app đang trỏ tới BK-<số> để vẫn mở được đơn. Chạy lại nhiều lần vẫn an toàn.
@Slf4j
@Component
@Order(23)
@RequiredArgsConstructor
public class BookingCodeMigration implements CommandLineRunner {

    private static final Pattern LEGACY_WAYBILL = Pattern.compile("^VD-(\\d{4})-\\d+$");
    private static final Pattern LEGACY_BOOKING_REF = Pattern.compile("^BK-(\\d+)$");

    private final BookingRepository bookingRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Map<Integer, String> codeById = new HashMap<>();
        List<Booking> changedBookings = new ArrayList<>();
        for (Booking booking : bookingRepository.findAll()) {
            boolean changed = false;
            if (booking.getCode() == null) {
                booking.setCode(BookingCodes.newCode());
                changed = true;
            }
            Matcher waybill = booking.getWaybillNo() == null ? null : LEGACY_WAYBILL.matcher(booking.getWaybillNo());
            if (waybill != null && waybill.matches()) {
                booking.setWaybillNo("VD-" + waybill.group(1) + "-" + booking.getCode().substring(3));
                changed = true;
            }
            if (changed) changedBookings.add(booking);
            codeById.put(booking.getId(), booking.getCode());
        }
        bookingRepository.saveAll(changedBookings);

        List<Notification> changedNotifications = new ArrayList<>();
        for (Notification notification : notificationRepository.findAll()) {
            Matcher ref = notification.getBookingId() == null ? null : LEGACY_BOOKING_REF.matcher(notification.getBookingId());
            if (ref == null || !ref.matches()) continue;
            int id = Integer.parseInt(ref.group(1));
            String code = codeById.get(id);
            if (code == null) continue;
            notification.setBookingId(code);
            if (notification.getLink() != null) {
                notification.setLink(notification.getLink().replaceAll("BK-0*" + id + "(?!\\d)", code));
            }
            changedNotifications.add(notification);
        }
        notificationRepository.saveAll(changedNotifications);

        if (!changedBookings.isEmpty() || !changedNotifications.isEmpty()) {
            log.info("Đã gán mã đơn ngẫu nhiên cho {} đơn và sửa {} thông báo", changedBookings.size(), changedNotifications.size());
        }
    }
}
