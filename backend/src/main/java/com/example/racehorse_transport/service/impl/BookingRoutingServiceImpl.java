package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.DepartureRoutePolicy;
import com.example.racehorse_transport.policy.DocumentCompletenessPolicy;
import com.example.racehorse_transport.policy.RegulationPolicy;
import com.example.racehorse_transport.policy.HubResolutionPolicy;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.HubServiceAreaRepository;
import com.example.racehorse_transport.repository.ProvinceRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.BookingRoutingService;
import com.example.racehorse_transport.service.RegulationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingRoutingServiceImpl implements BookingRoutingService {
    private final ProvinceRepository provinces;
    private final HubServiceAreaRepository serviceAreas;
    private final RouteRepository routes;
    private final DepartureRepository departures;
    private final BookingAddressRepository bookingAddresses;
    private final RegulationService regulations;
    private final BookingHorseRepository horses;
    private final DocumentRepository documents;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public boolean requiresDeparture(Booking booking) {
        if (booking.getBookingType() == BookingType.LOCAL) {
            return false;
        }
        BookingHubs hubs = bookingHubs(booking.getId());
        return HubResolutionPolicy.routingOf(booking.getBookingType(), hubs.pickup(), hubs.delivery())
                == HubResolutionPolicy.Routing.LINEHAUL;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Departure lockReplacement(Booking booking, Long replacementId) {
        Long oldId = booking.getDeparture() == null ? null : booking.getDeparture().getId();
        if (oldId != null && oldId < replacementId) {
            lock(oldId);
        }
        Departure replacement = lock(replacementId);
        if (oldId != null && oldId > replacementId) {
            lock(oldId);
        }
        List<BookingAddress> addresses = bookingAddresses.findAddressesForDetail(booking.getId());
        BookingAddress pickup = bookingAddress(addresses, AddressRole.PICKUP);
        BookingAddress delivery = bookingAddress(addresses, AddressRole.DELIVERY);
        BookingHubs hubs = new BookingHubs(hubOf(pickup.getProvince()), hubOf(delivery.getProvince()));
        BookingSubmissionPolicy.validateDepartureSelection(
                HubResolutionPolicy.routingOf(booking.getBookingType(), hubs.pickup(), hubs.delivery()), replacementId);
        Route route = routeForSubmission(replacementId);
        BookingSubmissionPolicy.validateRoute(route, booking.getBookingType(), hubs.pickup(), hubs.delivery(),
                DepartureRoutePolicy.counterpartCountryCode(booking.getBookingType(), pickup.getCountry().getCode(), delivery.getCountry().getCode()));
        BookingSubmissionPolicy.validatePickupDate(booking.getBookingType(), booking.getRequestedPickupDate(), replacement.getDepartureDate());
        Long gate = DepartureRoutePolicy.gateId(route, booking.getBookingType());
        var resolved = regulations.resolve(booking.getBookingType(), pickup.getCountry().getCode(),
                delivery.getCountry().getCode(), booking.getRequestedPickupDate(), gate);
        // Đã cọc thì chỉ tính giấy phải làm lại; chưa cọc thì tính đủ như lúc đặt
        long leadDays = booking.getWaybillNumber() == null ? resolved.leadDays()
                : redoLeadDays(booking, resolved, replacement.getDepartureDate());
        if (!RegulationPolicy.feasible(leadDays, clock.instant(), replacement.getBookingCutoffAt())) {
            throw BookingRuleException.conflict("Không đủ thời gian chuẩn bị giấy tờ cho chuyến mới.");
        }
        return replacement;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireDirectLeadTime(Booking booking, LocalDate pickupDate) {
        if (booking.getBookingType() == BookingType.LOCAL) {
            return;
        }
        List<BookingAddress> addresses = bookingAddresses.findAddressesForDetail(booking.getId());
        var resolved = regulations.resolve(booking.getBookingType(),
                bookingAddress(addresses, AddressRole.PICKUP).getCountry().getCode(),
                bookingAddress(addresses, AddressRole.DELIVERY).getCountry().getCode(), pickupDate, null);
        if (!RegulationPolicy.feasible(redoLeadDays(booking, resolved, pickupDate),
                BookingSubmissionPolicy.businessDate(clock.instant()), pickupDate)) {
            throw BookingRuleException.conflict("Không đủ thời gian làm lại giấy tờ cho ngày đón mới.");
        }
    }

    private long redoLeadDays(Booking booking, RegulationPreviewResponseDto resolved, LocalDate on) {
        var active = horses.findForDetail(booking.getId()).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE).toList();
        return RegulationPolicy.redoLeadDays(resolved.rule(), DocumentCompletenessPolicy.missingTypes(booking.getId(),
                active, resolved.requirements(), documents.findForBookingReview(booking.getId()), on));
    }

    private Departure lock(Long id) {
        return departures.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến."));
    }

    private BookingHubs bookingHubs(Long bookingId) {
        var addresses = bookingAddresses.findAddressesForDetail(bookingId);
        return new BookingHubs(hubOf(bookingAddress(addresses, AddressRole.PICKUP).getProvince()),
                hubOf(bookingAddress(addresses, AddressRole.DELIVERY).getProvince()));
    }

    private static BookingAddress bookingAddress(List<BookingAddress> addresses, AddressRole role) {
        return addresses.stream().filter(row -> row.getAddressType() == role).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ của đơn."));
    }

    private record BookingHubs(Long pickup, Long delivery) {}

    @Override
    @Transactional(readOnly = true)
    public SubmissionRouting resolve(BookingCreateRequestDto request) {
        AddressInput pickup = addressOf(request, AddressRole.PICKUP);
        AddressInput delivery = addressOf(request, AddressRole.DELIVERY);
        Province pickupProvince = province(pickup);
        Province deliveryProvince = province(delivery);
        BookingType type = request.bookingType();
        HubResolutionPolicy.requireConsistentBookingType(type, pickup.countryCode(), delivery.countryCode(),
                pickupProvince.getCode(), deliveryProvince.getCode());
        Long pickupHub = hubOf(pickupProvince);
        Long deliveryHub = hubOf(deliveryProvince);
        HubResolutionPolicy.Routing routing = HubResolutionPolicy.routingOf(type, pickupHub, deliveryHub);
        BookingSubmissionPolicy.validateDepartureSelection(routing, request.departureId());
        BookingSubmissionPolicy.validateTransfer(type, routing, request.pickupMethod(), request.deliveryMethod());

        if (request.departureId() != null) {
            Route route = routeForSubmission(request.departureId());
            BookingSubmissionPolicy.validateRoute(route, type, pickupHub, deliveryHub,
                    DepartureRoutePolicy.counterpartCountryCode(type, pickup.countryCode(), delivery.countryCode()));
        }
        return new SubmissionRouting(pickup, delivery, pickupProvince, deliveryProvince, pickupHub, deliveryHub);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Departure lockDeparture(BookingCreateRequestDto request, SubmissionRouting routing) {
        // Khóa chuyến sau ngựa và trước khi INSERT booking lấy khóa FK của chuyến.
        Departure departure = departures.findForUpdate(request.departureId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến."));
        BookingSubmissionPolicy.validateRoute(departure.getRoute(), request.bookingType(), routing.pickupHub(), routing.deliveryHub(),
                DepartureRoutePolicy.counterpartCountryCode(request.bookingType(), routing.pickup().countryCode(), routing.delivery().countryCode()));
        BookingSubmissionPolicy.validatePickupDate(request.bookingType(), request.requestedPickupDate(), departure.getDepartureDate());
        return departure;
    }

    private Route routeForSubmission(Long departureId) {
        return routes.findRouteForSubmission(departureId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến."));
    }

    private static AddressInput addressOf(BookingCreateRequestDto request, AddressRole type) {
        return request.addresses().stream().filter(address -> address.addressType() == type).findFirst().orElseThrow();
    }

    private Province province(AddressInput input) {
        return provinces.findById(input.provinceCode().trim())
                .filter(province -> input.countryCode().equals(province.getCountry().getCode()))
                .orElseThrow(() -> new BookingRuleException("Tỉnh và quốc gia của địa chỉ không khớp."));
    }

    private Long hubOf(Province province) {
        return serviceAreas.findHubId(province.getCode(), ActiveStatus.ACTIVE).orElse(null);
    }
}
