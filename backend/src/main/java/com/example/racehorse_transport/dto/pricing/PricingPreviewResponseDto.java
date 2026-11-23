package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.ServiceLayer;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.PricingResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record PricingPreviewResponseDto(Long priceListId, String currencyCode, BookingType bookingType,
                                        BigDecimal depositRate, List<LegDistance> legs,
                                        List<PriceLineResponseDto> lines, BigDecimal total) {
    public record LegDistance(ServiceLayer layer, BigDecimal km, Long vehicleClassId) {}

    public static PricingPreviewResponseDto from(PricingInput input, PricingResult result) {
        List<LegDistance> legs = new ArrayList<>();
        input.roadLegs().forEach(leg -> legs.add(new LegDistance(leg.layer(), leg.km(), leg.vehicleClassId())));
        if (input.routeId() != null) {
            int position = !legs.isEmpty() && legs.getFirst().layer() == ServiceLayer.PICKUP ? 1 : 0;
            legs.add(position, new LegDistance(ServiceLayer.LINEHAUL, input.linehaulKm(), null));
        }
        return new PricingPreviewResponseDto(result.priceListId(), result.currencyCode(), input.bookingType(),
                result.depositRate(), List.copyOf(legs), result.lines().stream().map(PriceLineResponseDto::from).toList(),
                result.total());
    }
}
