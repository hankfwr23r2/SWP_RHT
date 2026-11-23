package com.example.racehorse_transport.dto.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.policy.PricingPolicy.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingDtoTest {
    @Test
    void listMappingLeavesIdentityStatusAndRatesToService() {
        LocalDate date = LocalDate.of(2026, 11, 23);
        PriceList list = new PriceListCreateRequestDto(" Base ", "VND", BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, date, null).toEntity();
        assertThat(list.getName()).isEqualTo("Base");
        assertThat(list.getId()).isNull();
        assertThat(list.getStatus()).isNull();
        assertThat(list.getMarginRate()).isNull();
        list.setId(1L);
        list.setStatus(PriceListStatus.DRAFT);
        list.setDepositRate(BigDecimal.TEN);
        new PriceListUpdateRequestDto("New", "VND", BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, date, date).applyTo(list);
        assertThat(list.getId()).isEqualTo(1L);
        assertThat(list.getStatus()).isEqualTo(PriceListStatus.DRAFT);
        assertThat(list.getDepositRate()).isEqualTo(BigDecimal.TEN);
        assertThat(PriceListResponseDto.from(list).name()).isEqualTo("New");
    }

    @Test
    void itemMappingUsesLoadedReferencesAndLeavesUnitPriceToService() {
        PriceList list = new PriceList();
        BillableService service = new BillableService();
        service.setId(2L);
        PriceItem item = new PriceItemCreateRequestDto(2L, null, null, null, null, null, BigDecimal.TEN)
                .toEntity(list, service, null, null, null);
        assertThat(item.getId()).isNull();
        assertThat(item.getUnitPrice()).isNull();
        item.setId(1L);
        item.setUnitPrice(BigDecimal.ONE);
        new PriceItemUpdateRequestDto(2L, null, null, null, BigDecimal.ZERO, null, BigDecimal.TEN)
                .applyTo(item, service, null, null, null);
        assertThat(item.getPriceList()).isSameAs(list);
        assertThat(item.getId()).isEqualTo(1L);
        assertThat(item.getUnitPrice()).isEqualTo(BigDecimal.ONE);
        assertThat(PriceItemResponseDto.from(item).id()).isEqualTo(1L);
        assertThat(BillableServiceResponseDto.from(service).id()).isEqualTo(2L);
    }

    @Test
    void previewMappingKeepsCalculatedMoneyAndPriceItemIdentity() {
        Line line = new Line(17L, "DIRECT_KM", BillingUnit.PER_KM, 2L, null,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, false);
        PricingInput input = new PricingInput("VND", LocalDate.of(2026, 11, 23), BookingType.LOCAL,
                null, null, List.of(new RoadLeg(ServiceLayer.DIRECT, 2L, BigDecimal.TEN)),
                Map.of("STANDARD", 1), 1, 0, List.of());
        PricingPreviewResponseDto response = PricingPreviewResponseDto.from(input,
                new PricingResult(1L, "VND", BigDecimal.ONE, List.of(line), BigDecimal.TEN));
        assertThat(response.lines()).containsExactly(PriceLineResponseDto.from(line));
        assertThat(response.lines().getFirst().priceItemId()).isEqualTo(17L);
        assertThat(response.total()).isEqualTo(BigDecimal.TEN);
    }
}
