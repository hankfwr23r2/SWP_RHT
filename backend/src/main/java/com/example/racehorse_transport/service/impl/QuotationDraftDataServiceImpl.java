package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.HubResolutionPolicy.Routing;
import com.example.racehorse_transport.policy.InsuranceReviewPolicy;
import com.example.racehorse_transport.policy.PricingPolicy;
import com.example.racehorse_transport.policy.PricingPolicy.AddOn;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.QuotationPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.BookingRoutingService;
import com.example.racehorse_transport.service.DepartureFleetService;
import com.example.racehorse_transport.service.DeliveryRouteService.Destination;
import com.example.racehorse_transport.service.PricingRouteService.RoadSegment;
import com.example.racehorse_transport.service.RegulationService;
import com.example.racehorse_transport.service.SystemConfigService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuotationDraftDataServiceImpl {
    private final BookingAddressRepository addresses;
    private final BookingHorseRepository horses;
    private final BookingHorseAddOnRepository addOns;
    private final HorseInsuranceRepository insurances;
    private final BillableServiceRepository catalog;
    private final DepartureFleetService fleet;
    private final BookingRoutingService bookingRouting;
    private final RegulationService regulations;
    private final SystemConfigService configs;
    private final EntityManager entityManager;

    PriceList priceListReference(Long id) {
        return entityManager.getReference(PriceList.class, id);
    }

    Long smallestClass(List<BookingHorse> lines) {
        return PricingPolicy.smallestVehicleClass(fleet.vehicleClasses(),
                StallSeatPolicy.total(lines.stream().map(line -> line.getStallType().getCode()).toList())).getId();
    }

    boolean addOnCompleted(QuotationLine line) {
        return line.getBillableService() != null && addOns.existsByIdAndCompletedAtIsNotNull(
                new BookingHorseAddOnId(line.getBookingHorse().getId(), line.getBillableService().getId()));
    }

    BigDecimal remainingPremium(QuotationLine line) {
        return insurances.findByBookingHorse_Id(line.getBookingHorse().getId())
                .map(insurance -> com.example.racehorse_transport.policy.PickupMissPricingPolicy.remainingPremium(
                        insurance, line.getUnitPrice()))
                .orElseGet(line::getUnitPrice);
    }

    List<BookingHorseAddOn> pickupAddOns(Long horseId) {
        return addOns.findAddOnsForDetail(List.of(horseId));
    }

    BookingHorseAddOn completePickupAddOn(Long horseId, Long serviceId, Instant at) {
        var addOn = addOns.findById(new BookingHorseAddOnId(horseId, serviceId))
                .orElseThrow(() -> BookingRuleException.conflict("Dịch vụ không thuộc ngựa trong đơn."));
        if (addOn.getCompletedAt() == null) addOn.setCompletedAt(at);
        return addOn;
    }

    List<BookingHorse> remainingAfter(BookingHorse excluded) {
        return activeHorses(excluded.getBooking()).stream()
                .filter(line -> !line.getId().equals(excluded.getId())).toList();
    }

    record HorseRow(Long id, String stallType, Set<Long> services) {}
    record InsuranceQuote(Long horseId, String horseName, BigDecimal premium, String currency, Instant expiresAt) {}
    record DraftData(PricingInput input, List<HorseRow> horses, List<InsuranceQuote> insurance,
                     List<RoadSegment> roads, Long deliveryHubId, List<Destination> destinations, LocalDateTime pickupTime, LocalDate departureDate,
                     Instant plannedDeparture, int linehaulMinutes, Long vehicleClassId, int weight, int speed) {}

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public DraftData load(Booking booking, Instant now) {
        var allHorses = horses.findForDetail(booking.getId());
        var active = allHorses.stream().filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE).toList();
        if (active.isEmpty()) {
            throw BookingRuleException.conflict("Đơn không còn ngựa để lập báo giá.");
        }
        var savedAddresses = addresses.findAddressesForDetail(booking.getId());
        var pickup = address(savedAddresses, AddressRole.PICKUP);
        var defaultDelivery = address(savedAddresses, AddressRole.DELIVERY);
        var byDelivery = active.stream().collect(Collectors.groupingBy(
                horse -> horse.getDeliveryAddress() == null ? defaultDelivery : horse.getDeliveryAddress(),
                LinkedHashMap::new, Collectors.toList()));
        var delivery = byDelivery.keySet().iterator().next();
        var type = booking.getBookingType();
        var departure = booking.getDeparture();
        boolean linehaul = bookingRouting.requiresDeparture(booking);
        if (linehaul != (departure != null)) {
            throw BookingRuleException.conflict("Chuyến trục chưa khớp với điểm đón và giao đã lưu.");
        }
        var route = departure == null ? null : departure.getRoute();
        BookingSubmissionPolicy.validateTransfer(type, linehaul ? Routing.LINEHAUL : Routing.DIRECT,
                booking.getPickupMethod(), booking.getDeliveryMethod());
        LocalDate pickupDate = booking.getRequestedPickupDate();
        LocalDate departureDate = departure == null ? pickupDate : departure.getDepartureDate();
        LocalDate today = LocalDate.ofInstant(now, QuotationPolicy.BUSINESS_ZONE);
        if (pickupDate.isBefore(today)) {
            throw BookingRuleException.conflict("Ngày đón đã qua.");
        }
        if (linehaul) {
            BookingSubmissionPolicy.validatePickupDate(type, pickupDate, departureDate);
            if (route.getStatus() != ActiveStatus.ACTIVE || !now.isBefore(departure.getPlannedDepartureAt())) {
                throw BookingRuleException.conflict("Chuyến trục đã qua giờ xuất phát hoặc tuyến không còn hoạt động.");
            }
        }
        var regulation = regulations.resolve(type, pickup.getCountry().getCode(), delivery.getCountry().getCode(),
                pickupDate, regulations.gateOf(route, type));

        Map<Long, Set<Long>> selections = new HashMap<>();
        Set<Long> bookingServices = new TreeSet<>();
        for (var row : addOns.findAddOnsForDetail(allHorses.stream().map(BookingHorse::getId).toList())) {
            Long serviceId = row.getId().getBillableServiceId();
            if (row.getBillableService().getBillingUnit() == BillingUnit.PER_BOOKING) {
                bookingServices.add(serviceId);
            } else {
                selections.computeIfAbsent(row.getId().getBookingHorseId(), key -> new TreeSet<>()).add(serviceId);
            }
        }
        var required = PricingPolicy.requiredAddOns(regulation.requirements(),
                catalog.findByStatusOrderByCodeAsc(ActiveStatus.ACTIVE), active.size());
        Map<Long, Integer> counts = new TreeMap<>();
        Map<String, Integer> stalls = new TreeMap<>();
        List<HorseRow> horseRows = new ArrayList<>();
        for (var horse : active) {
            Set<Long> services = new TreeSet<>(selections.getOrDefault(horse.getId(), Set.of()));
            required.forEach(addOn -> services.add(addOn.serviceId()));
            services.forEach(id -> counts.merge(id, 1, Integer::sum));
            String stall = horse.getStallType().getCode();
            stalls.merge(stall, 1, Integer::sum);
            horseRows.add(new HorseRow(horse.getId(), stall, Set.copyOf(services)));
        }

        bookingServices.forEach(serviceId -> counts.put(serviceId, 1));
        var privateVehicle = booking.getTransportMode() == TransportMode.PRIVATE
                ? PricingPolicy.PrivateVehicle.from(linehaul ? fleet.privateVehicleClass(booking.getId())
                        : PricingPolicy.smallestVehicleClass(fleet.vehicleClasses(), StallSeatPolicy.total(stalls))) : null;
        var input = new PricingInput("VND", today, type, route == null ? null : route.getId(),
                route == null ? null : route.getDistanceKm(), List.of(), stalls, active.size(),
                counts.entrySet().stream().map(e -> new AddOn(e.getKey(), e.getValue(), e.getValue(), 0)).toList(),
                PricingPolicy.earlyPickupDays(type, pickupDate, linehaul ? departureDate : null), List.of(), privateVehicle);
        var roadSegments = roads(booking, pickup, delivery, route);
        List<Destination> destinations = byDelivery.size() < 2 ? List.of() : byDelivery.entrySet().stream()
                .map(row -> new Destination(point(row.getKey()), row.getValue().size(), StallSeatPolicy.total(
                        row.getValue().stream().map(horse -> horse.getStallType().getCode()).toList()))).toList();
        if (!destinations.isEmpty()) {
            roadSegments = roadSegments.stream().filter(segment -> segment.layer() != ServiceLayer.DELIVERY).toList();
        }
        Long vehicleClass = privateVehicle != null ? privateVehicle.classId() : roadSegments.isEmpty() ? null
                : PricingPolicy.smallestVehicleClass(fleet.vehicleClasses(), StallSeatPolicy.total(stalls)).getId();
        return new DraftData(input, List.copyOf(horseRows), insurance(active, pickupDate, departureDate, now),
                roadSegments, destinations.isEmpty() ? null : route.getDestination().getId(), destinations,
                pickupDate.atStartOfDay(), departureDate,
                departure == null ? null : departure.getPlannedDepartureAt(),
                route == null ? 0 : route.getEstimatedDurationMinutes(), vehicleClass,
                roadSegments.isEmpty() ? 0 : configs.getInt("TRUCK_WEIGHT_TONS"),
                roadSegments.isEmpty() ? 0 : configs.getInt("TRUCK_TOP_SPEED_KMH"));
    }

    record ExclusionDelivery(List<Destination> destinations, Long pickupVehicleClass) {}

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ExclusionDelivery> deliveryAfterExclusion(BookingHorse excluded, boolean companyPickup) {
        var active = remainingAfter(excluded);
        if (active.isEmpty() || excluded.getDeliveryAddress() == null || active.stream()
                .noneMatch(horse -> horse.getDeliveryAddress() != null
                        && !horse.getDeliveryAddress().getId().equals(excluded.getDeliveryAddress().getId()))) {
            return Optional.empty();
        }
        var destinations = active.stream().filter(horse -> horse.getDeliveryAddress() != null).collect(Collectors.groupingBy(
                BookingHorse::getDeliveryAddress, LinkedHashMap::new, Collectors.toList())).entrySet().stream()
                .map(row -> new Destination(point(row.getKey()), row.getValue().size(), StallSeatPolicy.total(
                        row.getValue().stream().map(horse -> horse.getStallType().getCode()).toList()))).toList();
        int originalSeats = destinations.stream().mapToInt(Destination::seats).sum()
                + StallSeatPolicy.seats(excluded.getStallType().getCode());
        Long pickupClass = companyPickup ? PricingPolicy.smallestVehicleClass(fleet.vehicleClasses(), originalSeats).getId() : null;
        return Optional.of(new ExclusionDelivery(destinations, pickupClass));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<InsuranceQuote> insurance(Quotation quotation, Instant now) {
        return insurance(activeHorses(quotation.getBooking()), quotation.getConfirmedPickupDate(),
                quotation.getConfirmedDepartureDate(), now);
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public BookingHorse horseReference(Long id) {
        return horses.getReferenceById(id);
    }

    // Open-in-view có thể giữ entity từ lần đọc. Bỏ chúng để lần khóa sau lộ trình đọc dữ liệu mới.
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void detachReadSnapshot() {
        entityManager.clear();
    }

    private List<BookingHorse> activeHorses(Booking booking) {
        return horses.findForDetail(booking.getId()).stream().filter(h -> h.getStatus() == BookingHorseStatus.ACTIVE).toList();
    }

    private List<InsuranceQuote> insurance(List<BookingHorse> active, LocalDate pickup, LocalDate departure, Instant now) {
        if (active.isEmpty()) {
            return List.of();
        }
        var byHorse = insurances.findForDetail(active.stream().map(BookingHorse::getId).toList()).stream()
                .collect(Collectors.toMap(i -> i.getBookingHorse().getId(), Function.identity()));
        List<InsuranceQuote> result = new ArrayList<>();
        for (BookingHorse row : active) {
            var insurance = byHorse.get(row.getId());
            var name = row.getHorse().getName();
            InsuranceReviewPolicy.validateForQuoting(insurance, name, pickup, departure, now);
            if (insurance.getCoverageOption() == InsuranceCoverageOption.ARRANGED) {
                result.add(new InsuranceQuote(row.getId(), name, insurance.getPremiumAmount(),
                        insurance.getCurrencyCode(), insurance.getQuoteExpiresAt()));
            }
        }
        return List.copyOf(result);
    }

    private List<RoadSegment> roads(Booking booking, BookingAddress pickup, BookingAddress delivery, Route route) {
        int limit = configs.getInt("MAX_DRIVE_HOURS_PER_DAY");
        if (route == null) {
            return List.of(new RoadSegment(ServiceLayer.DIRECT, point(pickup), point(delivery), limit));
        }
        List<RoadSegment> result = new ArrayList<>();
        if (booking.getPickupMethod() == TransferMethod.COMPANY) {
            result.add(new RoadSegment(ServiceLayer.PICKUP, point(pickup), point(route.getOrigin()), limit));
        }
        if (booking.getDeliveryMethod() == TransferMethod.COMPANY) {
            result.add(new RoadSegment(ServiceLayer.DELIVERY, point(route.getDestination()), point(delivery), limit));
        }
        return List.copyOf(result);
    }

    private static BookingAddress address(List<BookingAddress> values, AddressRole role) {
        return values.stream().filter(a -> a.getAddressType() == role).findFirst()
                .orElseThrow(() -> BookingRuleException.conflict("Đơn chưa lưu đủ địa chỉ đón và giao."));
    }

    private static GeoPointDto point(BookingAddress address) {
        return point(address.getLatitude(), address.getLongitude());
    }

    private static GeoPointDto point(Location location) {
        return point(location.getLatitude(), location.getLongitude());
    }

    private static GeoPointDto point(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw BookingRuleException.conflict("Thiếu tọa độ để tính quãng đường.");
        }
        return new GeoPointDto(latitude.doubleValue(), longitude.doubleValue());
    }
}
