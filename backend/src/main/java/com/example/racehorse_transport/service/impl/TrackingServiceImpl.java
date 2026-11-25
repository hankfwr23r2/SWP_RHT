package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.tracking.HorseJourneyResponseDto.HandoverEvidenceResponseDto;
import com.example.racehorse_transport.policy.DeliveryEtaPolicy;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.repository.DocumentRepository;

import com.example.racehorse_transport.dto.tracking.BookingJourneyResponseDto;
import com.example.racehorse_transport.policy.TrackingPolicy.CustodyHolder;
import com.example.racehorse_transport.dto.tracking.HorseJourneyResponseDto;
import com.example.racehorse_transport.dto.tracking.PublicTrackingRequestDto;
import com.example.racehorse_transport.dto.tracking.PublicTrackingResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.BookingParty;
import com.example.racehorse_transport.entity.BookingPartyId;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingPartyRole;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CustomerNoticePolicy;
import com.example.racehorse_transport.policy.CustomerNoticePolicy.ExpectedTimes;
import com.example.racehorse_transport.policy.TrackingPolicy;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingPartyRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.security.PublicTrackingGuard;
import com.example.racehorse_transport.service.TrackingService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrackingServiceImpl implements TrackingService {

    // Tra cứu công khai không hiện con đã loại khỏi đơn hoặc đã hủy
    private static final Set<BookingHorseStatus> HIDDEN_FROM_PUBLIC = Set.of(BookingHorseStatus.EXCLUDED, BookingHorseStatus.CANCELLED);

    private final BookingRepository bookings;
    private final BookingHorseRepository bookingHorses;
    private final BookingPartyRepository parties;
    private final HorseLegRepository legs;
    private final HorseStayRepository stays;
    private final HandoverHorseRepository handoverHorses;
    private final HandoverRepository handovers;
    private final DocumentRepository documents;
    private final PublicTrackingGuard guard;
    private final SystemConfigService configs;

    // Dữ liệu thô của một đơn, gom theo con ngựa
    // openHandovers: giao nhận chưa DONE ở điểm lên, xuống xe của các chặng (chưa quét con nào thì không có handover_horse)
    private record Journey(List<BookingHorse> lines, Map<Long, List<HorseLeg>> legs, Map<Long, List<HorseStay>> stays,
                           Map<Long, List<HandoverHorse>> handovers, Map<Long, List<Handover>> openHandovers) {

        <T> List<T> of(Map<Long, List<T>> rows, BookingHorse line) {
            return rows.getOrDefault(line.getId(), List.of());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingJourneyResponseDto bookingJourney(Long bookingId) {
        return journeyOf(bookings.findById(bookingId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn " + bookingId)));
    }

    @Override
    @Transactional(readOnly = true)
    public BookingJourneyResponseDto customerBookingJourney(Long bookingId, String username) {
        return journeyOf(bookings.findByIdAndCustomer_User_Username(bookingId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn.")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicTrackingResponseDto> publicTrack(PublicTrackingRequestDto request) {
        return request.codes().stream().map(code -> code.trim().toUpperCase(Locale.ROOT)).distinct()
                .map(code -> publicTrack(code, request.phoneLast4())).toList();
    }

    private PublicTrackingResponseDto publicTrack(String code, String phoneLast4) {
        Booking booking = bookings.findByCode(code).or(() -> bookings.findByWaybillNumber(code)).orElse(null);
        if (booking == null) return PublicTrackingResponseDto.notFound(code);
        guard.checkAllowed(booking.getId(), code);
        String shipperPhone = parties.findById(new BookingPartyId(booking.getId(), BookingPartyRole.SHIPPER)).map(BookingParty::getPhone).orElse(null);
        if (!TrackingPolicy.phoneSuffixMatches(shipperPhone, phoneLast4)) {
            guard.recordFailure(booking.getId());
            return PublicTrackingResponseDto.notFound(code);
        }
        guard.recordSuccess(booking.getId());
        Journey journey = load(booking);
        List<BookingHorse> shown = journey.lines().stream().filter(line -> !HIDDEN_FROM_PUBLIC.contains(line.getStatus())).toList();
        Instant plannedDelivery = shown.stream().flatMap(line -> journey.of(journey.legs(), line).stream())
                .filter(leg -> leg.getStatus() != HorseLegStatus.FAILED).map(HorseLeg::getPlannedEndAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new PublicTrackingResponseDto(code, true, booking.getStatus(), plannedDelivery,
                shown.stream().map(line -> publicHorse(line, journey)).toList());
    }

    private PublicTrackingResponseDto.PublicHorseDto publicHorse(BookingHorse line, Journey journey) {
        List<HorseLeg> horseLegs = journey.of(journey.legs(), line);
        List<HorseStay> horseStays = journey.of(journey.stays(), line);
        List<HandoverHorse> rows = journey.of(journey.handovers(), line);
        HandoverHorse last = lastDone(rows);
        Instant updatedAt = Stream.of(
                        horseLegs.stream().flatMap(leg -> Stream.of(leg.getActualStartAt(), leg.getActualEndAt())),
                        horseStays.stream().flatMap(stay -> Stream.of(stay.getActualStartAt(), stay.getActualEndAt())),
                        rows.stream().map(row -> row.getHandover().getHandedAt()))
                .flatMap(s -> s).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        CustodyHolder holder = holder(last);
        HorseStay activeStay = horseStays.stream().filter(stay -> stay.getActualStartAt() != null && stay.getActualEndAt() == null).findFirst().orElse(null);
        if (activeStay != null) {
            return new PublicTrackingResponseDto.PublicHorseDto(line.getHorse().getName(), holder, activeStay.getStayType(), null,
                    activeStay.getLocation().getName(), updatedAt);
        }
        HorseLeg moving = horseLegs.stream().filter(leg -> leg.getStatus() == HorseLegStatus.IN_PROGRESS).findFirst().orElse(null);
        return new PublicTrackingResponseDto.PublicHorseDto(line.getHorse().getName(), holder, null,
                moving == null ? null : moving.getLegType(),
                moving == null || moving.getToLocation() == null ? null : moving.getToLocation().getName(), updatedAt);
    }

    private BookingJourneyResponseDto journeyOf(Booking booking) {
        Journey journey = load(booking);
        List<Long> handoverIds = journey.handovers().values().stream().flatMap(List::stream)
                .map(row -> row.getHandover().getId()).distinct().toList();
        Map<Long, List<Document>> evidence = handoverIds.isEmpty() ? Map.of()
                : documents.findHandoverEvidenceForBooking(booking.getId(), handoverIds).stream()
                        .collect(Collectors.groupingBy(document -> document.getHandover().getId()));
        int tolerance = configs.getInt(DeliveryEtaPolicy.TOLERANCE);
        return new BookingJourneyResponseDto(booking.getId(), booking.getCode(), booking.getStatus(),
                journey.lines().stream().map(line -> horseJourney(line, journey, evidence, tolerance)).toList());
    }

    private HorseJourneyResponseDto horseJourney(BookingHorse line, Journey journey,
                                                 Map<Long, List<Document>> evidence, int tolerance) {
        List<HandoverHorse> rows = journey.of(journey.handovers(), line);
        HandoverHorse last = lastDone(rows);
        CustodyHolder holder = holder(last);
        return new HorseJourneyResponseDto(line.getId(), line.getHorse().getName(), line.getHorse().getMicrochip(), line.getStatus(),
                holder, holder == CustodyHolder.PARTY ? last.getHandover().getToParty().getDisplayName() : null,
                last == null ? null : last.getHandover().getHandedAt(),
                journey.of(journey.legs(), line).stream().map(leg -> journeyLeg(leg, tolerance)).toList(),
                journey.of(journey.stays(), line).stream().map(HorseJourneyResponseDto.JourneyStayDto::from).toList(),
                handoversOf(line, journey, evidence));
    }

    // Chặng đã gỡ khỏi lượt xe thì khung giờ không còn đúng
    private static HorseJourneyResponseDto.JourneyLegDto journeyLeg(HorseLeg leg, int tolerance) {
        ExpectedTimes expected = leg.getStatus() == HorseLegStatus.CANCELLED ? new ExpectedTimes(null, null) : CustomerNoticePolicy.expectedTimes(leg);
        var stop = leg.getAlightTripStop();
        var delivery = expected.delivery() == null ? null : CustomerNoticePolicy.window(expected.delivery());
        if (expected.delivery() != null && stop != null && stop.getToldArrivalAt() != null) {
            delivery = DeliveryEtaPolicy.window(stop.getToldArrivalAt(), tolerance);
        }
        return HorseJourneyResponseDto.JourneyLegDto.from(leg,
                expected.pickup() == null ? null : CustomerNoticePolicy.window(expected.pickup()), delivery);
    }

    // Giao nhận đã có dòng của con này, cộng các lần chưa xong ở điểm lên, xuống xe của nó, xếp theo thứ tự lập
    private static List<HorseJourneyResponseDto.JourneyHandoverDto> handoversOf(
            BookingHorse line, Journey journey, Map<Long, List<Document>> evidence) {
        List<HandoverHorse> rows = journey.of(journey.handovers(), line);
        Set<Long> withRow = rows.stream().map(row -> row.getHandover().getId()).collect(Collectors.toSet());
        Stream<HorseJourneyResponseDto.JourneyHandoverDto> scanned = rows.stream()
                .map(row -> HorseJourneyResponseDto.JourneyHandoverDto.from(row.getHandover(), row.getMicrochipMatched(),
                        Boolean.TRUE.equals(row.getMicrochipMatched()) ? evidence.getOrDefault(row.getHandover().getId(), List.of()).stream()
                                .filter(document -> document.getEvidenceHorseLeg() == null
                                        || document.getEvidenceHorseLeg().getId().equals(row.getHorseLeg().getId()))
                                .map(HandoverEvidenceResponseDto::from).toList() : List.of()));
        Stream<HorseJourneyResponseDto.JourneyHandoverDto> open = journey.of(journey.legs(), line).stream()
                .flatMap(leg -> Stream.concat(
                        stopHandovers(journey, leg.getBoardTripStop() == null ? null : leg.getBoardTripStop().getId(), true),
                        stopHandovers(journey, leg.getAlightTripStop() == null ? null : leg.getAlightTripStop().getId(), false)))
                .filter(handover -> !withRow.contains(handover.getId())).distinct()
                .map(handover -> HorseJourneyResponseDto.JourneyHandoverDto.from(handover, null, List.of()));
        return Stream.concat(scanned, open).sorted(Comparator.comparing(HorseJourneyResponseDto.JourneyHandoverDto::id)).toList();
    }

    private static Stream<Handover> stopHandovers(Journey journey, Long stopId, boolean boarding) {
        return stopId == null ? Stream.empty() : journey.openHandovers().getOrDefault(stopId, List.of()).stream()
                .filter(handover -> TripExecutionPolicy.isBoarding(handover.getHandoverType(), handover.getFromParty() != null) == boarding);
    }

    private Journey load(Booking booking) {
        List<BookingHorse> lines = bookingHorses.findByBooking_Id(booking.getId()).stream()
                .sorted(Comparator.comparing(BookingHorse::getId)).toList();
        List<Long> ids = lines.stream().map(BookingHorse::getId).toList();
        List<HorseLeg> allLegs = legs.findByBookingHorse_IdInOrderByBookingHorse_IdAscSequenceNumberAsc(ids);
        List<Long> stopIds = allLegs.stream().flatMap(leg -> Stream.of(leg.getBoardTripStop(), leg.getAlightTripStop()))
                .filter(Objects::nonNull).map(stop -> stop.getId()).distinct().toList();
        return new Journey(lines,
                allLegs.stream().collect(Collectors.groupingBy(leg -> leg.getBookingHorse().getId())),
                stays.findByBookingHorse_IdInOrderById(ids).stream().collect(Collectors.groupingBy(stay -> stay.getBookingHorse().getId())),
                handoverHorses.findByBookingHorseIds(ids).stream().collect(Collectors.groupingBy(row -> row.getHorseLeg().getBookingHorse().getId())),
                stopIds.isEmpty() ? Map.of() : handovers.findByTripStop_IdInOrderById(stopIds).stream()
                        .filter(handover -> handover.getStatus() != HandoverStatus.DONE)
                        .collect(Collectors.groupingBy(handover -> handover.getTripStop().getId())));
    }

    // Lần giao nhận DONE mới nhất mà con ngựa thực sự được chuyển (chip khớp)
    private static HandoverHorse lastDone(List<HandoverHorse> rows) {
        return rows.stream()
                .filter(row -> row.getHandover().getStatus() == HandoverStatus.DONE && Boolean.TRUE.equals(row.getMicrochipMatched()))
                .max(Comparator.comparing((HandoverHorse row) -> row.getHandover().getHandedAt()).thenComparing(row -> row.getHandover().getId()))
                .orElse(null);
    }

    private static CustodyHolder holder(HandoverHorse last) {
        return last == null ? CustodyHolder.SHIPPER
                : TrackingPolicy.holderAfter(last.getHandover().getHandoverType(), last.getHandover().getToParty() != null);
    }
}
