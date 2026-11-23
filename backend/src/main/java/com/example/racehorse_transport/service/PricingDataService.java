package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.PricingResult;
import com.example.racehorse_transport.service.PricingRouteService.RoadSegment;
import java.time.LocalDateTime;
import java.util.List;

public interface PricingDataService {
    PreviewData loadPreview(PricingPreviewRequestDto request, String username);

    PricingResult calculate(PricingInput input);

    record PreviewData(PricingInput input, List<RoadSegment> segments, Long vehicleClassId,
                       int weightTons, int topSpeedKmh, LocalDateTime pickupTime) {}
}
