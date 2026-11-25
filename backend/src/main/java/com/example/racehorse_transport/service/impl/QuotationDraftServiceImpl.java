package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.quotation.QuotationResponseDto;
import com.example.racehorse_transport.dto.booking.DeliveryChangeResponseDto;
import com.example.racehorse_transport.policy.DeliveryChangePolicy;
import com.example.racehorse_transport.dto.operation.PickupAddOnResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.PricingPolicy;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.PricingResult;
import com.example.racehorse_transport.policy.PricingPolicy.RoadLeg;
import com.example.racehorse_transport.policy.QuotationPolicy;
import com.example.racehorse_transport.policy.PickupMissPricingPolicy;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.PricingRouteService;
import com.example.racehorse_transport.service.PricingService;
import com.example.racehorse_transport.service.QuotationDraftService;
import com.example.racehorse_transport.service.impl.QuotationDraftDataServiceImpl.DraftData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class QuotationDraftServiceImpl implements QuotationDraftService {
    private final QuotationDraftDataServiceImpl snapshots;
    private final BookingRepository bookings;
    private final QuotationRepository quotations;
    private final QuotationLineRepository lines;
    private final PaymentRepository payments;
    private final PriceItemRepository priceItems;
    private final PricingService pricing;
    private final PricingRouteService maps;
    private final PlatformTransactionManager transactions;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public QuotationResponseDto create(Long bookingId) {
        var read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
        DraftData data = read.execute(tx -> {
            var snapshot = snapshots.load(requireQuoting(bookings.findById(bookingId)), clock.instant());
            snapshots.detachReadSnapshot();
            return snapshot;
        });
        var estimates = data.roads().stream().map(segment -> {
            var estimate = maps.route(segment, data.pickupTime(), data.weight(), data.speed());
            PricingPolicy.validateDriveTime(segment.layer(), estimate.driveSeconds(), segment.maxDriveHoursPerDay());
            return estimate;
        }).toList();
        List<RoadLeg> roads = new ArrayList<>();
        for (int i = 0; i < estimates.size(); i++) {
            roads.add(new RoadLeg(data.roads().get(i).layer(), data.vehicleClassId(), estimates.get(i).km()));
        }
        var delivery = data.destinations().isEmpty() ? null : pricing.deliveryPricing(data.deliveryHubId(), data.destinations());
        if (delivery != null) roads.add(delivery.loop());
        var loaded = data.input();
        var input = new PricingInput(loaded.currencyCode(), loaded.quotationDate(), loaded.bookingType(), loaded.routeId(),
                loaded.linehaulKm(), roads, loaded.stallsByType(), loaded.horseCount(), loaded.addOns(), loaded.earlyPickupDays(),
                delivery == null ? List.of() : delivery.separate());
        long deliverySeconds = delivery == null ? 0 : delivery.deliverySeconds();
        for (int i = 0; i < data.roads().size(); i++) {
            if (data.roads().get(i).layer() != ServiceLayer.PICKUP) {
                deliverySeconds += estimates.get(i).driveSeconds();
            }
        }
        LocalDate arrival = estimatedDelivery(data, deliverySeconds);
        return new TransactionTemplate(transactions).execute(tx -> {
            var booking = requireQuoting(bookings.findForUpdate(bookingId));
            if (!data.equals(snapshots.load(booking, clock.instant()))) {
                throw BookingRuleException.conflict("Đơn đã thay đổi trong lúc tính lộ trình. Vui lòng tính lại.");
            }
            return saveDraft(booking, data, pricing.calculate(input), arrival);
        });
    }

    private QuotationResponseDto saveDraft(Booking booking, DraftData data, PricingResult result, LocalDate estimatedDelivery) {
        var history = quotations.findByBooking_IdOrderByVersion(booking.getId());
        var quotation = !history.isEmpty() && history.getLast().getStatus() == QuotationStatus.DRAFT
                ? history.getLast() : newDraft(booking, history);
        var oldLines = quotation.getId() == null ? List.<QuotationLine>of()
                : lines.findByQuotation_IdOrderByLineNumber(quotation.getId());
        quotation.setPriceList(snapshots.priceListReference(result.priceListId()));
        quotation.setCurrencyCode(result.currencyCode());
        quotation.setDepositRate(result.depositRate());
        quotation.setConfirmedPickupDate(data.pickupTime().toLocalDate());
        quotation.setConfirmedDepartureDate(data.departureDate());
        quotation.setEstimatedDeliveryDate(estimatedDelivery);
        quotation.setExpiresAt(quotation.getConfirmedPickupDate().plusDays(1)
                .atStartOfDay(QuotationPolicy.BUSINESS_ZONE).toInstant());
        var snapshot = buildLines(quotation, data, result);
        oldLines.stream().filter(line -> line.getLineType() == QuotationLineType.ADJUSTMENT)
                .map(line -> copyLine(line, quotation)).forEach(snapshot::add);
        setTotals(quotation, snapshot);
        quotations.save(quotation);
        if (!oldLines.isEmpty()) {
            lines.deleteAllInBatch(oldLines);
        }
        return QuotationResponseDto.from(quotation, storeLines(snapshot));
    }

    private List<QuotationLine> buildLines(Quotation quotation, DraftData data, PricingResult result) {
        var items = priceItems.findAllById(result.lines().stream().map(PricingPolicy.Line::priceItemId).distinct().toList())
                .stream().collect(Collectors.toMap(PriceItem::getId, Function.identity()));
        List<QuotationLine> snapshot = new ArrayList<>();
        for (var price : result.lines()) {
            var item = items.get(price.priceItemId());
            var service = item.getBillableService();
            var type = lineType(service.getLayer());
            var billedHorses = data.horses().stream().filter(horse -> switch (type) {
                case LINEHAUL, STALL -> Objects.equals(price.stallTypeCode(), horse.stallType());
                case QUARANTINE -> true;
                case SERVICE -> price.billingUnit() != BillingUnit.PER_BOOKING && price.billingUnit() != BillingUnit.PER_KM
                        && horse.services().contains(service.getId());
                default -> false;
            }).toList();
            if (billedHorses.isEmpty()) {
                snapshot.add(line(quotation, type, service, item, null, service.getName(), price.quantity(),
                        price.unitPrice(), price.passThrough()));
            } else {
                BigDecimal quantity = price.quantity().divide(BigDecimal.valueOf(billedHorses.size()));
                for (var horse : billedHorses) {
                    snapshot.add(line(quotation, type, service, item, horse.id(), service.getName(), quantity,
                            price.unitPrice(), price.passThrough()));
                }
            }
        }
        if (!data.insurance().isEmpty()) {
            var service = priceItems.findPricingCatalog(ActiveStatus.ACTIVE).stream()
                    .filter(entry -> "INSURANCE_PREMIUM".equals(entry.getCode())).findFirst()
                    .orElseThrow(() -> BookingRuleException.conflict("Thiếu dịch vụ phí bảo hiểm."));
            for (var insurance : data.insurance()) {
                if (!result.currencyCode().equals(insurance.currency()) || insurance.premium() == null) {
                    throw BookingRuleException.conflict("Phí bảo hiểm của ngựa " + insurance.horseName() + " không hợp lệ.");
                }
                snapshot.add(line(quotation, QuotationLineType.INSURANCE, service, null, insurance.horseId(),
                        service.getName() + " - " + insurance.horseName(), BigDecimal.ONE, insurance.premium(), true));
            }
        }
        return snapshot;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<Instant> validateInsurance(Quotation quotation, Instant now) {
        var current = snapshots.insurance(quotation, now);
        var insured = lines.findByQuotation_IdOrderByLineNumber(quotation.getId()).stream()
                .filter(line -> line.getLineType() == QuotationLineType.INSURANCE)
                .collect(Collectors.toMap(line -> line.getBookingHorse().getId(), Function.identity()));
        if (insured.size() != current.size()) {
            throw BookingRuleException.conflict("Bảo hiểm đã thay đổi. Vui lòng lập lại bản nháp.");
        }
        for (var insurance : current) {
            var line = insured.get(insurance.horseId());
            if (line == null || !quotation.getCurrencyCode().equals(insurance.currency()) || insurance.premium() == null
                    || line.getUnitPrice().compareTo(insurance.premium()) != 0) {
                throw BookingRuleException.conflict("Phí bảo hiểm của ngựa " + insurance.horseName()
                        + " đã thay đổi. Vui lòng lập lại bản nháp.");
            }
        }
        return current.stream().map(QuotationDraftDataServiceImpl.InsuranceQuote::expiresAt).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void setTotals(Quotation quotation, List<QuotationLine> snapshot) {
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw BookingRuleException.conflict("Chỉ được tính tiền cho báo giá nháp.");
        }
        var totals = QuotationPolicy.totals(snapshot, quotation.getDepositRate());
        quotation.setTotalAmount(totals.total());
        quotation.setPassThroughAmount(totals.passThrough());
        quotation.setDepositAmount(totals.deposit());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PickupAddOnResponseDto> pickupAddOns(Long horseId) {
        return snapshots.pickupAddOns(horseId).stream().map(PickupAddOnResponseDto::from).toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PickupAddOnResponseDto completePickupAddOn(Long horseId, Long serviceId, Instant at) {
        return PickupAddOnResponseDto.from(snapshots.completePickupAddOn(horseId, serviceId, at));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Quotation> replaceAfterExclusion(BookingHorse excluded) {
        return replace(excluded, true, null, List.of(), false, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Quotation> replaceAfterPickupChoice(BookingHorse horse, HorseLeg retry, List<BookingHorse> riding,
                                                        boolean waivePickup, boolean exclude, boolean preview) {
        return replace(horse, exclude, retry, riding, waivePickup, preview);
    }

    private Optional<Quotation> replace(BookingHorse horse, boolean exclusion, HorseLeg retry,
                                        List<BookingHorse> riding, boolean waivePickup, boolean preview) {
        var booking = horse.getBooking();
        var accepted = quotations.findByBooking_IdAndStatus(booking.getId(), QuotationStatus.ACCEPTED);
        if (accepted.isEmpty()) {
            return Optional.empty();
        }
        var previous = accepted.get();
        var originalLines = lines.findByQuotation_IdOrderByLineNumber(previous.getId());
        if (retry != null && originalLines.stream().anyMatch(line -> line.getHorseLeg() != null
                && line.getHorseLeg().getVehicleTrip() != null
                && Objects.equals(line.getHorseLeg().getVehicleTrip().getId(), retry.getVehicleTrip().getId()))) {
            return preview ? accepted : Optional.empty();
        }
        if (!exclusion && retry == null && !waivePickup) return preview ? accepted : Optional.empty();
        var replacement = replacementDraft(previous);

        boolean customer = horse.getPickupMissLiability() == PickupMissLiability.CUSTOMER;
        List<QuotationLine> keep = new ArrayList<>();
        for (QuotationLine row : originalLines) {
            if (exclusion && row.isPickupDepositFloor()) continue;
            if (exclusion && row.getBookingHorse() != null && row.getBookingHorse().getId().equals(horse.getId())
                    && !PickupMissPricingPolicy.retainExcludedLine(row, customer,
                            customer && row.getLineType() == QuotationLineType.SERVICE && snapshots.addOnCompleted(row))) {
                continue;
            }
            var copy = copyLine(row, replacement);
            if (exclusion && customer && row.getLineType() == QuotationLineType.INSURANCE
                    && row.getBookingHorse() != null && row.getBookingHorse().getId().equals(horse.getId())) {
                copy.setUnitPrice(snapshots.remainingPremium(row));
                if (copy.getUnitPrice().signum() == 0) continue;
            }
            keep.add(copy);
        }
        boolean deliveryRepriced = exclusion && repriceDeliveryAfterExclusion(horse, replacement, keep);
        if (exclusion && horse.getPickupMissLiability() != null && !customer) {
            repriceRemainingRoads(horse, previous, keep, deliveryRepriced);
        }
        if (retry != null) {
            addRetryCharges(previous, replacement, originalLines, retry, riding, keep);
        }
        if (waivePickup) {
            keep.removeIf(row -> row.getLineType() == QuotationLineType.PICKUP && row.getHorseLeg() == null);
        }
        QuotationPolicy.cappedDiscounts(keep).forEach(QuotationLine::setUnitPrice);
        if (exclusion && customer) {
            var floor = PickupMissPricingPolicy.depositFloorAdjustment(
                    QuotationPolicy.totals(keep, replacement.getDepositRate()).total(),
                    PaymentPolicy.paidDeposit(payments.findByBooking_IdOrderById(booking.getId())));
            if (floor.signum() > 0) {
                var adjustment = new QuotationLine();
                adjustment.setQuotation(replacement);
                adjustment.setLineType(QuotationLineType.ADJUSTMENT);
                adjustment.setDescription("Điều chỉnh theo mức cọc đã trả");
                adjustment.setQuantity(BigDecimal.ONE);
                adjustment.setUnit(BillingUnit.PER_BOOKING.name());
                adjustment.setUnitPrice(floor);
                adjustment.setPassThrough(false);
                adjustment.setPickupDepositFloor(true);
                keep.add(adjustment);
            }
        }
        setTotals(replacement, keep);
        replacement.setDepositAmount(previous.getDepositAmount().max(replacement.getPassThroughAmount())
                .min(replacement.getTotalAmount()));
        if (preview) return Optional.of(replacement);
        acceptReplacement(previous, replacement, keep);
        return Optional.of(replacement);
    }

    private Quotation replacementDraft(Quotation previous) {
        var booking = previous.getBooking();
        var replacement = newDraft(booking, quotations.findByBooking_IdOrderByVersion(booking.getId()));
        replacement.setPriceList(previous.getPriceList());
        replacement.setCurrencyCode(previous.getCurrencyCode());
        replacement.setDepositRate(previous.getDepositRate());
        replacement.setConfirmedPickupDate(previous.getConfirmedPickupDate());
        replacement.setConfirmedDepartureDate(previous.getConfirmedDepartureDate());
        replacement.setEstimatedDeliveryDate(previous.getEstimatedDeliveryDate());
        replacement.setExpiresAt(previous.getExpiresAt());
        return replacement;
    }

    private void acceptReplacement(Quotation previous, Quotation replacement, List<QuotationLine> rows) {
        quotations.save(replacement);
        storeLines(rows);

        QuotationPolicy.requireTransition(previous.getStatus(), QuotationStatus.SUPERSEDED);
        previous.setStatus(QuotationStatus.SUPERSEDED);
        quotations.flush();
        replacement.setSentAt(previous.getSentAt());
        replacement.setSentBy(previous.getSentBy());
        replacement.setAcceptedAt(clock.instant());
        QuotationPolicy.requireTransition(replacement.getStatus(), QuotationStatus.ACCEPTED);
        replacement.setStatus(QuotationStatus.ACCEPTED);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DeliveryReplacement replaceDelivery(Quotation previous, List<BookingHorse> active,
            List<Payment> entries, com.example.racehorse_transport.service.DeliveryRouteService.DeliveryPricing delivery,
            LocalDate date, BigDecimal expected) {
        var booking = previous.getBooking();
        var replacement = replacementDraft(previous);
        replacement.setEstimatedDeliveryDate(date);
        var original = lines.findByQuotation_IdOrderByLineNumber(previous.getId());
        var list = previous.getPriceList();
        var items = priceItems.findByPriceList_Id(list.getId());
        var itemById = items.stream().collect(Collectors.toMap(PriceItem::getId, Function.identity()));
        var catalog = items.stream().map(PriceItem::getBillableService).distinct().toList();
        List<RoadLeg> roads = new ArrayList<>();
        original.stream().filter(row -> row.getLineType() == QuotationLineType.PICKUP
                && row.getHorseLeg() == null && BillingUnit.PER_KM.name().equals(row.getUnit()))
                .findFirst().ifPresent(row -> {
                    var item = row.getPriceItem();
                    Long vehicleClass = item != null && item.getVehicleClass() != null
                            ? item.getVehicleClass().getId() : snapshots.smallestClass(active);
                    roads.add(new RoadLeg(ServiceLayer.PICKUP, vehicleClass, row.getQuantity()));
                });
        roads.add(delivery.loop());
        var priced = PricingPolicy.chooseDeliveryPricing(list, items, catalog, booking.getDeparture().getRoute().getId(),
                roads, delivery.separate());
        List<QuotationLine> updated = new ArrayList<>(original.stream()
                .filter(row -> !DeliveryChangePolicy.replaceLine(row)).map(row -> copyLine(row, replacement)).toList());
        for (var price : priced.lines()) {
            var item = itemById.get(price.priceItemId());
            var service = item.getBillableService();
            if (service.getLayer() != ServiceLayer.PICKUP && price.billingUnit() == BillingUnit.PER_KM) {
                updated.add(line(replacement, lineType(service.getLayer()), service, item, null,
                        service.getName(), price.quantity(), price.unitPrice(), price.passThrough()));
            }
        }
        setTotals(replacement, updated);
        replacement.setDepositAmount(previous.getDepositAmount().min(replacement.getTotalAmount())
                .max(replacement.getPassThroughAmount()));
        var paid = previous.getTotalAmount().subtract(PaymentPolicy.outstanding(previous, entries));
        var total = replacement.getTotalAmount();
        var balance = DeliveryChangePolicy.requireBalance(total, paid, expected);
        var preview = new DeliveryChangeResponseDto(DeliveryChangePolicy.deliveryCharge(original),
                DeliveryChangePolicy.deliveryCharge(updated), total.subtract(previous.getTotalAmount()),
                total, balance, date, replacement.getCurrencyCode());
        if (expected != null) acceptReplacement(previous, replacement, updated);
        return new DeliveryReplacement(replacement, preview);
    }

    // Đơn giao nhiều nơi: bỏ một con thì tính lại vòng giao của các con còn lại
    private boolean repriceDeliveryAfterExclusion(BookingHorse excluded, Quotation replacement, List<QuotationLine> linesToKeep) {
        var booking = excluded.getBooking();
        if (booking.getDeparture() == null || booking.getDeliveryMethod() != TransferMethod.COMPANY
                || replacement.getPriceList() == null) return false;
        var pickup = linesToKeep.stream().filter(line -> line.getLineType() == QuotationLineType.PICKUP
                && line.getHorseLeg() == null).findFirst();
        var changed = snapshots.deliveryAfterExclusion(excluded, pickup.isPresent());
        if (changed.isEmpty()) return false;

        var route = booking.getDeparture().getRoute();
        var destinations = changed.get();
        var delivery = pricing.deliveryPricing(route.getDestination().getId(), destinations.destinations());
        List<RoadLeg> roadLegs = new ArrayList<>();
        pickup.ifPresent(line -> roadLegs.add(new RoadLeg(ServiceLayer.PICKUP, destinations.pickupVehicleClass(), line.getQuantity())));
        roadLegs.add(delivery.loop());
        var priceList = replacement.getPriceList();
        var availableItems = priceItems.findByPriceList_Id(priceList.getId());
        var catalog = availableItems.stream().map(PriceItem::getBillableService).distinct().toList();
        var updated = PricingPolicy.chooseRoadPricing(priceList, availableItems, catalog, route.getId(), roadLegs, delivery.separate());
        linesToKeep.removeIf(line -> line.getHorseLeg() == null && (line.getLineType() == QuotationLineType.DELIVERY
                || line.getLineType() == QuotationLineType.FUEL));
        for (var price : updated.lines()) {
            var item = availableItems.stream().filter(candidate -> candidate.getId().equals(price.priceItemId())).findFirst().orElseThrow();
            var service = item.getBillableService();
            if (service.getLayer() != ServiceLayer.PICKUP) {
                linesToKeep.add(line(replacement, lineType(service.getLayer()), service, item, null,
                        service.getName(), price.quantity(), price.unitPrice(), price.passThrough()));
            }
        }
        return true;
    }

    private void repriceRemainingRoads(BookingHorse excluded, Quotation previous, List<QuotationLine> rows,
                                       boolean deliveryRepriced) {
        var remaining = snapshots.remainingAfter(excluded);
        if (remaining.isEmpty() || previous.getPriceList() == null) return;
        var roadTypes = EnumSet.of(QuotationLineType.PICKUP, QuotationLineType.DELIVERY,
                QuotationLineType.DIRECT, QuotationLineType.FUEL);
        if (deliveryRepriced) roadTypes.retainAll(EnumSet.of(QuotationLineType.PICKUP));
        var roadRows = rows.stream().filter(row -> roadTypes.contains(row.getLineType())
                && row.getHorseLeg() == null && row.getPriceItem() != null
                && row.getBillableService().getBillingUnit() == BillingUnit.PER_KM).toList();
        if (roadRows.isEmpty()) return;
        var classId = snapshots.smallestClass(remaining);
        var departure = excluded.getBooking().getDeparture();
        var routeId = departure == null ? null : departure.getRoute().getId();
        var list = previous.getPriceList();
        var items = priceItems.findByPriceList_Id(list.getId());
        for (var row : roadRows) {
            var price = PricingPolicy.repriceRoad(list, items, row.getBillableService(), routeId, classId, row.getQuantity());
            row.setPriceItem(priceItems.getReferenceById(price.priceItemId()));
            row.setUnitPrice(price.unitPrice());
        }
    }

    private void addRetryCharges(Quotation previous, Quotation replacement, List<QuotationLine> originalLines,
                                 HorseLeg retry, List<BookingHorse> riding, List<QuotationLine> result) {
        var pickup = originalLines.stream()
                .filter(row -> row.getLineType() == QuotationLineType.PICKUP && row.getHorseLeg() == null)
                .findFirst().orElseThrow(() -> BookingRuleException.conflict("Thiếu cước đón trong báo giá gốc."));
        var list = previous.getPriceList();
        var items = priceItems.findByPriceList_Id(list.getId());
        var catalog = items.stream().map(PriceItem::getBillableService).distinct().toList();
        var departure = previous.getBooking().getDeparture();
        var prices = PricingPolicy.roadCharges(list, items, catalog, ServiceLayer.PICKUP,
                departure == null ? null : departure.getRoute().getId(), snapshots.smallestClass(riding), pickup.getQuantity());
        for (var charge : prices) {
            var item = items.stream().filter(row -> row.getId().equals(charge.priceItemId())).findFirst().orElseThrow();
            var service = item.getBillableService();
            var row = line(replacement, lineType(service.getLayer()), service, item, null,
                    service.getName() + " - Đón lại", charge.quantity(), charge.unitPrice(), charge.passThrough());
            row.setHorseLeg(retry);
            result.add(row);
        }
    }

    private List<QuotationLine> storeLines(List<QuotationLine> rows) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setLineNumber(i + 1);
        }
        return lines.saveAllAndFlush(rows);
    }

    private static Quotation newDraft(Booking booking, List<Quotation> history) {
        var quotation = new Quotation();
        quotation.setBooking(booking);
        quotation.setVersion(history.isEmpty() ? 1 : history.getLast().getVersion() + 1);
        quotation.setStatus(QuotationStatus.DRAFT);
        return quotation;
    }

    private static QuotationLine copyLine(QuotationLine old, Quotation quotation) {
        var copy = new QuotationLine();
        copy.setQuotation(quotation);
        copy.setBillableService(old.getBillableService());
        copy.setPriceItem(old.getPriceItem());
        copy.setBookingHorse(old.getBookingHorse());
        copy.setHorseLeg(old.getHorseLeg());
        copy.setLineType(old.getLineType());
        copy.setDescription(old.getDescription());
        copy.setQuantity(old.getQuantity());
        copy.setUnit(old.getUnit());
        copy.setUnitPrice(old.getUnitPrice());
        copy.setPassThrough(old.getPassThrough());
        copy.setPickupDepositFloor(old.isPickupDepositFloor());
        copy.setRefundPayment(old.getRefundPayment());
        return copy;
    }

    private QuotationLine line(Quotation quotation, QuotationLineType type, BillableService service, PriceItem item,
                               Long horseId, String description, BigDecimal quantity, BigDecimal price, boolean passThrough) {
        var line = new QuotationLine();
        line.setQuotation(quotation);
        line.setLineType(type);
        line.setBillableService(service);
        line.setPriceItem(item);
        line.setBookingHorse(horseId == null ? null : snapshots.horseReference(horseId));
        line.setDescription(description);
        line.setQuantity(quantity);
        line.setUnit(service.getBillingUnit().name());
        line.setUnitPrice(price);
        line.setPassThrough(passThrough);
        return line;
    }

    private static QuotationLineType lineType(ServiceLayer layer) {
        return switch (layer) {
            case ADD_ON, SURCHARGE -> QuotationLineType.SERVICE;
            case LINEHAUL -> QuotationLineType.LINEHAUL;
            case PICKUP -> QuotationLineType.PICKUP;
            case DELIVERY -> QuotationLineType.DELIVERY;
            case DIRECT -> QuotationLineType.DIRECT;
            case FUEL -> QuotationLineType.FUEL;
            case STALL -> QuotationLineType.STALL;
            case QUARANTINE -> QuotationLineType.QUARANTINE;
            case DOCS -> QuotationLineType.DOCS;
        };
    }

    private static Booking requireQuoting(Optional<Booking> row) {
        var booking = row.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (booking.getStatus() != BookingStatus.QUOTING) {
            throw BookingRuleException.conflict("Chỉ lập bản nháp cho đơn đang báo giá.");
        }
        return booking;
    }

    private static LocalDate estimatedDelivery(DraftData data, long deliverySeconds) {
        if (data.plannedDeparture() == null) {
            return data.pickupTime().plusSeconds(deliverySeconds).toLocalDate();
        }
        return LocalDate.ofInstant(data.plannedDeparture().plusSeconds(data.linehaulMinutes() * 60L + deliverySeconds),
                QuotationPolicy.BUSINESS_ZONE);
    }
}
