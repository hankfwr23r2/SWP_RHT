package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.HubResolutionPolicy;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.HubServiceAreaRepository;
import com.example.racehorse_transport.repository.ProvinceRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.BookingRoutingService;
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
        BookingSubmissionPolicy.validateDepartureSelection(
                HubResolutionPolicy.routingOf(type, pickupHub, deliveryHub), request.departureId());

        if (request.departureId() != null) {
            Route route = routes.findRouteForSubmission(request.departureId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến."));
            BookingSubmissionPolicy.validateRoute(route, type, pickupHub, deliveryHub);
        }
        return new SubmissionRouting(pickup, delivery, pickupProvince, deliveryProvince, pickupHub, deliveryHub);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Departure lockDeparture(BookingCreateRequestDto request, SubmissionRouting routing) {
        // Khóa chuyến sau ngựa và trước khi INSERT booking lấy khóa FK của chuyến.
        Departure departure = departures.findForUpdate(request.departureId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến."));
        BookingSubmissionPolicy.validateRoute(departure.getRoute(), request.bookingType(), routing.pickupHub(), routing.deliveryHub());
        BookingSubmissionPolicy.validatePickupDate(request.requestedPickupDate(), departure.getDepartureDate());
        return departure;
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
