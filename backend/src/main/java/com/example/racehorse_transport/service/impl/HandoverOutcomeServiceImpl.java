package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.service.BookingHorseService;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.HandoverOutcomeService;
import com.example.racehorse_transport.service.HorseStayService;
import com.example.racehorse_transport.service.PaymentService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HandoverOutcomeServiceImpl implements HandoverOutcomeService {

    private final HorseLegRepository legs;
    private final HorseStayService horseStays;
    private final BookingStatusService bookingStatus;
    private final BookingHorseService bookingHorses;
    private final PaymentService payments;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Handover handover, List<HorseLeg> matched, Long actorUserId) {
        Instant now = handover.getHandedAt();
        boolean boarding = TripExecutionPolicy.isBoarding(handover.getHandoverType());
        for (HorseLeg leg : matched) {
            if (boarding) {
                leg.setStatus(HorseLegStatus.IN_PROGRESS);
                leg.setActualStartAt(now);
                // Nhận từ hub: kết thúc lưu trú tập kết
                horseStays.closeActive(leg.getBookingHorse().getId(), now);
            } else {
                leg.setStatus(HorseLegStatus.COMPLETED);
                leg.setActualEndAt(now);
                if (handover.getHandoverType() == HandoverType.HUB_IN) {
                    horseStays.openAssembly(leg, now);
                }
            }
        }
        legs.flush();
        // Khóa đơn theo ID tăng dần khi một lần giao nhận có nhiều đơn
        List<Booking> bookings = matched.stream().map(l -> l.getBookingHorse().getBooking()).distinct()
                .sorted(Comparator.comparing(Booking::getId)).toList();
        if (handover.getHandoverType() == HandoverType.PICKUP) {
            // Nhận ngựa từ khách xong thì lập khoản số dư; xe chỉ rời nơi nhận khi đã trả đủ (#32)
            bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .forEach(b -> payments.requestBalance(b.getId(), now));
        } else if (!boarding) {
            // Quy tắc #16: con ACTIVE cuối cùng của đơn giao xong thì đơn DELIVERED
            bookings.stream()
                    .filter(b -> b.getStatus() == BookingStatus.IN_OPERATION && legs.countActiveHorsesNotDelivered(b.getId()) == 0)
                    .forEach(b -> bookingStatus.transition(b.getId(), BookingStatus.DELIVERED, "Đã giao xong mọi con ngựa", actorUserId));
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refuse(HorseLeg leg, String reason, Long actorUserId) {
        bookingHorses.exclude(leg.getBookingHorse().getId(), reason, actorUserId);
        legs.findByBookingHorse_IdOrderBySequenceNumber(leg.getBookingHorse().getId()).stream()
                .filter(l -> l.getStatus() == HorseLegStatus.PLANNED)
                .forEach(l -> l.setStatus(HorseLegStatus.CANCELLED));
        legs.flush();
    }
}
