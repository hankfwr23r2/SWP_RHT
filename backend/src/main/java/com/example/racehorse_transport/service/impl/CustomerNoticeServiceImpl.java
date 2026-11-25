package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.policy.CustomerNoticePolicy;
import com.example.racehorse_transport.policy.CustomerNoticePolicy.ExpectedTimes;
import com.example.racehorse_transport.policy.CustomerNoticePolicy.Notice;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.CustomerNoticeService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CustomerNoticeServiceImpl implements CustomerNoticeService {

    private final NotificationService notifications;
    private final StaffRepository staff;
    private final HorseLegRepository legs;
    private final HorseStayRepository stays;
    private final SystemConfigService configs;

    @Override
    public void bookingSubmitted(Booking booking) {
        notifyCustomer(booking, CustomerNoticePolicy.orderReceived(booking.getCode()));
        List<Long> specialists = staff.findUserIdsByRoleAndStatus("SPECIALIST", AppUserStatus.ACTIVE);
        if (!specialists.isEmpty()) {
            Notice notice = CustomerNoticePolicy.orderWaitingIntake(booking.getCode());
            notifications.notify(specialists, booking.getId(), notice.title(), notice.message(), "/specialist/review/" + booking.getId());
        }
    }

    @Override
    public void legAssigned(HorseLeg leg) {
        ExpectedTimes expected = CustomerNoticePolicy.expectedTimes(leg);
        Booking booking = leg.getBookingHorse().getBooking();
        // Con cùng đơn đã vào lượt này trước thì khách đã được báo
        if (expected.isEmpty() || legs.existsByVehicleTrip_IdAndBookingHorse_Booking_IdAndIdNotAndStatusNot(
                leg.getVehicleTrip().getId(), booking.getId(), leg.getId(), HorseLegStatus.CANCELLED)) {
            return;
        }
        int noticeMinutes = configs.getInt("SCHEDULE_CHANGE_NOTICE_MINUTES");
        List<HorseLeg> previous = legs.findByBookingHorse_IdOrderBySequenceNumber(leg.getBookingHorse().getId()).stream()
                .filter(l -> !l.getId().equals(leg.getId()) && l.getLegType() == leg.getLegType()).toList();
        ExpectedTimes told = CustomerNoticePolicy.lastTold(previous, noticeMinutes);
        CustomerNoticePolicy.expectedNotice(booking.getCode(), told, expected, noticeMinutes)
                .ifPresent(notice -> notifyCustomer(booking, notice));
    }

    @Override
    public void dropOffAppointed(HorseStay stay) {
        if (toldForAnotherHorse(stay, HorseStay::getPlannedStartAt)) {
            return;
        }
        Booking booking = stay.getBookingHorse().getBooking();
        notifyCustomer(booking, CustomerNoticePolicy.hubDropOff(booking.getCode(), stay.getLocation().getName(), stay.getPlannedStartAt()));
    }

    @Override
    public void collectionAppointed(HorseStay stay) {
        if (toldForAnotherHorse(stay, HorseStay::getPlannedEndAt)) {
            return;
        }
        Booking booking = stay.getBookingHorse().getBooking();
        notifyCustomer(booking, CustomerNoticePolicy.hubCollection(booking.getCode(), stay.getLocation().getName(), stay.getPlannedEndAt()));
    }

    // Con khác cùng đơn đã hẹn đúng giờ này ở hub này
    private boolean toldForAnotherHorse(HorseStay stay, Function<HorseStay, Instant> time) {
        return stays.findByBookingHorse_Booking_Id(stay.getBookingHorse().getBooking().getId()).stream()
                .anyMatch(other -> !other.getId().equals(stay.getId()) && other.getStayType() == stay.getStayType()
                        && other.getLocation().getId().equals(stay.getLocation().getId())
                        && other.getActualEndAt() == null && time.apply(other).equals(time.apply(stay)));
    }

    @Override
    @Transactional
    public void clearHubAppointmentsAndNotify(Long bookingId, int removedLegs) {
        long removedAppointments = stays.deleteByBookingHorse_Booking_IdAndStayTypeAndActualStartAtIsNull(bookingId, HorseStayType.ASSEMBLY);
        if (removedLegs == 0 && removedAppointments == 0) return;
        List<Long> coordinators = staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE);
        if (!coordinators.isEmpty()) {
            notifications.notify(coordinators,
                    bookingId, "Đã gỡ lịch vận chuyển", "Đã gỡ các chặng hoặc lịch hẹn hub chưa bắt đầu của đơn.", null);
        }
    }

    private void notifyCustomer(Booking booking, Notice notice) {
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), notice.title(), notice.message(),
                "/orders/" + booking.getId());
    }
}
