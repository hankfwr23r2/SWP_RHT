package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.*;

import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.PricingRuleException;
import com.example.racehorse_transport.policy.PricingPolicy.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingPolicyTest {
    @Test
    void pricesRoadLinehaulDocumentsAndChosenServiceWithoutQuarantine() {
        List<BillableService> catalog = List.of(
                service(1, ServiceLayer.PICKUP, BillingUnit.PER_KM, false),
                service(2, ServiceLayer.DELIVERY, BillingUnit.PER_KM, false),
                service(3, ServiceLayer.LINEHAUL, BillingUnit.PER_STALL, false),
                service(4, ServiceLayer.FUEL, BillingUnit.PER_KM, false),
                service(5, ServiceLayer.QUARANTINE, BillingUnit.PER_HORSE_DAY, false),
                service(6, ServiceLayer.DOCS, BillingUnit.PER_BOOKING, true),
                service(7, ServiceLayer.ADD_ON, BillingUnit.PER_HORSE_DAY, false),
                service(8, ServiceLayer.STALL, BillingUnit.PER_HORSE, false));
        List<PriceItem> items = List.of(item(1, catalog.get(0), "30000", null, null),
                item(2, catalog.get(1), "30000", null, null), item(3, catalog.get(2), "2000000", null, null),
                item(4, catalog.get(3), "9000", null, null), item(5, catalog.get(4), "100000", null, null),
                item(6, catalog.get(5), "123", null, null), item(7, catalog.get(6), "50.55", null, null));
        PricingInput input = new PricingInput("VND", LocalDate.of(2026, 11, 23), BookingType.INTERPROVINCE,
                4L, new BigDecimal("100"), List.of(new RoadLeg(ServiceLayer.PICKUP, 3L, new BigDecimal("35")),
                new RoadLeg(ServiceLayer.DELIVERY, 3L, new BigDecimal("5"))), Map.of("WIDE", 2), 2,
                List.of(new AddOn(7L, 1, 1, 3)));
        PricingResult result = PricingPolicy.calculate(list(), items, catalog, input);
        assertThat(result.lines()).extracting(Line::priceItemId).containsExactly(1L, 2L, 3L, 4L, 6L, 7L);
        assertThat(result.lines()).extracting(Line::amount).containsExactly(
                new BigDecimal("1155000.00"), new BigDecimal("165000.00"), new BigDecimal("4400000.00"),
                new BigDecimal("378000.00"), new BigDecimal("123.00"),
                new BigDecimal("153.00"));
        assertThat(result.lines().get(4).passThrough()).isTrue();
        assertThat(result.total()).isEqualByComparingTo("6098276");
    }

    @Test
    void usesHalfOpenBoundsAndMostSpecificMatch() {
        BillableService direct = service(1, ServiceLayer.DIRECT, BillingUnit.PER_KM, false);
        BillableService fuel = service(2, ServiceLayer.FUEL, BillingUnit.PER_KM, false);
        List<PriceItem> prices = List.of(item(1, direct, "2.33", "0", "50"),
                item(2, direct, "3.33", "50", "200"), item(3, direct, "4.33", "200", null),
                item(4, direct, "1", null, null), item(5, fuel, "1", null, null));
        assertThat(PricingPolicy.calculate(list(), prices, List.of(direct, fuel), direct("49.99"))
                .lines().getFirst().priceItemId()).isEqualTo(1L);
        Line boundary = PricingPolicy.calculate(list(), prices, List.of(direct, fuel), direct("50"))
                .lines().getFirst();
        assertThat(boundary.priceItemId()).isEqualTo(2L);
        assertThat(boundary.unitPrice()).isEqualByComparingTo("4");
        assertThat(boundary.amount()).isEqualByComparingTo("200");
        for (String km : List.of("200", "900")) {
            assertThat(PricingPolicy.calculate(list(), prices, List.of(direct, fuel), direct(km))
                    .lines().getFirst().priceItemId()).isEqualTo(3L);
        }
        assertThatThrownBy(() -> PricingPolicy.calculate(list(), prices.subList(0, 2),
                List.of(direct, fuel), direct("200"))).isInstanceOf(PricingRuleException.class)
                .hasMessageContaining("Thiếu dòng giá");
    }

    @Test
    void rejectsEqualPriorityAndSelectsOnlyAnEffectiveList() {
        BillableService direct = service(1, ServiceLayer.DIRECT, BillingUnit.PER_KM, false);
        assertThatThrownBy(() -> PricingPolicy.calculate(list(),
                List.of(item(1, direct, "2", "0", "100"), item(2, direct, "3", "50", "200")),
                List.of(direct), direct("50"))).isInstanceOf(PricingRuleException.class).hasMessageContaining("ưu tiên");
        PriceList priceList = list();
        priceList.setValidTo(LocalDate.of(2026, 11, 23));
        assertThat(PricingPolicy.selectList(List.of(priceList), direct("50"))).isSameAs(priceList);
        priceList.setStatus(PriceListStatus.ARCHIVED);
        assertThatThrownBy(() -> PricingPolicy.selectList(List.of(priceList), direct("50")))
                .isInstanceOf(PricingRuleException.class);
    }

    @Test
    void chargesOnlyWideHorsesAndPrefersTheMatchingStallType() {
        BillableService stall = service(1, ServiceLayer.STALL, BillingUnit.PER_HORSE, false);
        PriceItem generic = item(1, stall, "5", null, null);
        PriceItem wide = item(2, stall, "12", null, null);
        StallType stallType = new StallType();
        stallType.setCode("WIDE");
        wide.setStallType(stallType);
        var directWithWide = new PricingInput("VND", LocalDate.of(2026, 11, 23), BookingType.LOCAL,
                null, null, List.of(new RoadLeg(ServiceLayer.DIRECT, 1L, BigDecimal.ZERO)),
                Map.of("STANDARD", 1, "WIDE", 2), 3, List.of());
        assertThat(PricingPolicy.calculate(list(), List.of(generic, wide), List.of(stall), directWithWide).lines())
                .singleElement().satisfies(line -> {
                    assertThat(line.priceItemId()).isEqualTo(2L);
                    assertThat(line.stallTypeCode()).isEqualTo("WIDE");
                    assertThat(line.quantity()).isEqualByComparingTo("2");
                    assertThat(line.amount()).isEqualByComparingTo("24");
                });
        assertThat(PricingPolicy.calculate(list(), List.of(generic, wide), List.of(stall), direct("0")).lines())
                .isEmpty();
    }

    @Test
    void rejectsInactiveFeedingEvenWhenItsPriceStillExists() {
        BillableService feeding = service(1, ServiceLayer.ADD_ON, BillingUnit.PER_HORSE_DAY, false);
        feeding.setCode("FEEDING");
        feeding.setStatus(ActiveStatus.INACTIVE);
        var input = new PricingInput("VND", LocalDate.of(2026, 11, 23), BookingType.LOCAL,
                null, null, List.of(), Map.of("STANDARD", 1), 1, List.of(new AddOn(1L, 1, 1, 1)));
        assertThatThrownBy(() -> PricingPolicy.calculate(list(), List.of(item(1, feeding, "10", null, null)),
                List.of(feeding), input)).isInstanceOf(PricingRuleException.class).hasMessageContaining("không khả dụng");
    }

    @Test
    void earlyPickupDaysUseCalendarDaysAndExcludeImportAndDirect() {
        LocalDate departure = LocalDate.of(2027, 2, 1);
        assertThat(PricingPolicy.earlyPickupDays(departure, departure)).isZero();
        assertThat(PricingPolicy.earlyPickupDays(departure.minusDays(1), departure)).isZero();
        assertThat(PricingPolicy.earlyPickupDays(BookingType.EXPORT, departure.minusDays(2), departure)).isEqualTo(1);
        assertThat(PricingPolicy.earlyPickupDays(BookingType.INTERPROVINCE, departure.minusDays(5), departure)).isEqualTo(4);
        assertThat(PricingPolicy.earlyPickupDays(BookingType.IMPORT, departure.minusDays(5), departure)).isZero();
        assertThat(PricingPolicy.earlyPickupDays(BookingType.INTERPROVINCE, departure.minusDays(5), null)).isZero();
    }

    @Test
    void pricesEarlyPickupByHorseDay() {
        var linehaul = service(1, ServiceLayer.LINEHAUL, BillingUnit.PER_STALL, false);
        var stay = service(2, ServiceLayer.QUARANTINE, BillingUnit.PER_HORSE_DAY, false);
        stay.setCode("QUARANTINE_STAY");
        var input = new PricingInput("VND", LocalDate.of(2026, 11, 23), BookingType.EXPORT, 4L,
                BigDecimal.TEN, List.of(), Map.of("STANDARD", 2), 2, List.of(), 3);
        var result = PricingPolicy.calculate(list(), List.of(item(1, linehaul, "10", null, null),
                item(2, stay, "20", null, null)), List.of(linehaul, stay), input);
        assertThat(result.lines().getLast().serviceCode()).isEqualTo("QUARANTINE_STAY");
        assertThat(result.lines().getLast().quantity()).isEqualByComparingTo("6");
    }

    @Test
    void labTestAddsVetCheckOnlyWhenMandatoryAndSubmittedByCompany() {
        var vet = service(2, ServiceLayer.ADD_ON, BillingUnit.PER_HORSE, false);
        vet.setCode("VET_CHECK");
        var required = new DocumentRequirementResponseDto(1L, 1L, 1L, "LAB_TEST", "Lab", null, null,
                DocumentSubmitter.COMPANY, true);
        var optional = new DocumentRequirementResponseDto(2L, 1L, 1L, "LAB_TEST", "Lab", null, null,
                DocumentSubmitter.COMPANY, false);
        assertThat(PricingPolicy.requiredAddOns(List.of(required), List.of(vet), 3))
                .containsExactly(new AddOn(2L, 3, 3, 0));
        assertThat(PricingPolicy.requiredAddOns(List.of(optional), List.of(vet), 3)).isEmpty();
    }

    @Test
    void driveTimeLimitsUseOneWayDurationForPickupAndDelivery() {
        assertThatCode(() -> PricingPolicy.validateDriveTime(ServiceLayer.PICKUP, 18000, 10))
                .doesNotThrowAnyException();
        assertThatCode(() -> PricingPolicy.validateDriveTime(ServiceLayer.DIRECT, 36000, 10))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PricingPolicy.validateDriveTime(ServiceLayer.PICKUP, 18001, 10))
                .isInstanceOfSatisfying(PricingRuleException.class, error -> {
                    assertThat(error.isConflict()).isTrue();
                    assertThat(error).hasMessageContaining("Chặng đón");
                });
        assertThatThrownBy(() -> PricingPolicy.validateDriveTime(ServiceLayer.DELIVERY, 18001, 10))
                .isInstanceOfSatisfying(PricingRuleException.class, error -> {
                    assertThat(error.isConflict()).isTrue();
                    assertThat(error).hasMessageContaining("Chặng giao");
                });
        assertThatThrownBy(() -> PricingPolicy.validateDriveTime(ServiceLayer.DIRECT, 36001, 10))
                .isInstanceOfSatisfying(PricingRuleException.class, error -> assertThat(error.isConflict()).isTrue());
    }

    private static PricingInput direct(String km) {
        return new PricingInput("VND", LocalDate.of(2026, 11, 23), BookingType.LOCAL, null, null,
                List.of(new RoadLeg(ServiceLayer.DIRECT, 1L, new BigDecimal(km))), Map.of("STANDARD", 1),
                1, List.of());
    }

    private static PriceList list() {
        PriceList list = new PriceList();
        list.setId(10L);
        list.setCurrencyCode("VND");
        list.setStatus(PriceListStatus.ACTIVE);
        list.setValidFrom(LocalDate.of(2026, 1, 1));
        list.setMarginRate(new BigDecimal("0.10"));
        list.setFuelBufferRate(new BigDecimal("0.05"));
        list.setDepositRate(new BigDecimal("0.30"));
        return list;
    }

    private static BillableService service(long id, ServiceLayer layer, BillingUnit unit, boolean passThrough) {
        BillableService service = new BillableService();
        service.setId(id);
        service.setStatus(ActiveStatus.ACTIVE);
        service.setCode(layer.name());
        service.setLayer(layer);
        service.setBillingUnit(unit);
        service.setPassThrough(passThrough);
        service.setAutoApplied(layer == ServiceLayer.DOCS);
        return service;
    }

    private static PriceItem item(long id, BillableService service, String price, String from, String to) {
        PriceItem item = new PriceItem();
        item.setId(id);
        item.setBillableService(service);
        item.setUnitPrice(new BigDecimal(price));
        if (from != null) item.setKmFrom(new BigDecimal(from));
        if (to != null) item.setKmTo(new BigDecimal(to));
        return item;
    }
}
