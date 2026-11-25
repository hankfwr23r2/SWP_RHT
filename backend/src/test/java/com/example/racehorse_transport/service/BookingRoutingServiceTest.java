package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.HubServiceAreaRepository;
import com.example.racehorse_transport.repository.ProvinceRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.impl.BookingRoutingServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingRoutingServiceTest {

    @Mock private ProvinceRepository provinces;
    @Mock private HubServiceAreaRepository serviceAreas;
    @Mock private RouteRepository routes;
    @Mock private DepartureRepository departures;
    @Mock private BookingAddressRepository bookingAddresses;
    @Mock private RegulationService regulations;
    @Mock private DocumentCompletenessService completeness;
    @Mock private Clock clock;
    @InjectMocks private BookingRoutingServiceImpl routing;

    @Test
    void resolvesDirectBookingWithoutLookingUpDeparture() {
        Province province = servedProvince("VN-79", 1L);
        AddressInput pickup = address(AddressRole.PICKUP, " VN-79 ");
        AddressInput delivery = address(AddressRole.DELIVERY, "VN-79");

        var result = routing.resolve(request(BookingType.LOCAL, null, pickup, delivery));

        assertThat(result.pickup()).isSameAs(pickup);
        assertThat(result.delivery()).isSameAs(delivery);
        assertThat(result.pickupProvince()).isSameAs(province);
        assertThat(result.deliveryProvince()).isSameAs(province);
        verifyNoInteractions(routes, departures);
    }

    @Test
    void rejectsRouteThatNoLongerMatchesAfterDepartureIsLocked() {
        servedProvince("VN-79", 1L);
        servedProvince("VN-48", 2L);
        var request = request(BookingType.INTERPROVINCE, 10L,
                address(AddressRole.PICKUP, "VN-79"), address(AddressRole.DELIVERY, "VN-48"));
        when(routes.findRouteForSubmission(10L)).thenReturn(Optional.of(route(1L, 2L)));
        var resolved = routing.resolve(request);
        assertThat(resolved.pickupHub()).isEqualTo(1L);
        assertThat(resolved.deliveryHub()).isEqualTo(2L);
        verifyNoInteractions(departures);

        Departure departure = new Departure();
        departure.setRoute(route(1L, 3L));
        when(departures.findForUpdate(10L)).thenReturn(Optional.of(departure));

        assertThatThrownBy(() -> routing.lockDeparture(request, resolved))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void locksTheLowerDepartureIdFirstAndChecksPreparationLeadTime() {
        LocalDate pickupDate = LocalDate.of(2027, 1, 1);
        Departure old = new Departure();
        old.setId(20L);
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setBookingType(BookingType.INTERPROVINCE);
        booking.setDeparture(old);
        booking.setRequestedPickupDate(pickupDate);
        Departure replacement = new Departure();
        replacement.setId(10L);
        replacement.setRoute(route(1L, 2L));
        when(routes.findRouteForSubmission(10L)).thenReturn(Optional.of(replacement.getRoute()));
        replacement.setDepartureDate(pickupDate.plusDays(1));
        replacement.setBookingCutoffAt(Instant.parse("2026-12-29T01:00:00Z"));
        when(departures.findForUpdate(10L)).thenReturn(Optional.of(replacement));
        when(departures.findForUpdate(20L)).thenReturn(Optional.of(old));
        when(bookingAddresses.findAddressesForDetail(booking.getId())).thenReturn(List.of(
                storedAddress(AddressRole.PICKUP, "VN-79"), storedAddress(AddressRole.DELIVERY, "VN-48")));
        when(serviceAreas.findHubId("VN-79", ActiveStatus.ACTIVE)).thenReturn(Optional.of(1L));
        when(serviceAreas.findHubId("VN-48", ActiveStatus.ACTIVE)).thenReturn(Optional.of(2L));
        when(regulations.resolve(BookingType.INTERPROVINCE, "VN", "VN", pickupDate, null))
                .thenReturn(new RegulationPreviewResponseDto(null, 5, List.of(), false, null, null));
        when(clock.instant()).thenReturn(Instant.parse("2026-12-28T01:00:00Z"));

        assertThatThrownBy(() -> routing.lockReplacement(booking, 10L))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("chuẩn bị giấy tờ");
        var order = inOrder(departures);
        order.verify(departures).findForUpdate(10L);
        order.verify(departures).findForUpdate(20L);

        // Đã cọc và không còn giấy phải làm lại thì không tính thời gian làm giấy
        booking.setWaybillNumber("RHT-1");
        assertThat(routing.lockReplacement(booking, 10L)).isSameAs(replacement);
    }

    private static BookingAddress storedAddress(AddressRole role, String code) {
        Country country = new Country();
        country.setCode("VN");
        Province province = new Province();
        province.setCode(code);
        BookingAddress address = new BookingAddress();
        address.setAddressType(role);
        address.setProvince(province);
        address.setCountry(country);
        return address;
    }

    private Province servedProvince(String code, Long hubId) {
        Country country = new Country();
        country.setCode("VN");
        Province province = new Province();
        province.setCode(code);
        province.setCountry(country);
        when(provinces.findById(code)).thenReturn(Optional.of(province));
        when(serviceAreas.findHubId(code, ActiveStatus.ACTIVE)).thenReturn(Optional.of(hubId));
        return province;
    }

    private static AddressInput address(AddressRole role, String province) {
        return new AddressInput(role, "Address", province, "VN", BigDecimal.ONE, BigDecimal.ONE,
                "Contact", "0900000000", null, null);
    }

    private static BookingCreateRequestDto request(BookingType type, Long departureId,
                                                    AddressInput pickup, AddressInput delivery) {
        return new BookingCreateRequestDto(type, departureId, LocalDate.of(2027, 1, 1), null,
                List.of(), List.of(pickup, delivery), List.of(), true);
    }

    private static Route route(Long originId, Long destinationId) {
        Location origin = new Location();
        origin.setId(originId);
        Location destination = new Location();
        destination.setId(destinationId);
        Route route = new Route();
        route.setStatus(ActiveStatus.ACTIVE);
        route.setOrigin(origin);
        route.setDestination(destination);
        return route;
    }
}
