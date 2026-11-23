package com.example.racehorse_transport.dto.network;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.DepartureSchedule;
import com.example.racehorse_transport.entity.DepartureScheduleDay;
import com.example.racehorse_transport.entity.DepartureScheduleDayId;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import com.example.racehorse_transport.entity.enums.ScheduleStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// Chuyển đổi Entity <-> DTO của danh mục mạng lưới: đúng field, và không gán field bị cấm (id, trạng thái).
class NetworkDtoTest {

    private static Country vn() {
        Country country = new Country();
        country.setCode("VN");
        country.setName("Việt Nam");
        country.setDefaultCurrencyCode("VND");
        return country;
    }

    private static Location location(long id, String code, LocationType type) {
        Location location = new Location();
        location.setId(id);
        location.setCode(code);
        location.setName("Điểm " + code);
        location.setLocationType(type);
        location.setCountry(vn());
        location.setAddress("Địa chỉ");
        location.setStatus(ActiveStatus.ACTIVE);
        return location;
    }

    @Test
    void locationRequestNormalizesCodeAndLeavesIdAndStatusToService() {
        LocationRequestDto request = new LocationRequestDto();
        request.setCode(" hub-test ");
        request.setName(" Hub thử ");
        request.setLocationType(LocationType.HUB);
        request.setCountryCode("VN");
        request.setAddress(" Số 1 ");
        request.setLatitude(new BigDecimal("10.5"));
        request.setLongitude(new BigDecimal("106.5"));
        request.setContactPhone("  ");
        Province province = new Province();
        province.setCode("VN-79");

        Location entity = request.toEntity(vn(), province, null);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getStatus()).isNull();
        assertThat(entity.getCode()).isEqualTo("HUB-TEST");
        assertThat(entity.getName()).isEqualTo("Hub thử");
        assertThat(entity.getAddress()).isEqualTo("Số 1");
        assertThat(entity.getProvince()).isSameAs(province);
        assertThat(entity.getContactPhone()).isNull();
    }

    @Test
    void locationResponseCopiesFields() {
        Location entity = location(5L, "GATE-X", LocationType.BORDER_GATE);
        Country counterpart = new Country();
        counterpart.setCode("KH");
        entity.setCounterpartCountry(counterpart);
        entity.setOpeningTime(LocalTime.of(7, 0));

        LocationResponseDto dto = LocationResponseDto.from(entity);

        assertThat(dto.getId()).isEqualTo(5L);
        assertThat(dto.getCountryCode()).isEqualTo("VN");
        assertThat(dto.getProvinceCode()).isNull();
        assertThat(dto.getCounterpartCountryCode()).isEqualTo("KH");
        assertThat(dto.getOpeningTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(dto.getStatus()).isEqualTo(ActiveStatus.ACTIVE);
    }

    @Test
    void routeRequestBuildsRouteAndStopsWithoutIdOrStatus() {
        RouteRequestDto request = new RouteRequestDto();
        request.setCode("hcm-x");
        request.setName("Tuyến thử");
        request.setDistanceKm(new BigDecimal("75.00"));
        request.setEstimatedDurationMinutes(120);
        Location origin = location(1L, "HUB-A", LocationType.HUB);
        Location destination = location(2L, "GATE-B", LocationType.BORDER_GATE);

        Route route = request.toEntity(origin, destination);

        assertThat(route.getId()).isNull();
        assertThat(route.getStatus()).isNull();
        assertThat(route.getCode()).isEqualTo("HCM-X");
        assertThat(route.getOrigin()).isSameAs(origin);

        route.setId(9L);
        RouteStopRequestDto stopRequest = new RouteStopRequestDto();
        stopRequest.setSequenceNumber(2);
        stopRequest.setLocationId(2L);
        stopRequest.setStopType(RouteStopType.BORDER);
        stopRequest.setArrivalOffsetMinutes(120);
        stopRequest.setDwellMinutes(60);
        RouteStop stop = stopRequest.toEntity(route, destination);
        assertThat(stop.getId().getRouteId()).isEqualTo(9L);
        assertThat(stop.getId().getSequenceNumber()).isEqualTo(2);

        RouteResponseDto response = RouteResponseDto.from(route, List.of(stop));
        assertThat(response.getOriginCode()).isEqualTo("HUB-A");
        assertThat(response.getStops()).singleElement().satisfies(s -> {
            assertThat(s.getLocationCode()).isEqualTo("GATE-B");
            assertThat(s.getLocationType()).isEqualTo(LocationType.BORDER_GATE);
        });
        assertThat(RouteResponseDto.from(route, null).getStops()).isNull();
    }

    @Test
    void scheduleRequestLeavesStatusAndDaysToService() {
        DepartureScheduleRequestDto request = new DepartureScheduleRequestDto();
        request.setRouteId(9L);
        request.setDepartureTime(LocalTime.of(6, 0));
        request.setBookingCutoffHours(72);
        request.setStallCapacity(9);
        request.setValidFrom(LocalDate.of(2026, 12, 1));
        request.setDaysOfWeek(Set.of((short) 4, (short) 1));
        Route route = new Route();
        route.setId(9L);
        route.setCode("HCM-X");

        DepartureSchedule schedule = request.toEntity(route);
        assertThat(schedule.getId()).isNull();
        assertThat(schedule.getStatus()).isNull();
        assertThat(schedule.getStallCapacity()).isEqualTo(9);

        schedule.setId(3L);
        schedule.setStatus(ScheduleStatus.ACTIVE);
        DepartureScheduleDay thursday = new DepartureScheduleDay();
        thursday.setId(new DepartureScheduleDayId(3L, (short) 4));
        DepartureScheduleDay monday = new DepartureScheduleDay();
        monday.setId(new DepartureScheduleDayId(3L, (short) 1));
        assertThat(DepartureScheduleResponseDto.from(schedule, List.of(thursday, monday)).getDaysOfWeek())
                .containsExactly((short) 1, (short) 4);
    }
}
