package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.PricingRuleException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class PricingPolicy {
    private static final String WIDE_STALL_CODE = "WIDE";

    private PricingPolicy() {}

    public record RoadLeg(ServiceLayer layer, Long vehicleClassId, BigDecimal km) {}

    public record AddOn(Long serviceId, int horseCount, int stallCount, int horseDays) {}

    public record PricingInput(String currencyCode, LocalDate quotationDate, BookingType bookingType,
                               Long routeId, BigDecimal linehaulKm, List<RoadLeg> roadLegs,
                               Map<String, Integer> stallsByType, int horseCount,
                               List<AddOn> addOns, long earlyPickupDays) {
        public PricingInput(String currencyCode, LocalDate quotationDate, BookingType bookingType,
                            Long routeId, BigDecimal linehaulKm, List<RoadLeg> roadLegs,
                            Map<String, Integer> stallsByType, int horseCount, List<AddOn> addOns) {
            this(currencyCode, quotationDate, bookingType, routeId, linehaulKm, roadLegs,
                    stallsByType, horseCount, addOns, 0);
        }

        public PricingInput {
            roadLegs = List.copyOf(roadLegs);
            stallsByType = Collections.unmodifiableMap(new TreeMap<>(stallsByType));
            addOns = List.copyOf(addOns);
        }
    }

    public record Line(Long priceItemId, String serviceCode, BillingUnit billingUnit,
                       Long vehicleClassId, String stallTypeCode, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal amount, boolean passThrough) {}

    public record PricingResult(Long priceListId, String currencyCode, BigDecimal depositRate,
                                List<Line> lines, BigDecimal total) {}

    private record Match(Long routeId, Long vehicleClassId, String stallType, BigDecimal km) {}

    public static long earlyPickupDays(LocalDate requestedPickupDate, LocalDate departureDate) {
        return Math.max(0, ChronoUnit.DAYS.between(requestedPickupDate, departureDate) - 1);
    }

    public static long earlyPickupDays(BookingType type, LocalDate requestedPickupDate, LocalDate departureDate) {
        if (departureDate == null || (type != BookingType.INTERPROVINCE && type != BookingType.EXPORT)) {
            return 0;
        }
        return earlyPickupDays(requestedPickupDate, departureDate);
    }

    public static List<AddOn> requiredAddOns(List<DocumentRequirementResponseDto> requirements,
                                            List<BillableService> catalog, int horseCount) {
        boolean required = requirements.stream().anyMatch(requirement ->
                "LAB_TEST".equals(requirement.documentTypeCode())
                        && requirement.submitterType() == DocumentSubmitter.COMPANY
                        && Boolean.TRUE.equals(requirement.mandatory()));
        if (!required) {
            return List.of();
        }
        BillableService vetCheck = catalog.stream()
                .filter(service -> "VET_CHECK".equals(service.getCode()) && service.getLayer() == ServiceLayer.ADD_ON
                        && service.getStatus() == ActiveStatus.ACTIVE)
                .findFirst().orElseThrow(() -> new PricingRuleException("Thiếu dịch vụ thú y bắt buộc.", true));
        return List.of(new AddOn(vetCheck.getId(), horseCount, horseCount, 0));
    }

    // Công ty chở được một đầu đón, giao hay không. UNKNOWN: bản đồ chưa trả lời được, không chặn khách
    public enum TransferStatus {
        AVAILABLE, TOO_FAR, NO_ROUTE, UNKNOWN;

        public boolean companyAllowed() {
            return this == AVAILABLE || this == UNKNOWN;
        }
    }

    public static void validateDriveTime(ServiceLayer layer, long driveSeconds, int dailyHours) {
        if (!withinDriveTime(layer, driveSeconds, dailyHours)) {
            throw new PricingRuleException(tooFarMessage(layer), true);
        }
    }

    public static TransferStatus transferStatus(ServiceLayer layer, long driveSeconds, int dailyHours) {
        return withinDriveTime(layer, driveSeconds, dailyHours) ? TransferStatus.AVAILABLE : TransferStatus.TOO_FAR;
    }

    // Lý do hiện cho khách. NULL khi chở được
    public static String transferReason(ServiceLayer layer, TransferStatus status) {
        return switch (status) {
            case AVAILABLE -> null;
            case TOO_FAR -> tooFarMessage(layer);
            case NO_ROUTE -> "Không tìm được đường cho xe tải tới địa chỉ này.";
            case UNKNOWN -> "Chưa kiểm được quãng đường. Công ty sẽ kiểm lại khi tính giá.";
        };
    }

    private static boolean withinDriveTime(ServiceLayer layer, long driveSeconds, int dailyHours) {
        long dailySeconds = dailyHours * 3600L;
        return driveSeconds <= (layer == ServiceLayer.DIRECT ? dailySeconds : dailySeconds / 2);
    }

    private static String tooFarMessage(ServiceLayer layer) {
        return switch (layer) {
            case DIRECT -> "Chặng chạy thẳng vượt giới hạn giờ lái trong ngày.";
            case PICKUP -> "Chặng đón vượt giới hạn giờ lái một chiều. Vui lòng tự chở ngựa tới hub.";
            case DELIVERY -> "Chặng giao vượt giới hạn giờ lái một chiều. Vui lòng tự nhận ngựa tại hub.";
            default -> throw new PricingRuleException("Chặng đường tính giá không hợp lệ.");
        };
    }

    public static PriceList selectList(List<PriceList> candidates, PricingInput input) {
        List<PriceList> matches = candidates.stream()
                .filter(list -> list.getStatus() == PriceListStatus.ACTIVE
                        && list.getCurrencyCode().equals(input.currencyCode())
                        && !input.quotationDate().isBefore(list.getValidFrom())
                        && (list.getValidTo() == null || !input.quotationDate().isAfter(list.getValidTo())))
                .toList();
        if (matches.size() != 1) {
            throw new PricingRuleException("Không xác định được một bảng giá đang có hiệu lực.", true);
        }
        return matches.getFirst();
    }

    public static VehicleClass smallestVehicleClass(List<VehicleClass> classes, int stallCount) {
        List<VehicleClass> ordered = classes.stream().filter(type -> type.getMaxStallCount() >= stallCount)
                .sorted(Comparator.comparing(VehicleClass::getMaxStallCount)).toList();
        if (ordered.isEmpty()) {
            throw new PricingRuleException("Không có hạng xe đủ số khoang.");
        }
        if (ordered.size() > 1 && ordered.get(0).getMaxStallCount().equals(ordered.get(1).getMaxStallCount())) {
            throw new PricingRuleException("Không xác định được hạng xe nhỏ nhất duy nhất.", true);
        }
        return ordered.getFirst();
    }

    public static PricingResult calculate(PriceList list, List<PriceItem> items,
                                          List<BillableService> catalog, PricingInput input) {
        validateInput(input);
        List<Line> lines = new ArrayList<>();
        for (RoadLeg leg : input.roadLegs()) {
            if (leg.km().signum() > 0) {
                addLayer(lines, list, items, catalog, leg.layer(),
                        new Match(input.routeId(), leg.vehicleClassId(), null, leg.km()), leg.km());
            }
        }
        if (input.routeId() != null) {
            input.stallsByType().forEach((stall, count) -> addLayer(lines, list, items, catalog,
                    ServiceLayer.LINEHAUL, new Match(input.routeId(), null, stall, input.linehaulKm()),
                    BigDecimal.valueOf(count)));
        } else {
            int wideHorseCount = input.stallsByType().getOrDefault(WIDE_STALL_CODE, 0);
            if (wideHorseCount > 0) {
                addLayer(lines, list, items, catalog, ServiceLayer.STALL,
                        new Match(null, null, WIDE_STALL_CODE, null), BigDecimal.valueOf(wideHorseCount));
            }
        }
        BigDecimal roadKm = input.roadLegs().stream().map(RoadLeg::km).reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<Long> roadClasses = new HashSet<>();
        input.roadLegs().forEach(leg -> roadClasses.add(leg.vehicleClassId()));
        if (roadKm.signum() > 0) {
            Long fuelClass = roadClasses.size() == 1 ? roadClasses.iterator().next() : null;
            addLayer(lines, list, items, catalog, ServiceLayer.FUEL,
                    new Match(input.routeId(), fuelClass, null, roadKm), roadKm);
        }
        Match booking = new Match(input.routeId(), null, null, null);
        if (input.earlyPickupDays() > 0) {
            BillableService stay = catalog.stream()
                    .filter(service -> service.getLayer() == ServiceLayer.QUARANTINE
                            && "QUARANTINE_STAY".equals(service.getCode()))
                    .findFirst().orElseThrow(() -> new PricingRuleException("Thiếu dịch vụ lưu chuồng.", true));
            BigDecimal quantity = BigDecimal.valueOf(input.horseCount()).multiply(BigDecimal.valueOf(input.earlyPickupDays()));
            lines.add(line(list, items, stay, booking, quantity));
        }
        if (input.bookingType() != BookingType.LOCAL) {
            for (BillableService service : catalog) {
                if (service.getLayer() == ServiceLayer.DOCS && Boolean.TRUE.equals(service.getAutoApplied())) {
                    lines.add(line(list, items, service, booking, BigDecimal.ONE));
                }
            }
        }
        for (AddOn selected : input.addOns().stream().sorted(Comparator.comparing(AddOn::serviceId)).toList()) {
            BillableService service = catalog.stream()
                    .filter(entry -> entry.getStatus() == ActiveStatus.ACTIVE
                            && entry.getLayer() == ServiceLayer.ADD_ON && entry.getId().equals(selected.serviceId()))
                    .findFirst().orElseThrow(() -> new PricingRuleException("Dịch vụ thêm không khả dụng."));
            BigDecimal quantity = switch (service.getBillingUnit()) {
                case PER_BOOKING -> BigDecimal.ONE;
                case PER_HORSE -> BigDecimal.valueOf(selected.horseCount());
                case PER_STALL -> BigDecimal.valueOf(selected.stallCount());
                case PER_HORSE_DAY -> BigDecimal.valueOf(selected.horseDays());
                case PER_KM -> roadKm;
            };
            if (quantity.signum() <= 0) {
                throw new PricingRuleException("Thiếu số lượng tính phí cho dịch vụ " + service.getCode() + ".");
            }
            Match criteria = service.getBillingUnit() == BillingUnit.PER_KM
                    ? new Match(input.routeId(), null, null, roadKm) : booking;
            lines.add(line(list, items, service, criteria, quantity));
        }
        BigDecimal total = money(lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new PricingResult(list.getId(), list.getCurrencyCode(), list.getDepositRate(), List.copyOf(lines), total);
    }

    private static void validateInput(PricingInput input) {
        if (input.earlyPickupDays() < 0 || input.horseCount() <= 0
                || input.stallsByType().isEmpty() || input.stallsByType().values().stream().anyMatch(count -> count <= 0)) {
            throw new PricingRuleException("Số ngựa, số khoang hoặc số ngày lưu chuồng không hợp lệ.");
        }
        if (input.routeId() != null && (input.linehaulKm() == null || input.linehaulKm().signum() < 0)) {
            throw new PricingRuleException("Thiếu quãng đường chặng trục.");
        }
        Set<ServiceLayer> layers = EnumSet.noneOf(ServiceLayer.class);
        for (RoadLeg leg : input.roadLegs()) {
            boolean validLayer = input.routeId() == null ? leg.layer() == ServiceLayer.DIRECT
                    : leg.layer() == ServiceLayer.PICKUP || leg.layer() == ServiceLayer.DELIVERY;
            if (!validLayer || !layers.add(leg.layer()) || leg.vehicleClassId() == null
                    || leg.km() == null || leg.km().signum() < 0) {
                throw new PricingRuleException("Chặng đường tính giá không hợp lệ.");
            }
        }
        Set<Long> addOnIds = new HashSet<>();
        for (AddOn addOn : input.addOns()) {
            if (addOn.serviceId() == null || !addOnIds.add(addOn.serviceId())) {
                throw new PricingRuleException("Dịch vụ thêm phải được tổng hợp theo mã dịch vụ.");
            }
        }
    }

    public static List<Line> roadCharges(PriceList list, List<PriceItem> items, List<BillableService> catalog,
                                         ServiceLayer layer, Long routeId, Long classId, BigDecimal km) {
        List<Line> result = new ArrayList<>();
        var match = new Match(routeId, classId, null, km);
        addLayer(result, list, items, catalog, layer, match, km);
        addLayer(result, list, items, catalog, ServiceLayer.FUEL, match, km);
        return result;
    }

    public static Line repriceRoad(PriceList list, List<PriceItem> items, BillableService service,
                                   Long routeId, Long classId, BigDecimal km) {
        return line(list, items, service, new Match(routeId, classId, null, km), km);
    }

    private static void addLayer(List<Line> lines, PriceList list, List<PriceItem> items,
                                 List<BillableService> catalog, ServiceLayer layer, Match match, BigDecimal quantity) {
        List<BillableService> services = catalog.stream().filter(service -> service.getLayer() == layer).toList();
        if (services.size() != 1) {
            throw new PricingRuleException("Không xác định được một dịch vụ tính phí cho " + layer + ".", true);
        }
        lines.add(line(list, items, services.getFirst(), match, quantity));
    }

    private static Line line(PriceList list, List<PriceItem> items, BillableService service,
                             Match match, BigDecimal quantity) {
        if (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 2
                || quantity.compareTo(new BigDecimal("100000000")) >= 0) {
            throw new PricingRuleException("Số lượng tính phí vượt quá giới hạn lưu trữ.");
        }
        List<PriceItem> matches = items.stream()
                .filter(item -> item.getBillableService().getId().equals(service.getId()) && matches(item, match))
                .sorted(Comparator.comparingInt(PricingPolicy::specificity).reversed())
                .toList();
        if (matches.isEmpty()) {
            throw new PricingRuleException("Thiếu dòng giá cho dịch vụ " + service.getCode() + ".", true);
        }
        PriceItem item = matches.getFirst();
        if (matches.size() > 1 && specificity(item) == specificity(matches.get(1))) {
            throw new PricingRuleException("Có nhiều dòng giá cùng mức ưu tiên cho " + service.getCode() + ".", true);
        }
        boolean passThrough = Boolean.TRUE.equals(service.getPassThrough());
        BigDecimal rate = switch (service.getLayer()) {
            case DIRECT, PICKUP, DELIVERY, LINEHAUL -> passThrough ? BigDecimal.ZERO : list.getMarginRate();
            case FUEL -> list.getFuelBufferRate();
            default -> BigDecimal.ZERO;
        };
        // Đơn giá nguyên đồng nên dòng giá và tổng không có lẻ đồng (VNPAY chỉ nhận số nguyên đồng)
        BigDecimal unitPrice = money(item.getUnitPrice().multiply(BigDecimal.ONE.add(rate)).setScale(0, RoundingMode.HALF_UP));
        BigDecimal amount = money(quantity.multiply(unitPrice));
        return new Line(item.getId(), service.getCode(), service.getBillingUnit(), match.vehicleClassId(),
                match.stallType(), quantity, unitPrice, amount, passThrough);
    }

    private static boolean matches(PriceItem item, Match match) {
        return (item.getRoute() == null || Objects.equals(item.getRoute().getId(), match.routeId()))
                && (item.getVehicleClass() == null || Objects.equals(item.getVehicleClass().getId(), match.vehicleClassId()))
                && (item.getStallType() == null || Objects.equals(item.getStallType().getCode(), match.stallType()))
                && (item.getKmFrom() == null || match.km() != null && match.km().compareTo(item.getKmFrom()) >= 0)
                && (item.getKmTo() == null || match.km() != null && match.km().compareTo(item.getKmTo()) < 0);
    }

    private static int specificity(PriceItem item) {
        int score = 0;
        if (item.getRoute() != null) score++;
        if (item.getVehicleClass() != null) score++;
        if (item.getStallType() != null) score++;
        if (item.getKmFrom() != null || item.getKmTo() != null) score++;
        return score;
    }

    private static BigDecimal money(BigDecimal value) {
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        if (rounded.precision() > 15) {
            throw new PricingRuleException("Số tiền vượt quá giới hạn lưu trữ.");
        }
        return rounded;
    }
}
