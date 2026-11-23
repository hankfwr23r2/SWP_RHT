package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto;
import com.example.racehorse_transport.dto.pricing.PricingPreviewResponseDto;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.PricingResult;
import com.example.racehorse_transport.policy.PricingPolicy.RoadLeg;
import com.example.racehorse_transport.service.PricingDataService;
import com.example.racehorse_transport.service.PricingRouteService;
import com.example.racehorse_transport.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {
    private final PricingDataService data;
    private final PricingRouteService routing;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PricingPreviewResponseDto preview(PricingPreviewRequestDto request, String username) {
        var preview = data.loadPreview(request, username);
        var roadLegs = preview.segments().stream()
                .map(segment -> new RoadLeg(segment.layer(), preview.vehicleClassId(),
                        routing.distance(segment, preview.pickupTime(), preview.weightTons(), preview.topSpeedKmh())))
                .toList();
        PricingInput loaded = preview.input();
        PricingInput input = new PricingInput(loaded.currencyCode(), loaded.quotationDate(), loaded.bookingType(),
                loaded.routeId(), loaded.linehaulKm(), roadLegs, loaded.stallsByType(), loaded.horseCount(),
                loaded.addOns());
        return PricingPreviewResponseDto.from(input, data.calculate(input));
    }

    @Override
    public PricingResult calculate(PricingInput input) {
        return data.calculate(input);
    }
}
