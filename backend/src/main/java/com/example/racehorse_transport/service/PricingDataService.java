package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsRequestDto;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.PricingResult;
import com.example.racehorse_transport.service.PricingRouteService.RoadSegment;
import java.time.LocalDateTime;
import java.util.List;

public interface PricingDataService {
    PreviewData loadPreview(PricingPreviewRequestDto request, String username);

    // Chặng công ty có thể chở ở đầu đón, đầu giao trong nước của đơn đi trục. Đơn chạy thẳng: danh sách trống
    TransferData loadTransfers(TransferOptionsRequestDto request, String username);

    PricingResult calculate(PricingInput input);

    record TransferData(List<RoadSegment> segments, int weightTons, int topSpeedKmh, LocalDateTime pickupTime) {}

    record PreviewData(PricingInput input, List<RoadSegment> segments, Long vehicleClassId,
                       int weightTons, int topSpeedKmh, LocalDateTime pickupTime, Long deliveryHubId,
                       List<DeliveryRouteService.Destination> destinations, List<Long> deliveryAddressIds, java.time.Instant deliveryReadyAt) {}
}
