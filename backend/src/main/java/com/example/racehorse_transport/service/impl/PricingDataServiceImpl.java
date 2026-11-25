package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsRequestDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.PricingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.DepartureRoutePolicy;
import com.example.racehorse_transport.policy.HubResolutionPolicy.Routing;
import com.example.racehorse_transport.policy.HubResolutionPolicy;
import com.example.racehorse_transport.policy.PricingPolicy.*;
import com.example.racehorse_transport.policy.PricingPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.PricingDataService;
import com.example.racehorse_transport.service.PricingRouteService.RoadSegment;
import com.example.racehorse_transport.service.RegulationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.racehorse_transport.service.DeliveryRouteService.Destination;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PricingDataServiceImpl implements PricingDataService {
    private static final String DOMESTIC_COUNTRY = "VN";
    private static final String PREVIEW_CURRENCY = "VND";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PriceListRepository lists;
    private final PriceItemRepository items;
    private final CustomerAddressRepository addresses;
    private final HorseRepository horses;
    private final DepartureRepository departures;
    private final HubServiceAreaRepository serviceAreas;
    private final VehicleClassRepository vehicleClasses;
    private final Clock clock;

    private final RegulationService regulations;
    private final SystemConfigService config;

    @Override
    public PreviewData loadPreview(PricingPreviewRequestDto request, String username) {
        Instant now = clock.instant();
        if (request.pickupAt().isBefore(now)) {
            throw new PricingRuleException("Thời điểm đón phải từ hiện tại trở đi.");
        }
        CustomerAddress pickup = ownedAddress(request.pickupAddressId(), username);
        Map<Long, List<PricingPreviewRequestDto.HorseSelection>> grouped = request.horses().stream()
                .collect(Collectors.groupingBy(horse -> horse.deliveryAddressId() == null
                        ? request.deliveryAddressId() : horse.deliveryAddressId(), LinkedHashMap::new,
                        Collectors.toList()));
        CustomerAddress delivery = ownedAddress(grouped.keySet().iterator().next(), username);
        BookingType type = bookingType(pickup, delivery);
        Selection selection = loadSelection(request, username);
        LocalDateTime pickupTime = LocalDateTime.ofInstant(request.pickupAt(), BUSINESS_ZONE);
        int seats = StallSeatPolicy.total(selection.stalls());
        RoutePlan plan = routePlan(request, pickup, delivery, type, now, seats);
        var regulation = regulations.resolve(type, pickup.getCountry().getCode(), delivery.getCountry().getCode(),
                pickupTime.toLocalDate(), plan.gateId());

        Long vehicleClassId = null;
        int weightTons = 0;
        int topSpeedKmh = 0;
        if (!plan.roadLegs().isEmpty()) {
            vehicleClassId = PricingPolicy.smallestVehicleClass(vehicleClasses.findAll(), seats).getId();
            weightTons = config.getInt("TRUCK_WEIGHT_TONS");
            topSpeedKmh = config.getInt("TRUCK_TOP_SPEED_KMH");
        }
        Map<Long, AddOn> addOns = new TreeMap<>();
        selection.addOnCounts().forEach((id, count) -> addOns.put(id, new AddOn(id, count, count, 0)));
        PricingPolicy.requiredAddOns(regulation.requirements(), items.findPricingCatalog(ActiveStatus.ACTIVE),
                selection.horseCount()).forEach(addOn -> addOns.put(addOn.serviceId(), addOn));
        Route route = plan.departure() == null ? null : plan.departure().getRoute();
        long earlyPickupDays = PricingPolicy.earlyPickupDays(type, pickupTime.toLocalDate(),
                plan.departure() == null ? null : plan.departure().getDepartureDate());
        PricingInput input = new PricingInput(PREVIEW_CURRENCY, LocalDate.ofInstant(now, BUSINESS_ZONE),
                type, route == null ? null : route.getId(), route == null ? null : route.getDistanceKm(),
                List.of(), selection.stalls(), selection.horseCount(), List.copyOf(addOns.values()), earlyPickupDays);
        List<RoadSegment> segments = plan.roadLegs();
        Long deliveryHubId = null;
        List<Destination> destinations = List.of();
        if (grouped.size() > 1) {
            if (type != BookingType.INTERPROVINCE || route == null || request.deliveryMethod() != TransferMethod.COMPANY) {
                throw new PricingRuleException("Chỉ đơn liên tỉnh đi trục do công ty giao mới có nhiều nơi giao. Vui lòng đặt đơn riêng.");
            }
            int limit = config.getInt("MAX_DELIVERY_PLACES");
            if (grouped.size() > limit) {
                throw new PricingRuleException("Đơn có tối đa " + limit + " nơi giao. Vui lòng đặt đơn riêng.");
            }
            deliveryHubId = route.getDestination().getId();
            var deliveryPlaces = new ArrayList<Destination>();
            for (var entry : grouped.entrySet()) {
                CustomerAddress address = ownedAddress(entry.getKey(), username);
                if (!domestic(address) || !deliveryHubId.equals(hubId(hub(address)))) {
                    throw new PricingRuleException("Các nơi giao phải cùng hub đích. Vui lòng đặt đơn riêng.");
                }
                deliveryPlaces.add(new Destination(
                        point(address), entry.getValue().size(), StallSeatPolicy.total(
                                entry.getValue().stream().map(PricingPreviewRequestDto.HorseSelection::stallTypeCode).toList())));
            }
            destinations = List.copyOf(deliveryPlaces);
            segments = segments.stream().filter(segment -> segment.layer() != ServiceLayer.DELIVERY).toList();
        }
        return new PreviewData(input, List.copyOf(segments), vehicleClassId, weightTons, topSpeedKmh, pickupTime, deliveryHubId, destinations, List.copyOf(grouped.keySet()),
                deliveryHubId == null ? null : plan.departure().getPlannedDepartureAt()
                        .plus(Duration.ofMinutes(route.getEstimatedDurationMinutes())));
    }

    @Override
    public TransferData loadTransfers(TransferOptionsRequestDto request, String username) {
        CustomerAddress pickup = ownedAddress(request.pickupAddressId(), username);
        CustomerAddress delivery = ownedAddress(request.deliveryAddressId(), username);
        BookingType type = bookingType(pickup, delivery);
        Location originHub = domestic(pickup) ? hub(pickup) : null;
        Location destinationHub = type == BookingType.LOCAL ? originHub : domestic(delivery) ? hub(delivery) : null;
        boolean direct = HubResolutionPolicy.routingOf(type, hubId(originHub), hubId(destinationHub)) == Routing.DIRECT;
        List<RoadSegment> segments = direct ? List.of()
                : transferSegments(pickup, originHub, delivery, destinationHub, TransferMethod.COMPANY, TransferMethod.COMPANY);
        return new TransferData(segments, config.getInt("TRUCK_WEIGHT_TONS"), config.getInt("TRUCK_TOP_SPEED_KMH"),
                LocalDateTime.ofInstant(request.pickupAt(), BUSINESS_ZONE));
    }

    @Override
    public PricingResult calculate(PricingInput input) {
        PriceList list = PricingPolicy.selectList(
                lists.findByCurrencyCodeAndStatus(input.currencyCode(), PriceListStatus.ACTIVE), input);
        return PricingPolicy.calculate(list, items.findByPriceList_Id(list.getId()),
                items.findPricingCatalog(ActiveStatus.ACTIVE), input);
    }

    private CustomerAddress ownedAddress(Long id, String username) {
        return addresses.findById(id)
                .filter(address -> address.getCustomer().getUser().getUsername().equals(username))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ của khách hàng."));
    }

    private Selection loadSelection(PricingPreviewRequestDto request, String username) {
        Set<Long> selectedIds = new HashSet<>();
        Map<String, Integer> stalls = new HashMap<>();
        Map<Long, Integer> addOns = new HashMap<>();
        Set<String> validStalls = new HashSet<>(items.findStallTypeCodes());
        for (var selected : request.horses()) {
            if (!selectedIds.add(selected.horseId())) {
                throw new PricingRuleException("Một con ngựa chỉ được chọn một lần.");
            }
            Horse horse = horses.findById(selected.horseId())
                    .filter(entry -> entry.getOwner().getUser().getUsername().equals(username))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa của khách hàng."));
            if (horse.getStatus() != HorseStatus.ACTIVE || !validStalls.contains(selected.stallTypeCode())) {
                throw new PricingRuleException("Ngựa hoặc loại khoang không khả dụng.");
            }
            Set<Long> serviceIds = new HashSet<>(selected.addOnServiceIds());
            if (serviceIds.size() != selected.addOnServiceIds().size()) {
                throw new PricingRuleException("Một dịch vụ chỉ được chọn một lần cho mỗi con ngựa.");
            }
            stalls.merge(selected.stallTypeCode(), 1, Integer::sum);
            serviceIds.forEach(serviceId -> addOns.merge(serviceId, 1, Integer::sum));
        }
        return new Selection(stalls, addOns, selectedIds.size());
    }

    private RoutePlan routePlan(PricingPreviewRequestDto request, CustomerAddress pickup, CustomerAddress delivery,
                                BookingType type, Instant now, int stalls) {
        Location originHub = domestic(pickup) ? hub(pickup) : null;
        Location destinationHub = type == BookingType.LOCAL ? originHub : domestic(delivery) ? hub(delivery) : null;
        Routing routing = HubResolutionPolicy.routingOf(type, hubId(originHub), hubId(destinationHub));
        BookingSubmissionPolicy.validateTransfer(type, routing, request.pickupMethod(), request.deliveryMethod());
        if (routing == Routing.DIRECT) {
            if (request.departureId() != null) {
                throw new PricingRuleException("Đơn chạy thẳng không chọn chuyến trục.");
            }
            return new RoutePlan(null, null, List.of(segment(ServiceLayer.DIRECT, point(pickup), point(delivery))));
        }
        if (request.departureId() == null) {
            throw new PricingRuleException("Cần chọn chuyến trục để xem trước giá.");
        }
        Departure departure = departures.findWithRoute(request.departureId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến trục."));
        Route route = departure.getRoute();
        if (departure.getStatus() != DepartureStatus.OPEN || route.getStatus() != ActiveStatus.ACTIVE
                || !now.isBefore(departure.getBookingCutoffAt())
                || !request.pickupAt().isBefore(departure.getPlannedDepartureAt())
                || departure.getStallCapacity() - departure.getBookedStallCount() < stalls) {
            throw new PricingRuleException("Chuyến trục không đủ điều kiện để đặt.", true);
        }
        BookingSubmissionPolicy.validatePickupDate(type, LocalDate.ofInstant(request.pickupAt(), BUSINESS_ZONE),
                departure.getDepartureDate());
        String counterpart = DepartureRoutePolicy.counterpartCountryCode(type, pickup.getCountry().getCode(), delivery.getCountry().getCode());
        if (!DepartureRoutePolicy.matches(route, type, hubId(originHub), hubId(destinationHub), counterpart)) {
            throw new PricingRuleException("Chuyến trục không phù hợp với địa chỉ đã chọn.");
        }
        return new RoutePlan(departure, DepartureRoutePolicy.gateId(route, type),
                transferSegments(pickup, originHub, delivery, destinationHub, request.pickupMethod(), request.deliveryMethod()));
    }

    private List<RoadSegment> transferSegments(CustomerAddress pickup, Location originHub, CustomerAddress delivery, Location destinationHub,
                                               TransferMethod pickupMethod, TransferMethod deliveryMethod) {
        List<RoadSegment> segments = new ArrayList<>();
        if (originHub != null && pickupMethod == TransferMethod.COMPANY) {
            segments.add(segment(ServiceLayer.PICKUP, point(pickup), point(originHub)));
        }
        if (destinationHub != null && deliveryMethod == TransferMethod.COMPANY) {
            segments.add(segment(ServiceLayer.DELIVERY, point(destinationHub), point(delivery)));
        }
        return segments;
    }

    private RoadSegment segment(ServiceLayer layer, GeoPointDto from, GeoPointDto to) {
        return new RoadSegment(layer, from, to, config.getInt("MAX_DRIVE_HOURS_PER_DAY"));
    }

    private Location hub(CustomerAddress address) {
        return serviceAreas.findById_ProvinceCode(province(address)).map(HubServiceArea::getHub)
                .filter(hub -> hub.getStatus() == ActiveStatus.ACTIVE && hub.getLocationType() == LocationType.HUB)
                .orElseThrow(() -> new PricingRuleException("Địa chỉ chưa có hub phục vụ."));
    }

    private static BookingType bookingType(CustomerAddress pickup, CustomerAddress delivery) {
        boolean pickupDomestic = domestic(pickup);
        boolean deliveryDomestic = domestic(delivery);
        String pickupProvince = pickupDomestic ? province(pickup) : null;
        String deliveryProvince = deliveryDomestic ? province(delivery) : null;
        BookingType candidate;
        if (pickupDomestic && deliveryDomestic) {
            candidate = pickupProvince.equals(deliveryProvince) ? BookingType.LOCAL : BookingType.INTERPROVINCE;
        } else {
            candidate = pickupDomestic ? BookingType.EXPORT : BookingType.IMPORT;
        }
        try {
            HubResolutionPolicy.requireConsistentBookingType(candidate, pickup.getCountry().getCode(),
                    delivery.getCountry().getCode(), pickupProvince, deliveryProvince);
        } catch (IllegalArgumentException exception) {
            throw new PricingRuleException(exception.getMessage());
        }
        return candidate;
    }

    private static Long hubId(Location hub) {
        return hub == null ? null : hub.getId();
    }

    private static String province(CustomerAddress address) {
        if (address.getProvince() == null) {
            throw new PricingRuleException("Địa chỉ trong nước cần có tỉnh hoặc thành phố.");
        }
        return address.getProvince().getCode();
    }

    private static boolean domestic(CustomerAddress address) {
        return DOMESTIC_COUNTRY.equals(address.getCountry().getCode());
    }

    private static GeoPointDto point(CustomerAddress address) {
        return point(address.getLatitude(), address.getLongitude());
    }

    private static GeoPointDto point(Location location) {
        return point(location.getLatitude(), location.getLongitude());
    }

    private static GeoPointDto point(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw new PricingRuleException("Thiếu tọa độ để tính quãng đường.");
        }
        return new GeoPointDto(latitude.doubleValue(), longitude.doubleValue());
    }

    private record RoutePlan(Departure departure, Long gateId, List<RoadSegment> roadLegs) {}
    private record Selection(Map<String, Integer> stalls, Map<Long, Integer> addOnCounts, int horseCount) {}
}
