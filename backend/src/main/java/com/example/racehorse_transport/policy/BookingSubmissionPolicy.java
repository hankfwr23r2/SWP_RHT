package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddOnInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.PartyInput;
import com.example.racehorse_transport.entity.BillableService;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BookingSubmissionPolicy {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static final Set<BookingStatus> ENDED_STATUSES = Set.of(BookingStatus.COMPLETED,
            BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.QUOTE_EXPIRED);

    private BookingSubmissionPolicy() {}

    public static LocalDate businessDate(Instant now) {
        return LocalDate.ofInstant(now, BUSINESS_ZONE);
    }

    public static void validate(BookingCreateRequestDto request, Instant now) {
        if (!Boolean.TRUE.equals(request.acceptedServiceTerms())) {
            throw new BookingRuleException("Cần đồng ý điều khoản dịch vụ.");
        }
        if (request.parties().size() != BookingPartyRole.values().length
                || !new HashSet<>(request.parties().stream().map(PartyInput::bookingRole).toList())
                .equals(Set.of(BookingPartyRole.values()))) {
            throw new BookingRuleException("Cần khai đúng bốn vai trò trong đơn.");
        }
        if (request.addresses().size() != AddressRole.values().length
                || !new HashSet<>(request.addresses().stream().map(AddressInput::addressType).toList())
                .equals(Set.of(AddressRole.values()))) {
            throw new BookingRuleException("Cần một địa chỉ đón và một địa chỉ giao.");
        }
        requireDistinct(request.horses().stream().map(HorseInput::horseId).toList(), "Ngựa bị trùng trong đơn.");
        for (HorseInput horse : request.horses()) {
            if (horse.addOns() != null) {
                requireDistinct(horse.addOns().stream().map(AddOnInput::serviceId).toList(),
                        "Dịch vụ thêm bị trùng trên một con ngựa.");
            }
            InsuranceDeclarationPolicy.validate(horse.insurance());
        }
        if (request.requestedPickupDate().isBefore(businessDate(now))) {
            throw new BookingRuleException("Ngày đón không được ở quá khứ.");
        }
    }

    private static void requireDistinct(List<Long> ids, String message) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BookingRuleException(message);
        }
    }

    public static void validateDepartureSelection(HubResolutionPolicy.Routing routing, Long departureId) {
        if ((routing == HubResolutionPolicy.Routing.DIRECT) != (departureId == null)) {
            throw new BookingRuleException("Đơn chạy thẳng không chọn chuyến trục. Đơn đi trục phải chọn chuyến.");
        }
    }

    public static void validateRoute(Route route, BookingType type, Long pickupHubId, Long deliveryHubId) {
        if (route.getStatus() != ActiveStatus.ACTIVE || !DepartureRoutePolicy.matches(route, type, pickupHubId, deliveryHubId)) {
            throw new BookingRuleException("Tuyến của chuyến không khớp điểm đón và điểm giao.");
        }
    }

    public static void validatePickupDate(LocalDate pickupDate, LocalDate departureDate) {
        if (pickupDate.isAfter(departureDate)) {
            throw new BookingRuleException("Ngày đón phải trước hoặc cùng ngày xuất phát.");
        }
    }

    public static void validateAddOn(BillableService service) {
        if (service.getStatus() != ActiveStatus.ACTIVE || service.getLayer() != ServiceLayer.ADD_ON) {
            throw new BookingRuleException("Dịch vụ thêm được chọn không khả dụng.");
        }
    }
}
