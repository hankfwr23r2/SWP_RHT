package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.quotation.QuotationResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.PricingPolicy;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.PricingResult;
import com.example.racehorse_transport.policy.PricingPolicy.RoadLeg;
import com.example.racehorse_transport.policy.QuotationPolicy;
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
    private final PriceListRepository priceLists;
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
        quotation.setPriceList(priceLists.getReferenceById(result.priceListId()));
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
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Quotation> replaceAfterExclusion(BookingHorse excluded) {
        var booking = excluded.getBooking();
        var accepted = quotations.findByBooking_IdAndStatus(booking.getId(), QuotationStatus.ACCEPTED);
        if (accepted.isEmpty()) {
            return Optional.empty();
        }
        var previous = accepted.get();
        var replacement = newDraft(booking, quotations.findByBooking_IdOrderByVersion(booking.getId()));
        replacement.setPriceList(previous.getPriceList());
        replacement.setCurrencyCode(previous.getCurrencyCode());
        replacement.setDepositRate(previous.getDepositRate());
        replacement.setConfirmedPickupDate(previous.getConfirmedPickupDate());
        replacement.setConfirmedDepartureDate(previous.getConfirmedDepartureDate());
        replacement.setEstimatedDeliveryDate(previous.getEstimatedDeliveryDate());
        replacement.setExpiresAt(previous.getExpiresAt());

        List<QuotationLine> keep = new ArrayList<>(lines.findByQuotation_IdOrderByLineNumber(previous.getId()).stream()
                .filter(line -> line.getBookingHorse() == null || !line.getBookingHorse().getId().equals(excluded.getId()))
                .map(line -> copyLine(line, replacement)).toList());
        repriceDeliveryAfterExclusion(excluded, replacement, keep);
        QuotationPolicy.cappedDiscounts(keep).forEach(QuotationLine::setUnitPrice);
        setTotals(replacement, keep);
        quotations.save(replacement);
        storeLines(keep);

        QuotationPolicy.requireTransition(previous.getStatus(), QuotationStatus.SUPERSEDED);
        previous.setStatus(QuotationStatus.SUPERSEDED);
        quotations.flush();
        replacement.setSentAt(previous.getSentAt());
        replacement.setSentBy(previous.getSentBy());
        replacement.setAcceptedAt(clock.instant());
        QuotationPolicy.requireTransition(replacement.getStatus(), QuotationStatus.ACCEPTED);
        replacement.setStatus(QuotationStatus.ACCEPTED);
        return Optional.of(replacement);
    }

    private void repriceDeliveryAfterExclusion(BookingHorse excluded, Quotation replacement, List<QuotationLine> linesToKeep) {
        var booking = excluded.getBooking();
        if (booking.getDeparture() == null || booking.getDeliveryMethod() != TransferMethod.COMPANY) return;
        var pickup = linesToKeep.stream().filter(line -> line.getLineType() == QuotationLineType.PICKUP).findFirst();
        var changed = snapshots.deliveryAfterExclusion(excluded, pickup.isPresent());
        if (changed.isEmpty()) return;

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
        linesToKeep.removeIf(line -> line.getLineType() == QuotationLineType.DELIVERY || line.getLineType() == QuotationLineType.FUEL);
        for (var price : updated.lines()) {
            var item = availableItems.stream().filter(candidate -> candidate.getId().equals(price.priceItemId())).findFirst().orElseThrow();
            var service = item.getBillableService();
            if (service.getLayer() != ServiceLayer.PICKUP) {
                linesToKeep.add(line(replacement, lineType(service.getLayer()), service, item, null,
                        service.getName(), price.quantity(), price.unitPrice(), price.passThrough()));
            }
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
