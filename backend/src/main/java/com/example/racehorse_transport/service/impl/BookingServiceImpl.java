package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.DepartureRoutePolicy;
import com.example.racehorse_transport.policy.RegulationPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.service.BookingDetailsService;
import com.example.racehorse_transport.service.BookingRoutingService.SubmissionRouting;
import com.example.racehorse_transport.service.BookingRoutingService;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.RegulationService;
import com.example.racehorse_transport.util.BookingCodes;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final int CODE_ATTEMPTS = 5;

    private final BookingRepository bookings;
    private final HorseRepository horses;
    private final BookingHorseRepository bookingHorses;
    private final CustomerRepository customers;
    private final DepartureService departureService;
    private final RegulationService regulation;
    private final BookingDetailsService details;
    private final BookingStatusService statusHistory;
    private final BookingRoutingService routing;
    private final Clock clock;

    @Override
    @Transactional
    public BookingResponseDto submit(String username, String ipAddress, BookingCreateRequestDto request) {
        Instant now = clock.instant();
        BookingSubmissionPolicy.validate(request, now);
        Customer customer = customers.findSubmittingCustomer(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ khách hàng."));

        statusHistory.requireSubmissionAllowed(customer.getUserId());
        SubmissionRouting resolved = routing.resolve(request);

        Map<Long, Horse> selectedHorses = lockAvailableHorses(request, customer);
        Departure departure = null;
        if (request.departureId() != null) {
            departure = routing.lockDeparture(request, resolved);
        }
        if (departure != null || request.bookingType() == com.example.racehorse_transport.entity.enums.BookingType.INTERPROVINCE) {
            validateLeadTime(request, resolved.pickup(), resolved.delivery(), departure, clock.instant());
        }

        Booking booking = insertBooking(request, customer, departure, now);
        details.saveParties(request, booking);
        details.saveAddress(resolved.pickup(), resolved.pickupProvince(), booking);
        details.saveAddress(resolved.delivery(), resolved.deliveryProvince(), booking);
        for (HorseInput input : request.horses()) {
            details.saveHorse(input, selectedHorses.get(input.horseId()), booking, ipAddress,
                    BookingSubmissionPolicy.businessDate(now));
        }
        details.acceptServiceTerms(booking, ipAddress);
        if (departure != null) {
            int seats = StallSeatPolicy.total(request.horses().stream().map(HorseInput::stallTypeCode).toList());
            departureService.reserve(departure.getId(), seats);
        }
        statusHistory.recordInitialStatus(booking);
        return BookingResponseDto.from(booking);
    }

    private Map<Long, Horse> lockAvailableHorses(BookingCreateRequestDto request, Customer customer) {
        List<Long> ids = request.horses().stream().map(HorseInput::horseId).sorted().toList();
        List<Horse> locked = horses.findOwnedForUpdate(customer.getUserId(), ids);
        if (locked.size() != ids.size()) {
            throw new ResourceNotFoundException("Không tìm thấy ngựa trong hồ sơ khách hàng.");
        }
        if (locked.stream().anyMatch(horse -> horse.getStatus() != HorseStatus.ACTIVE)) {
            throw BookingRuleException.conflict("Chỉ đặt đơn cho ngựa đang hoạt động.");
        }
        List<String> busy = bookingHorses.findBusyBookingCodes(ids, BookingHorseStatus.ACTIVE,
                BookingSubmissionPolicy.ENDED_STATUSES);
        if (!busy.isEmpty()) {
            throw BookingRuleException.conflict("Ngựa đang có trong đơn " + String.join(", ", busy) + ".");
        }
        return locked.stream().collect(Collectors.toMap(Horse::getId, Function.identity()));
    }


    private void validateLeadTime(BookingCreateRequestDto request, AddressInput pickup, AddressInput delivery,
                                  Departure departure, Instant now) {
        Long gate = departure == null ? null : DepartureRoutePolicy.gateId(departure.getRoute(), request.bookingType());
        long days = regulation.resolve(request.bookingType(), pickup.countryCode(), delivery.countryCode(),
                request.requestedPickupDate(), gate).leadDays();
        if (departure == null) {
            if (!RegulationPolicy.feasible(days, BookingSubmissionPolicy.businessDate(now), request.requestedPickupDate())) {
                throw BookingRuleException.conflict("Không kịp làm giấy trước ngày đón. Cần " + days + " ngày.");
            }
        } else if (!RegulationPolicy.feasible(days, now, departure.getBookingCutoffAt())) {
            throw BookingRuleException.conflict("Không kịp làm giấy trước giờ chốt chuyến. Cần " + days + " ngày.");
        }
    }

    private Booking insertBooking(BookingCreateRequestDto request, Customer customer, Departure departure, Instant now) {
        Booking candidate = request.toEntity(customer, departure);
        candidate.setStatus(BookingStatus.PENDING_INTAKE);
        candidate.setCreatedBy(customer.getUser());
        candidate.setEmergencySpendingLimit(request.emergencySpendingLimit());
        Long departureId = departure == null ? null : departure.getId();
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = BookingCodes.newCode();
            if (bookings.insertIfCodeAvailable(code, customer.getUserId(), candidate.getBookingType().name(),
                    departureId, candidate.getRequestedPickupDate(), candidate.getEmergencySpendingLimit(),
                    candidate.getStatus().name(), candidate.getCreatedBy().getId(), now,
                    candidate.getPickupMethod().name(), candidate.getDeliveryMethod().name()) == 1) {
                return bookings.findByCode(code).orElseThrow();
            }
        }
        throw BookingRuleException.conflict("Chưa cấp được mã đơn. Vui lòng gửi lại.");
    }
}
