package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.entity.enums.ServiceLayer;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.PricingPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.PriceItemRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import com.example.racehorse_transport.service.DeliveryRouteService;
import com.example.racehorse_transport.service.ExtraChargeService;
import com.example.racehorse_transport.service.RedeliveryChargeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RedeliveryChargeServiceImpl implements RedeliveryChargeService {
    private final BookingRepository bookings;
    private final HorseStayRepository stays;
    private final HorseLegRepository legs;
    private final CostItemRepository costs;
    private final QuotationRepository quotations;
    private final PriceItemRepository priceItems;
    private final VehicleClassRepository classes;
    private final DeliveryRouteService routes;
    private final ExtraChargeService extras;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void chargeReadyTrip(HorseLeg replacement) {
        var bookingId = replacement.getBookingHorse().getBooking().getId();
        var booking = bookings.findForUpdate(bookingId).orElseThrow();
        var held = stays.findByBookingHorse_Booking_Id(bookingId).stream()
                .filter(stay -> stay.getStayType() == HorseStayType.STABLING && stay.getBalanceHoldAt() == null
                        && stay.getActualEndAt() == null && stay.getActualStartAt() != null)
                .filter(stay -> Objects.equals(stay.getLocation().getId(), replacement.getFromLocation().getId()))
                .filter(stay -> stay.getCollectionReportedAt() == null).toList();
        if (held.isEmpty() || held.stream().anyMatch(stay -> stay.getReadyAt() == null)) return;
        var assigned = held.stream().map(stay ->
                legs.findByBookingHorse_IdOrderBySequenceNumber(stay.getBookingHorse().getId()).stream()
                        .filter(leg -> leg.getStatus() == HorseLegStatus.PLANNED && leg.getReplacedHorseLeg() != null
                                && (stay.getFailedDeliveryLeg() == null
                                || stay.getFailedDeliveryLeg().getId().equals(leg.getReplacedHorseLeg().getId())))
                        .findFirst()).toList();
        if (assigned.stream().anyMatch(java.util.Optional::isEmpty)) return;
        assigned.stream().flatMap(java.util.Optional::stream)
                .collect(Collectors.groupingBy(leg -> leg.getVehicleTrip().getId()))
                .values().forEach(retry -> chargeTrip(booking, retry));
    }

    private void chargeTrip(Booking booking, List<HorseLeg> retry) {
        var trip = retry.getFirst().getVehicleTrip();
        if (costs.existsByBooking_IdAndVehicleTrip_IdAndCategory(booking.getId(), trip.getId(), CostCategory.REDELIVERY)) return;
        var quotation = quotations.findByBooking_IdAndStatus(booking.getId(), QuotationStatus.ACCEPTED)
                .orElseThrow(() -> BookingRuleException.conflict("Đơn chưa có báo giá đã chấp nhận."));
        var items = priceItems.findByPriceList_Id(quotation.getPriceList().getId());
        var catalog = items.stream().map(item -> item.getBillableService()).distinct().toList();
        int seats = retry.stream().mapToInt(this::seats).sum();
        var classId = PricingPolicy.smallestVehicleClass(classes.findAll(), seats).getId();
        var addresses = retry.stream().map(HorseLeg::getToBookingAddress).distinct().toList();
        var points = addresses.stream().map(this::point).toList();
        var counts = addresses.stream().map(address -> (int) retry.stream()
                .filter(leg -> leg.getToBookingAddress().equals(address)).count()).toList();
        var plan = routes.plan(retry.getFirst().getFromLocation().getId(), points, counts, seats);
        var distances = plan.individualKm().stream().map(km -> {
            if (km == null) throw BookingRuleException.conflict("Chưa có tuyến từ hub tới điểm giao lại.");
            return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
        }).toList();
        var routeId = booking.getDeparture() == null ? null : booking.getDeparture().getRoute().getId();
        var amount = PricingPolicy.redeliveryCharge(quotation.getPriceList(), items, catalog, routeId, classId, distances);
        if (amount.signum() <= 0) return;
        var cost = new CostItem();
        cost.setCategory(CostCategory.REDELIVERY);
        cost.setDescription("Cước giao lại và nhiên liệu");
        cost.setAmount(amount);
        cost.setCurrencyCode(quotation.getCurrencyCode());
        cost.setPayerType(CostPayerType.CONSIGNEE);
        cost.setBooking(booking);
        cost.setVehicleTrip(trip);
        cost.setIncurredAt(clock.instant());
        cost.setApprovedAt(cost.getIncurredAt());
        extras.createStablingCharge(booking.getId(), costs.saveAndFlush(cost).getId());
    }

    private int seats(HorseLeg leg) {
        return StallSeatPolicy.seats(leg.getBookingHorse().getStallType().getCode());
    }

    private GeoPointDto point(BookingAddress address) {
        return new GeoPointDto(address.getLatitude().doubleValue(), address.getLongitude().doubleValue());
    }
}
