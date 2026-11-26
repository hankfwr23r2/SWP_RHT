package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.PickupMissPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PickupMissServiceImpl implements PickupMissService {
    private static final Set<BookingStatus> OPEN = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    private final BookingRepository bookings;
    private final BookingHorseRepository horses;
    private final HorseLegRepository legs;
    private final AppUserRepository users;
    private final BookingHorseService bookingHorses;
    private final BookingCancellationService cancellations;
    private final PickupMissEvidenceService evidence;
    private final HorseStayService stays;
    private final SystemConfigService configs;
    private final Clock clock;

    @Override
    @Transactional
    public void record(Long horseId, String reason, Instant at, Long actorId) {
        record(horseId, reason, null, null, null, at, actorId);
    }

    @Override
    @Transactional
    public void recordAtStop(Long horseId, HandoverRefusalRequestDto request, MultipartFile photo,
                             Instant arrivedAt, Instant at, Long actorId) {
        record(horseId, request.reason().strip(), request, photo, arrivedAt, at, actorId);
    }

    private void record(Long horseId, String reason, HandoverRefusalRequestDto request,
                        MultipartFile photo, Instant arrivedAt, Instant at, Long actorId) {
        BookingHorse horse = lockHorse(horseId);
        Booking booking = horse.getBooking();
        if (horse.getPickupMissedAt() != null && horse.getPickupMissChoice() != PickupMissChoice.RETRY_PICKUP
                && horse.getPickupMissChoice() != PickupMissChoice.RESCHEDULE_PICKUP
                && horse.getPickupMissChoice() != PickupMissChoice.CHANGE_DEPARTURE
                && horse.getPickupMissChoice() != PickupMissChoice.SELF_DELIVERY) return;
        if (!OPEN.contains(booking.getStatus()) || horse.getStatus() != BookingHorseStatus.ACTIVE) {
            throw BookingRuleException.conflict("Ngựa không còn chờ nhận.");
        }
        var journey = legs.findByBookingHorse_IdOrderBySequenceNumber(horseId);
        var trip = journey.stream().filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED)
                .filter(leg -> leg.getLegType() == com.example.racehorse_transport.entity.enums.HorseLegType.PICKUP
                        || leg.getLegType() == com.example.racehorse_transport.entity.enums.HorseLegType.DIRECT)
                .map(leg -> leg.getVehicleTrip()).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        journey.stream().filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED)
                .forEach(leg -> leg.setStatus(HorseLegStatus.CANCELLED));
        // Đơn nhập hụt ở cửa khẩu: chặng đại lý đang chở kết thúc, ngựa ở lại với đại lý
        journey.stream().filter(leg -> leg.getStatus() == HorseLegStatus.IN_PROGRESS
                && leg.getAgentParty() != null).forEach(leg -> {
                    leg.setStatus(HorseLegStatus.CANCELLED);
                    leg.setActualEndAt(at);
                });
        stays.dropAppointments(horseId);
        horse.setPickupMissedAt(at);
        horse.setPickupMissReason(reason);
        horse.setPickupMissLiability(null);
        var dueAt = PickupMissPolicy.choiceDueAt(at, checkInAt(booking),
                Duration.ofHours(configs.getInt("PICKUP_CHOICE_LONG_HOURS")),
                Duration.ofHours(configs.getInt("PICKUP_CHOICE_SHORT_HOURS")));
        horse.setPickupChoiceDueAt(dueAt);
        horse.setPickupMissChoice(null);
        evidence.record(horse, request, photo, arrivedAt, at, actorId, trip);
        legs.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public void requireSelfDeliveryBeforeCheckIn(Long horseId) {
        BookingHorse horse = horses.findById(horseId).orElseThrow(() -> missing());
        if (horse.getPickupMissChoice() != PickupMissChoice.SELF_DELIVERY) return;
        Instant checkIn = checkInAt(horse.getBooking());
        if (checkIn == null || !clock.instant().isBefore(checkIn)) {
            throw new DispatchRuleException("horseStayId", "Đã quá hạn tập kết của chuyến này.", true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requirePickupCanComplete(HorseLeg leg, Instant now) {
        if (!legs.existsByIdAndStatus(leg.getId(), HorseLegStatus.PLANNED)) {
            throw new DispatchRuleException("horseLegId", "Chặng đón đã được xử lý. Tải lại lượt xe.", true);
        }
        var horse = leg.getBookingHorse();
        if (horse.getPickupMissChoice() != PickupMissChoice.RETRY_PICKUP) return;
        Instant checkIn = checkInAt(horse.getBooking());
        if (checkIn == null || !now.isBefore(checkIn) || leg.getAlightTripStop().getPlannedArrivalAt().isAfter(checkIn)) {
            throw new DispatchRuleException("horseLegId", "Lượt đón lại không còn kịp tập kết.", true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PickupMissResponseDto> list(Long bookingId, String username) {
        var booking = bookings.findById(bookingId).orElseThrow(() -> missing());
        var role = users.findByUsername(username).orElseThrow(() -> missing()).getRole().getCode();
        if (!Set.of("COORDINATOR", "MANAGER").contains(role)
                && !booking.getCustomer().getUser().getUsername().equals(username)) throw denied();
        return horses.findForDetail(bookingId).stream().filter(horse -> horse.getPickupMissedAt() != null)
                .map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PickupAddOnResponseDto> addOns(Long horseId, String username) {
        requireManager(username);
        return bookingHorses.pickupAddOns(horseId);
    }

    @Override
    @Transactional
    public PickupAddOnResponseDto completeAddOn(Long horseId, CompletePickupAddOnRequestDto request, String username) {
        requireManager(username);
        var horse = lockHorse(horseId);
        if (horse.getStatus() != BookingHorseStatus.ACTIVE || !OPEN.contains(horse.getBooking().getStatus())) {
            throw BookingRuleException.conflict("Ngựa không còn hoạt động trong đơn.");
        }
        return bookingHorses.completePickupAddOn(horseId, request.serviceId(), clock.instant());
    }

    private com.example.racehorse_transport.entity.AppUser requireManager(String username) {
        var actor = users.findByUsername(username).orElseThrow(() -> missing());
        if (!"MANAGER".equals(actor.getRole().getCode())) throw denied();
        return actor;
    }

    @Override
    @Transactional
    public PickupMissResponseDto assignLiability(Long horseId, PickupMissLiabilityRequestDto request, String username) {
        var actor = requireManager(username);
        BookingHorse horse = lockHorse(horseId);
        if (horse.getPickupMissedAt() == null) throw missing();
        if (horse.getPickupMissLiability() != null && horse.getPickupMissLiability() != request.liability()) {
            throw BookingRuleException.conflict("Bên chịu trách nhiệm đã chốt.");
        }
        if (horse.getPickupMissLiability() == null) {
            horse.setPickupMissLiability(request.liability());
            evidence.audit(actor.getId(), "CONFIRM_PICKUP_MISS_LIABILITY", horseId, java.util.Map.of("liability", request.liability().name()));
            if (horse.getPickupMissChoice() == PickupMissChoice.DROP_HORSE
                    && horse.getStatus() == BookingHorseStatus.ACTIVE && OPEN.contains(horse.getBooking().getStatus())) {
                drop(horse, actor.getId());
            }
            if (horse.getPickupMissChoice() == PickupMissChoice.RETRY_PICKUP
                    && request.liability() == PickupMissLiability.CUSTOMER) {
                var receivedRetry = legs.findByBookingHorse_IdOrderBySequenceNumber(horseId).stream()
                        .filter(leg -> leg.getLegType() == HorseLegType.PICKUP && leg.getVehicleTrip() != null
                                && leg.getActualStartAt() != null && !leg.getActualStartAt().isBefore(horse.getPickupMissedAt()))
                        .findFirst();
                receivedRetry.or(() -> legs.findPickupRetryPlans(horseId).stream().findFirst())
                        .ifPresent(leg -> chargeRetry(horse, leg));
            }
            if (horse.getPickupMissChoice() == null && request.liability() == PickupMissLiability.CUSTOMER
                    && horse.getPickupRetryDeparture() != null && hasReceived(horse.getBooking())) {
                drop(horse, actor.getId());
            }
        }
        return response(horse);
    }

    @Override
    @Transactional
    public PickupMissResponseDto decide(Long horseId, PickupMissChoiceRequestDto request, String username) {
        BookingHorse horse = lockHorse(horseId);
        var user = users.findByUsername(username).orElseThrow(() -> missing());
        if (!horse.getBooking().getCustomer().getUser().getId().equals(user.getId())) {
            if (!"COORDINATOR".equals(user.getRole().getCode())) throw denied();
            if (request.phoneNote() == null || request.phoneNote().isBlank()) {
                throw BookingRuleException.conflict("Cần ghi nội dung khách chọn qua điện thoại.");
            }
        }
        if (horse.getPickupMissChoice() == request.choice()) return response(horse);
        if (!choices(horse).contains(request.choice())) throw BookingRuleException.conflict("Lựa chọn đã hết hạn hoặc không còn phù hợp.");
        apply(horse, request, user.getId());
        evidence.audit(user.getId(), "CHOOSE_AFTER_PICKUP_MISS", horseId,
                java.util.Map.of("choice", request.choice().name(), "phoneNote",
                        request.phoneNote() == null ? "" : request.phoneNote().strip()));
        return response(horse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> expired() {
        Instant now = clock.instant();
        return java.util.stream.Stream.concat(horses.findExpiredPickupChoices(now, OPEN).stream(),
                horses.findPickupChoicesPastCheckIn(now.plus(checkInLead()), OPEN).stream()).toList();
    }

    @Override
    @Transactional
    public void expire(Long horseId) {
        BookingHorse horse = lockHorse(horseId);
        Instant now = clock.instant();
        Booking booking = horse.getBooking();
        if (!OPEN.contains(booking.getStatus()) || !choiceExpired(horse, now)) return;
        if (hasReceived(booking)) {
            drop(horse, null);
        } else if (allActiveHorsesMissed(booking, now)) {
            moveOffDeparture(horse, null);
        }
    }

    private boolean choiceExpired(BookingHorse horse, Instant now) {
        var choice = horse.getPickupMissChoice();
        if (choice == null) {
            return horse.getPickupChoiceDueAt() != null && !now.isBefore(horse.getPickupChoiceDueAt());
        }
        if (choice != PickupMissChoice.RETRY_PICKUP && choice != PickupMissChoice.SELF_DELIVERY) return false;
        var deadline = checkInAt(horse.getBooking());
        return deadline != null && !now.isBefore(deadline)
                && !horses.receivedAfterPickupMiss(horse.getId(), horse.getPickupMissedAt());
    }

    private void apply(BookingHorse horse, PickupMissChoiceRequestDto request, Long actorId) {
        var booking = horse.getBooking();
        if (request.choice() == PickupMissChoice.DROP_HORSE) {
            drop(horse, actorId);
        } else if (request.choice() == PickupMissChoice.CANCEL_BOOKING) {
            cancellations.cancelAfterPickupMiss(booking.getId(), horse.getPickupMissLiability() == PickupMissLiability.CUSTOMER, actorId);
        } else if (request.choice() == PickupMissChoice.CHANGE_DEPARTURE) {
            var origin = reschedulePickupCharge(horse);
            if (origin != null) {
                bookingHorses.repricePickup(horse, origin, activeHorses(booking), false);
            }
            moveOffDeparture(horse, actorId);
        } else if (request.choice() == PickupMissChoice.RETRY_PICKUP) {
            horse.setPickupRetryDeparture(booking.getDeparture());
            if (horse.getPickupMissLiability() == PickupMissLiability.CUSTOMER) {
                chargeRetry(horse, retryPlan(horse));
            }
        } else if (request.choice() == PickupMissChoice.SELF_DELIVERY) {
            var arrival = request.hubArrivalAt();
            if (arrival == null || !arrival.isAfter(clock.instant()) || !arrival.isBefore(checkInAt(booking))) {
                throw BookingRuleException.conflict("Giờ hẹn phải trước hạn tập kết.");
            }
            if (waivePickupForSelfDelivery(horse)) {
                bookingHorses.repricePickup(horse, null, List.of(), true);
            }
            legs.findByBookingHorse_IdOrderBySequenceNumber(horse.getId()).stream()
                    .filter(leg -> leg.getLegType() == HorseLegType.PICKUP && leg.getStatus() == HorseLegStatus.PLANNED)
                    .forEach(leg -> leg.setStatus(HorseLegStatus.CANCELLED));
            horse.setPickupMissChoice(request.choice());
            stays.planSelfDropOff(horse.getId(), arrival);
        } else if (request.choice() == PickupMissChoice.RESCHEDULE_PICKUP) {
            var today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
            var missedOn = LocalDate.ofInstant(horse.getPickupMissedAt(), ZoneId.of("Asia/Ho_Chi_Minh"));
            if (request.pickupDate() == null || !request.pickupDate().isAfter(today)
                    || request.pickupDate().isAfter(missedOn.plusDays(configs.getInt("PRICE_HOLD_DAYS")))) {
                throw BookingRuleException.conflict("Ngày đón mới phải nằm trong hạn giữ giá.");
            }
            cancellations.rebookDirectAfterPickupMiss(booking.getId(), request.pickupDate(), actorId);
            booking.setRequestedPickupDate(request.pickupDate());
            horses.findByBooking_Id(booking.getId()).stream()
                    .filter(line -> line.getStatus() == BookingHorseStatus.ACTIVE && line.getPickupMissedAt() != null)
                    .forEach(line -> line.setPickupMissChoice(PickupMissChoice.RESCHEDULE_PICKUP));
        }
        horse.setPickupMissChoice(request.choice());
    }

    private void drop(BookingHorse horse, Long actorId) {
        var booking = horse.getBooking();
        boolean last = horses.findByBooking_Id(booking.getId()).stream()
                .noneMatch(line -> !line.getId().equals(horse.getId()) && line.getStatus() == BookingHorseStatus.ACTIVE);
        if (horse.getPickupMissLiability() == null && (last || actorId != null)) {
            horse.setPickupMissChoice(PickupMissChoice.DROP_HORSE);
            return;
        }
        if (last) {
            cancellations.cancelAfterPickupMiss(booking.getId(), horse.getPickupMissLiability() == PickupMissLiability.CUSTOMER, actorId);
            horse.setStatus(BookingHorseStatus.EXCLUDED);
        } else {
            bookingHorses.exclude(horse.getId(), horse.getPickupMissReason(),
                    actorId == null ? booking.getCustomer().getUser().getId() : actorId);
        }
        legs.findByBookingHorse_IdOrderBySequenceNumber(horse.getId()).stream()
                .filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED)
                .forEach(leg -> leg.setStatus(HorseLegStatus.CANCELLED));
        stays.dropAppointments(horse.getId());
        horse.setPickupMissChoice(PickupMissChoice.DROP_HORSE);
    }

    private void moveOffDeparture(BookingHorse horse, Long actorId) {
        cancellations.releaseAfterPickupMiss(horse.getBooking().getId(),
                horse.getPickupMissLiability() == PickupMissLiability.CUSTOMER, actorId);
        horse.setPickupMissChoice(PickupMissChoice.CHANGE_DEPARTURE);
        for (BookingHorse line : horses.findByBooking_Id(horse.getBooking().getId())) {
            if (line.getStatus() == BookingHorseStatus.ACTIVE && line.getPickupMissedAt() != null) {
                line.setPickupMissChoice(PickupMissChoice.CHANGE_DEPARTURE);
            }
        }
    }

    private List<PickupMissChoice> choices(BookingHorse horse) {
        var booking = horse.getBooking();
        if (horse.getPickupMissChoice() == PickupMissChoice.CHANGE_DEPARTURE
                && booking.getStatus() == BookingStatus.SCHEDULE_CHANGE_REQUIRED
                && cancellations.isDirect(booking)
                && clock.instant().isBefore(horse.getPickupMissedAt().plus(Duration.ofDays(configs.getInt("PRICE_HOLD_DAYS"))))) {
            return List.of(PickupMissChoice.RESCHEDULE_PICKUP);
        }
        if (horse.getPickupMissChoice() != null || horse.getPickupChoiceDueAt() == null
                || choiceExpired(horse, clock.instant()) || !OPEN.contains(booking.getStatus())) return List.of();
        boolean entireBookingMissed = allActiveHorsesMissed(booking, null);
        List<PickupMissChoice> choices = new ArrayList<>();
        choices.add(PickupMissChoice.DROP_HORSE);
        if (booking.getDeparture() == null) {
            if (entireBookingMissed && cancellations.isDirect(booking)) choices.add(PickupMissChoice.RESCHEDULE_PICKUP);
        } else {
            if (canRetry(horse)) choices.add(PickupMissChoice.RETRY_PICKUP);
            choices.add(PickupMissChoice.SELF_DELIVERY);
            if (entireBookingMissed) choices.add(PickupMissChoice.CHANGE_DEPARTURE);
        }
        if (entireBookingMissed && horse.getPickupMissLiability() != null) {
            choices.add(PickupMissChoice.CANCEL_BOOKING);
        }
        return choices;
    }

    private boolean allActiveHorsesMissed(Booking booking, Instant expiryAt) {
        return !hasReceived(booking) && horses.findByBooking_Id(booking.getId()).stream()
                .filter(line -> line.getStatus() == BookingHorseStatus.ACTIVE)
                .allMatch(line -> expiryAt == null
                        ? line.getPickupMissedAt() != null && line.getPickupMissChoice() == null
                        : choiceExpired(line, expiryAt));
    }

    private boolean canRetry(BookingHorse horse) {
        var booking = horse.getBooking();
        if (horse.getPickupRetryDeparture() != null && horse.getPickupRetryDeparture().getId().equals(booking.getDeparture().getId())) {
            return false;
        }
        return retryPlan(horse) != null;
    }

    private HorseLeg retryPlan(BookingHorse horse) {
        var readyAt = clock.instant().plus(Duration.ofHours(configs.getInt("PICKUP_RETRY_PREPARATION_HOURS")));
        return legs.findPickupRetryPlans(horse.getId()).stream().filter(leg -> PickupMissPolicy.retryFits(readyAt,
                leg.getVehicleTrip().getPlannedStartAt(), leg.getAlightTripStop().getPlannedArrivalAt(),
                checkInAt(horse.getBooking()))).findFirst().orElse(null);
    }

    private List<BookingHorse> retryHorses(BookingHorse horse, HorseLeg retry) {
        var tripId = retry.getVehicleTrip().getId();
        return horses.findByBooking_Id(horse.getBooking().getId()).stream()
                .filter(line -> line.getStatus() == BookingHorseStatus.ACTIVE && line.getPickupMissedAt() != null)
                .filter(line -> line.getId().equals(horse.getId())
                        || legs.findByBookingHorse_IdOrderBySequenceNumber(line.getId()).stream()
                        .anyMatch(leg -> leg.getLegType() == HorseLegType.PICKUP && leg.getVehicleTrip() != null
                                && leg.getStatus() != HorseLegStatus.CANCELLED
                                && java.util.Objects.equals(leg.getVehicleTrip().getId(), tripId)))
                .toList();
    }

    private List<BookingHorse> activeHorses(Booking booking) {
        return horses.findByBooking_Id(booking.getId()).stream()
                .filter(line -> line.getStatus() == BookingHorseStatus.ACTIVE).toList();
    }

    private boolean waivePickupForSelfDelivery(BookingHorse horse) {
        return horse.getPickupMissLiability() == PickupMissLiability.COMPANY && !hasReceived(horse.getBooking())
                && activeHorses(horse.getBooking()).stream().allMatch(line -> line.getId().equals(horse.getId())
                        || line.getPickupMissChoice() == PickupMissChoice.SELF_DELIVERY);
    }

    private HorseLeg reschedulePickupCharge(BookingHorse horse) {
        if (horse.getPickupMissLiability() != PickupMissLiability.CUSTOMER) return null;
        return legs.findByBookingHorse_IdOrderBySequenceNumber(horse.getId()).stream()
                .filter(leg -> leg.getLegType() == HorseLegType.PICKUP && leg.getVehicleTrip() != null
                        && leg.getBoardTripStop() != null && leg.getBoardTripStop().getActualArrivalAt() != null)
                .reduce((earlier, latest) -> latest).orElse(null);
    }

    private void chargeRetry(BookingHorse horse, HorseLeg retry) {
        if (retry != null) {
            bookingHorses.repricePickup(horse, retry, retryHorses(horse, retry), false);
        }
    }

    private boolean hasReceived(Booking booking) {
        return booking.getStatus() == BookingStatus.IN_OPERATION || bookings.hasReceivedHorses(booking.getId());
    }

    private Instant checkInAt(Booking booking) {
        return booking.getDeparture() == null ? null : booking.getDeparture().getPlannedDepartureAt()
                .minus(checkInLead());
    }

    private Duration checkInLead() {
        return Duration.ofHours(configs.getInt("HUB_CHECKIN_HOURS"));
    }

    private PickupMissResponseDto response(BookingHorse horse) {
        var choices = choices(horse);
        var prices = new EnumMap<PickupMissChoice, PickupMissPriceResponseDto>(PickupMissChoice.class);
        if (horse.getPickupMissLiability() != null) {
            for (var choice : choices) {
                boolean exclude = choice == PickupMissChoice.DROP_HORSE;
                if (choice == PickupMissChoice.CANCEL_BOOKING || exclude && horses.findByBooking_Id(horse.getBooking().getId())
                        .stream().noneMatch(line -> !line.getId().equals(horse.getId()) && line.getStatus() == BookingHorseStatus.ACTIVE)) {
                    var cancellation = cancellations.previewAfterPickupMiss(horse.getBooking(),
                            horse.getPickupMissLiability() == PickupMissLiability.CUSTOMER);
                    if (cancellation != null && cancellation.allowed()) {
                        prices.put(choice, PickupMissPriceResponseDto.fromCancellation(cancellation));
                    }
                    continue;
                }
                HorseLeg retry = choice == PickupMissChoice.RETRY_PICKUP && horse.getPickupMissLiability() == PickupMissLiability.CUSTOMER
                        ? retryPlan(horse) : null;
                var riding = retry == null ? List.<BookingHorse>of() : retryHorses(horse, retry);
                if (choice == PickupMissChoice.CHANGE_DEPARTURE) {
                    retry = reschedulePickupCharge(horse);
                    if (retry != null) riding = activeHorses(horse.getBooking());
                }
                boolean waive = choice == PickupMissChoice.SELF_DELIVERY && waivePickupForSelfDelivery(horse);
                bookingHorses.previewPickup(horse, retry, riding, waive, exclude)
                        .ifPresent(price -> prices.put(choice, price));
            }
        }
        return PickupMissResponseDto.from(horse, choices, prices);
    }

    private BookingHorse lockHorse(Long horseId) {
        Long bookingId = horses.findBookingId(horseId).orElseThrow(() -> missing());
        bookings.findForUpdate(bookingId).orElseThrow(() -> missing());
        return horses.findById(horseId).orElseThrow(() -> missing());
    }

    private static ResourceNotFoundException missing() {
        return new ResourceNotFoundException("Không tìm thấy thông tin đón hụt.");
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Không có quyền xử lý đón hụt cho đơn này.");
    }
}
