package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.UnclaimedHorseResolutionRequestDto;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BalanceHoldPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UnclaimedHorseServiceImpl implements UnclaimedHorseService {
    private final HorseStayRepository stays;
    private final BookingRepository bookings;
    private final StaffRepository staff;
    private final SystemConfigService configs;
    private final HorseLegRepository legs;
    private final UnclaimedHandoverService handover;
    private final BookingStatusService statuses;
    private final Clock clock;
    private final UnclaimedSettlementService settlement;

    @Override
    @Transactional
    public void resolve(Long stayId, UnclaimedHorseResolutionRequestDto request, MultipartFile proof, String username) {
        var manager = staff.findByUser_Username(username)
                .filter(actor -> "MANAGER".equals(actor.getUser().getRole().getCode()))
                .orElseThrow(() -> new AccessDeniedException("Chỉ quản lý được ghi kết quả."));
        if (proof == null || proof.isEmpty()) throw new BookingRuleException("proof", "Cần tệp chứng minh bàn giao.");
        var bookingId = stays.findBookingId(stayId).orElseThrow(UnclaimedHorseServiceImpl::missing);
        var booking = bookings.findForUpdate(bookingId).orElseThrow(UnclaimedHorseServiceImpl::missing);
        var stay = stays.findForUpdate(stayId).orElseThrow(UnclaimedHorseServiceImpl::missing);
        if (booking.getStatus() != BookingStatus.IN_OPERATION || stay.getStayType() != HorseStayType.STABLING
                || stay.getLocation().getLocationType() != LocationType.HUB || stay.getBalanceHoldAt() != null
                || stay.getActualStartAt() == null || stay.getActualEndAt() != null) {
            throw BookingRuleException.conflict("Ngựa không còn chờ nhận tại hub sau giao hụt.");
        }
        var now = clock.instant();
        int noticeDays = configs.getInt("STABLING_MANAGER_NOTICE_DAYS");
        var noticeStart = stay.getPlannedStartAt().plus(configs.getInt("STABLING_WRITTEN_NOTICE_DAYS"), ChronoUnit.DAYS);
        if (bookings.firstNoticeSince(bookingId, BalanceHoldPolicy.WRITTEN_NOTICE_TITLE, noticeStart)
                .filter(sent -> BalanceHoldPolicy.writtenNoticeMature(now, sent, noticeDays)).isEmpty()) {
            throw BookingRuleException.conflict("Chưa đủ thời gian kể từ khi gửi văn bản nhận lại ngựa.");
        }
        var horse = stay.getBookingHorse();
        var journey = legs.findByBookingHorse_IdOrderBySequenceNumber(horse.getId());
        var failed = journey.stream().filter(leg -> leg.getStatus() != HorseLegStatus.PLANNED
                        && leg.getStatus() != HorseLegStatus.CANCELLED)
                .reduce((first, last) -> last)
                .filter(leg -> leg.getStatus() == HorseLegStatus.FAILED)
                .orElseThrow(UnclaimedHorseServiceImpl::missing);
        handover.record(stay, failed, manager, request, proof, now);
        horse.setStatus(BookingHorseStatus.RETURNED);
        journey.stream().filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED)
                .forEach(leg -> {
                    leg.setStatus(HorseLegStatus.CANCELLED);
                    leg.setStallPosition(null);
                });
        if (legs.countActiveHorsesNotDelivered(bookingId) == 0) {
            if (legs.hasDeliveredHorse(bookingId)) {
                statuses.transition(bookingId, BookingStatus.DELIVERED, "Đã giao hoặc trả mọi con ngựa.", manager.getUserId());
            } else {
                settlement.settle(bookingId);
                statuses.cancelAfterUnclaimedReturns(bookingId, manager.getUserId());
            }
        }
    }

    private static ResourceNotFoundException missing() {
        return new ResourceNotFoundException("Không tìm thấy lưu trú sau giao hụt.");
    }
}
