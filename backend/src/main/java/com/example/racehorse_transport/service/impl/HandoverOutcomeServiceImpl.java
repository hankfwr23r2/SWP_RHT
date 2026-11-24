package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.service.BookingCancellationService;
import com.example.racehorse_transport.service.BookingHorseService;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.HandoverOutcomeService;
import com.example.racehorse_transport.service.HorseStayService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.PaymentService;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
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
    private final BookingCancellationService cancellations;
    private final DocumentCompletenessService documents;
    private final NotificationService notifications;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Handover handover, List<HorseLeg> matched, Instant now, Long actorUserId) {
        boolean agent = handover.getHandoverType() == HandoverType.AGENT;
        boolean boarding = TripExecutionPolicy.isBoarding(handover.getHandoverType(), handover.getFromParty() != null);
        // Khóa đơn theo ID tăng dần khi một lần giao nhận có nhiều đơn
        List<Booking> bookings = matched.stream().map(l -> l.getBookingHorse().getBooking()).distinct()
                .sorted(Comparator.comparing(Booking::getId)).toList();
        if (agent && boarding) {
            // Quy tắc #35: đơn nhập chỉ nhận ngựa ở cửa khẩu khi đủ giấy bắt buộc, gồm giấy vận chuyển mẫu 14a
            bookings.stream().filter(b -> !documents.isComplete(b.getId())).findFirst().ifPresent(b -> {
                throw new DispatchRuleException("documents", "Đơn " + b.getCode()
                        + " còn thiếu giấy bắt buộc (gồm giấy vận chuyển mẫu 14a). Không nhận ngựa ở cửa khẩu.", true);
            });
        }
        handover.setStatus(HandoverStatus.DONE);
        handover.setHandedAt(now);
        // Bàn giao ca chỉ đổi người phụ trách (người ký), ngựa vẫn trên xe, chặng không đổi
        if (handover.getHandoverType() == HandoverType.SHIFT) return;
        // Nghỉ đêm: ngựa tạm xuống xe gửi đối tác chuồng rồi lên lại; chặng vẫn đang chạy
        if (handover.getHandoverType() == HandoverType.STAY_IN) {
            matched.forEach(leg -> horseStays.openOvernight(leg, handover.getTripStop(), handover.getToParty(), now));
            return;
        }
        if (handover.getHandoverType() == HandoverType.STAY_OUT) {
            matched.forEach(leg -> horseStays.closeActive(leg.getBookingHorse().getId(), now));
            return;
        }
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
            if (agent) {
                // Đơn xuất: chặng đại lý kế tiếp bắt đầu. Đơn nhập: chặng đại lý trước đó kết thúc
                HorseLeg adjacent = adjacentAgentLeg(leg, !boarding);
                if (boarding) {
                    adjacent.setStatus(HorseLegStatus.COMPLETED);
                    adjacent.setActualEndAt(now);
                } else {
                    adjacent.setStatus(HorseLegStatus.IN_PROGRESS);
                    adjacent.setActualStartAt(now);
                }
            }
        }
        legs.flush();
        if (handover.getHandoverType() == HandoverType.PICKUP || agent && boarding) {
            // Nhận ngựa từ khách xong thì lập khoản số dư; xe chỉ rời nơi nhận khi đã trả đủ (#32)
            bookings.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .forEach(b -> payments.requestBalance(b.getId(), now));
        } else if (!boarding) {
            // Quy tắc #16: con ACTIVE cuối cùng của đơn giao xong thì đơn DELIVERED
            bookings.stream()
                    .filter(b -> b.getStatus() == BookingStatus.IN_OPERATION && legs.countActiveHorsesNotDelivered(b.getId()) == 0)
                    .forEach(b -> bookingStatus.transition(b.getId(), BookingStatus.DELIVERED, "Đã giao xong mọi con ngựa", actorUserId));
        }
        notifyCustomers(handover, matched);
    }

    @Override
    public Party stablePartner(Long partyId) {
        return horseStays.requireStablePartner(partyId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void atHub(Handover handover, HorseLeg leg, Long actorUserId) {
        Booking booking = leg.getBookingHorse().getBooking();
        if (handover.getHandoverType() == HandoverType.PICKUP) {
            payments.requestBalance(booking.getId(), handover.getHandedAt());
        } else if (handover.getHandoverType() == HandoverType.DELIVERY
                && booking.getStatus() == BookingStatus.IN_OPERATION && legs.countActiveHorsesNotDelivered(booking.getId()) == 0) {
            bookingStatus.transition(booking.getId(), BookingStatus.DELIVERED, "Người nhận đã nhận đủ ngựa tại hub", actorUserId);
        }
        notifyCustomers(handover, List.of(leg));
    }

    // B6: báo khách các mốc khách thấy trên đơn (MAN_HINH: đã nhận ngựa, đã qua cửa khẩu, đã giao). Mỗi đơn một thông báo
    private void notifyCustomers(Handover handover, List<HorseLeg> handed) {
        String place = handover.getTripStop() != null ? placeOf(handover) : handover.getHorseStay().getLocation().getName();
        String party = handover.getToParty() != null ? handover.getToParty().getDisplayName()
                : handover.getFromParty() != null ? handover.getFromParty().getDisplayName() : null;
        handed.stream().collect(Collectors.groupingBy(l -> l.getBookingHorse().getBooking(), LinkedHashMap::new,
                Collectors.mapping(l -> l.getBookingHorse().getHorse().getName(), Collectors.joining(", "))))
                .forEach((booking, names) -> {
                    String[] notice = switch (handover.getHandoverType()) {
                        case PICKUP -> new String[] {"Đã nhận ngựa", "Công ty đã nhận " + names + " tại " + place + "."};
                        case DELIVERY -> new String[] {"Đã giao ngựa", names + " đã được giao cho người nhận tại " + place + "."};
                        case AGENT -> handover.getToParty() != null
                                ? new String[] {"Đã qua cửa khẩu", names + " đã được bàn giao cho đại lý " + party + " tại " + place + " để chở tiếp tới nơi nhận."}
                                : new String[] {"Đã nhận ngựa ở cửa khẩu", "Công ty đã nhận " + names + " từ đại lý " + party + " tại " + place + "."};
                        default -> null;
                    };
                    if (notice == null) return;
                    notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), notice[0], notice[1], "/orders/" + booking.getId());
                });
    }

    private static String placeOf(Handover handover) {
        var stop = handover.getTripStop();
        return stop.getLocation() != null ? stop.getLocation().getName() : stop.getBookingAddress().getAddressLine();
    }

    private HorseLeg adjacentAgentLeg(HorseLeg leg, boolean next) {
        List<HorseLeg> chain = legs.findByBookingHorse_IdOrderBySequenceNumber(leg.getBookingHorse().getId()).stream()
                .filter(l -> l.getStatus() != HorseLegStatus.CANCELLED && l.getStatus() != HorseLegStatus.FAILED).toList();
        return chain.get(chain.indexOf(leg) + (next ? 1 : -1));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refuse(Long bookingHorseId, String reason, Instant at, Long actorUserId) {
        bookingHorses.exclude(bookingHorseId, reason, actorUserId);
        for (HorseLeg leg : legs.findByBookingHorse_IdOrderBySequenceNumber(bookingHorseId)) {
            if (leg.getStatus() == HorseLegStatus.PLANNED) {
                leg.setStatus(HorseLegStatus.CANCELLED);
            } else if (leg.getStatus() == HorseLegStatus.IN_PROGRESS) {
                // Trước nơi nhận chỉ có chặng đại lý đang chở (đơn nhập): đại lý giữ ngựa, chặng kết thúc không giao
                leg.setStatus(HorseLegStatus.CANCELLED);
                leg.setActualEndAt(at);
            }
        }
        legs.flush();
        horseStays.dropAppointments(bookingHorseId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void returnUnpaid(List<HorseLeg> boarded, Instant at, Long actorUserId) {
        // Hủy đơn trước mọi thao tác ghi: luồng A từ chối thì không có gì thay đổi. Khóa đơn theo ID tăng dần
        boarded.stream().map(l -> l.getBookingHorse().getBooking().getId()).distinct().sorted()
                .forEach(id -> cancellations.cancelUnpaidAtPickup(id, actorUserId));
        for (HorseLeg leg : boarded) {
            leg.setStatus(HorseLegStatus.CANCELLED);
            leg.setActualEndAt(at);
            legs.findByBookingHorse_IdOrderBySequenceNumber(leg.getBookingHorse().getId()).stream()
                    .filter(l -> l.getStatus() == HorseLegStatus.PLANNED)
                    .forEach(l -> l.setStatus(HorseLegStatus.CANCELLED));
        }
        legs.flush();
    }
}
