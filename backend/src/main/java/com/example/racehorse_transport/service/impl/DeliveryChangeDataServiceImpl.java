package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailResponseDto.AddressSnapshot;
import com.example.racehorse_transport.dto.booking.DeliveryChangeRequestDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DeliveryChangePolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.DeliveryRouteService.Destination;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class DeliveryChangeDataServiceImpl {
    static final String ACTION = "DELIVERY_ADDRESS_CHANGED";
    private static final Set<HorseLegStatus> LIVE = EnumSet.of(HorseLegStatus.PLANNED, HorseLegStatus.IN_PROGRESS);

    private final BookingRepository bookings;
    private final BookingHorseRepository horses;
    private final HorseLegRepository legs;
    private final CustomerAddressRepository addresses;
    private final HubServiceAreaRepository serviceAreas;
    private final QuotationRepository quotations;
    private final PaymentRepository payments;
    private final AuditLogRepository audit;
    private final SystemConfigService config;
    private final jakarta.persistence.EntityManager entities;

    record Source(Long id, String address, String province, String country, GeoPointDto point) {}
    record Snapshot(Long quotationId, Long hubId, AddressSnapshot previous, Source source,
                    List<Destination> destinations, Instant readyAt, long changeCount) {}
    record Data(Snapshot snapshot, Booking booking, Quotation previous, BookingAddress target, CustomerAddress source,
                List<Payment> payments, List<BookingHorse> active) {}

    void detachReadSnapshot() {
        entities.clear();
    }

    String reason(Booking booking) {
        String reason = DeliveryChangePolicy.bookingReason(booking);
        if (reason != null) return reason;
        reason = DeliveryChangePolicy.paymentReason(payments.findByBooking_IdOrderById(booking.getId()));
        if (reason != null) return reason;
        return remainingReason(booking.getId(), active(booking.getId()), changeCount(booking.getId()));
    }

    private String remainingReason(Long id, List<BookingHorse> active, long count) {
        var assigned = liveLegHorseIds(active);
        if (active.stream().noneMatch(horse -> horse.getDeliveryAddress() != null)) return "NO_DESTINATION";
        if (active.stream().filter(horse -> horse.getDeliveryAddress() != null)
                .allMatch(horse -> assigned.contains(horse.getId()))) return "DELIVERY_PLANNED";
        if (count >= config.getInt("DELIVERY_CHANGE_MAX")) return "LIMIT";
        if (quotations.findByBooking_IdAndStatus(id, QuotationStatus.ACCEPTED).isEmpty()) return "NO_QUOTATION";
        return null;
    }

    Data load(Long id, String username, DeliveryChangeRequestDto request, boolean lock) {
        var booking = (lock ? bookings.findForUpdate(id) : bookings.findByIdAndCustomer_User_Username(id, username))
                .filter(row -> row.getCustomer().getUser().getUsername().equals(username))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        requireAvailable(DeliveryChangePolicy.bookingReason(booking));
        var paid = payments.findByBooking_IdOrderById(id);
        requireAvailable(DeliveryChangePolicy.paymentReason(paid));
        var active = active(id);
        long count = changeCount(id);
        requireAvailable(remainingReason(id, active, count));
        var grouped = active.stream().filter(horse -> horse.getDeliveryAddress() != null).collect(Collectors.groupingBy(
                horse -> horse.getDeliveryAddress().getId(), TreeMap::new, Collectors.toList()));
        var moving = Optional.ofNullable(grouped.get(request.deliveryAddressId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nơi giao đang dùng trong đơn."));
        if (moving.stream().map(BookingHorse::getId).anyMatch(liveLegHorseIds(moving)::contains)) {
            requireAvailable("DELIVERY_PLANNED");
        }
        var target = moving.getFirst().getDeliveryAddress();
        var source = addresses.findByIdAndCustomer_UserId(request.customerAddressId(), booking.getCustomer().getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ trong sổ của khách."));
        var departure = booking.getDeparture();
        var route = departure.getRoute();
        Long hub = route.getDestination().getId();
        var province = source.getProvince().getCode();
        var newHub = serviceAreas.findHubId(province, ActiveStatus.ACTIVE).orElse(null);
        DeliveryChangePolicy.requireDestination(target.getProvince().getCode(), province, hub, newHub,
                grouped.size(), config.getInt("MAX_DELIVERY_PLACES"));
        if (source.getLatitude() == null || source.getLongitude() == null) {
            throw BookingRuleException.conflict("Địa chỉ mới chưa có tọa độ giao ngựa.");
        }
        var newPoint = point(source.getLatitude(), source.getLongitude());
        var destinations = grouped.entrySet().stream().map(entry -> {
            var place = entry.getValue().getFirst().getDeliveryAddress();
            var point = place.getId().equals(target.getId()) ? newPoint : point(place.getLatitude(), place.getLongitude());
            return new Destination(point, entry.getValue().size(), StallSeatPolicy.total(
                    entry.getValue().stream().map(horse -> horse.getStallType().getCode()).toList()));
        }).toList();
        var previous = quotations.findByBooking_IdAndStatus(id, QuotationStatus.ACCEPTED).orElseThrow();
        var departureAt = departure.getRevisedDepartureAt() == null
                ? departure.getPlannedDepartureAt() : departure.getRevisedDepartureAt();
        var snapshot = new Snapshot(previous.getId(), hub, AddressSnapshot.from(target),
                new Source(source.getId(), source.getAddressLine(), province, source.getCountry().getCode(), newPoint),
                destinations, departureAt.plus(route.getEstimatedDurationMinutes(), ChronoUnit.MINUTES), count);
        return new Data(snapshot, booking, previous, target, source, paid, active);
    }

    private List<BookingHorse> active(Long id) {
        return horses.findForDetail(id).stream().filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE).toList();
    }

    private Set<Long> liveLegHorseIds(List<BookingHorse> active) {
        if (active.isEmpty()) return Set.of();
        return legs.findLiveLegHorseIds(active.stream().map(BookingHorse::getId).toList(), HorseLegType.DELIVERY, LIVE);
    }

    private long changeCount(Long id) {
        return audit.countByTargetTableAndTargetIdAndAction("booking", id, ACTION);
    }

    private static GeoPointDto point(java.math.BigDecimal lat, java.math.BigDecimal lng) {
        return new GeoPointDto(lat.doubleValue(), lng.doubleValue());
    }

    private static void requireAvailable(String reason) {
        if (reason == null) return;
        throw BookingRuleException.conflict(switch (reason) {
            case "EXPORT" -> "Đơn xuất khẩu bàn giao cho đại lý, khách không thể đổi nơi giao.";
            case "DIRECT" -> "Đơn chạy thẳng không có hub giao để lập lại chặng giao.";
            case "SELF" -> "Khách tự nhận tại hub nên không có nơi giao để đổi.";
            case "BALANCE_PAID" -> "Đơn đã trả số dư.";
            case "NO_DEPOSIT" -> "Đơn chưa trả cọc.";
            case "DELIVERY_PLANNED" -> "Ngựa tại nơi này đã có chặng giao được lập hoặc đang chạy.";
            case "LIMIT" -> "Đơn đã hết số lần đổi nơi giao.";
            case "NO_HUB" -> "Đơn không có hub giao.";
            case "NO_DESTINATION" -> "Đơn không có nơi giao đang dùng.";
            case "NO_QUOTATION" -> "Đơn không có báo giá đã chấp nhận.";
            default -> "Chỉ đổi nơi giao cho đơn đã xác nhận.";
        });
    }
}
