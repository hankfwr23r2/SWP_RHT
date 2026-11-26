package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto;
import com.example.racehorse_transport.dto.pricing.PricingPreviewResponseDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsRequestDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsResponseDto;
import com.example.racehorse_transport.dto.pricing.TransferOptionsResponseDto.TransferEndDto;
import com.example.racehorse_transport.entity.enums.ServiceLayer;
import com.example.racehorse_transport.exception.NoTruckRouteException;
import com.example.racehorse_transport.exception.RoutingUnavailableException;
import com.example.racehorse_transport.policy.PricingPolicy;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.PricingResult;
import com.example.racehorse_transport.policy.PricingPolicy.RoadLeg;
import com.example.racehorse_transport.policy.PricingPolicy.TransferStatus;
import com.example.racehorse_transport.service.PricingDataService;
import com.example.racehorse_transport.service.PricingDataService.TransferData;
import com.example.racehorse_transport.service.PricingRouteService;
import com.example.racehorse_transport.service.PricingRouteService.RoadSegment;
import com.example.racehorse_transport.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.example.racehorse_transport.service.DeliveryRouteService.DeliveryPricing;
import com.example.racehorse_transport.service.DeliveryRouteService.Destination;
import com.example.racehorse_transport.service.DeliveryRouteService;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingServiceImpl implements PricingService {
    private final PricingDataService data;
    private final PricingRouteService routing;
    private final DeliveryRouteService deliveryRoutes;

    @Override
    public DeliveryPricing deliveryPricing(
            Long hubId, List<Destination> destinations) {
        return deliveryRoutes.previewPricing(hubId, destinations);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public PricingPreviewResponseDto preview(PricingPreviewRequestDto request, String username) {
        var preview = data.loadPreview(request, username);
        var roadLegs = new ArrayList<>(preview.segments().stream()
                .map(segment -> {
                    var estimate = routing.routeOrEstimate(segment, preview.pickupTime(), preview.weightTons(), preview.topSpeedKmh());
                    PricingPolicy.validateDriveTime(segment.layer(), estimate.driveSeconds(), segment.maxDriveHoursPerDay());
                    return new RoadLeg(segment.layer(), preview.vehicleClassId(), estimate.km());
                })
                .toList());
        var delivery = preview.destinations().isEmpty() ? null : deliveryPricing(preview.deliveryHubId(), preview.destinations());
        var loaded = preview.input();
        if (delivery != null) {
            roadLegs.add(loaded.privateVehicle() == null ? delivery.loop()
                    : new RoadLeg(ServiceLayer.DELIVERY, loaded.privateVehicle().classId(), delivery.loop().km()));
        }
        PricingInput input = new PricingInput(loaded.currencyCode(), loaded.quotationDate(), loaded.bookingType(),
                loaded.routeId(), loaded.linehaulKm(), roadLegs, loaded.stallsByType(), loaded.horseCount(),
                loaded.addOns(), loaded.earlyPickupDays(),
                delivery == null || loaded.privateVehicle() != null ? List.of() : delivery.separate(), loaded.privateVehicle());
        var estimates = new ArrayList<PricingPreviewResponseDto.DeliveryEstimate>();
        if (delivery != null) {
            var times = deliveryRoutes.arrivalTimes(delivery.plan(), preview.destinations(), preview.deliveryReadyAt());
            for (int i = 0; i < times.size(); i++) {
                int index = delivery.plan().order().get(i) - 1;
                estimates.add(new PricingPreviewResponseDto.DeliveryEstimate(preview.deliveryAddressIds().get(index), times.get(i)));
            }
        }
        return PricingPreviewResponseDto.from(input, data.calculate(input), estimates);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public TransferOptionsResponseDto transferOptions(TransferOptionsRequestDto request, String username) {
        var transfers = data.loadTransfers(request, username);
        return new TransferOptionsResponseDto(end(ServiceLayer.PICKUP, transfers), end(ServiceLayer.DELIVERY, transfers));
    }

    private TransferEndDto end(ServiceLayer layer, TransferData transfers) {
        return transfers.segments().stream().filter(segment -> segment.layer() == layer).findFirst()
                .map(segment -> status(segment, transfers))
                .map(status -> TransferEndDto.from(status, PricingPolicy.transferReason(layer, status)))
                .orElse(null);
    }

    private TransferStatus status(RoadSegment segment, TransferData transfers) {
        try {
            var estimate = routing.route(segment, transfers.pickupTime(), transfers.weightTons(), transfers.topSpeedKmh());
            return PricingPolicy.transferStatus(segment.layer(), estimate.driveSeconds(), segment.maxDriveHoursPerDay());
        } catch (NoTruckRouteException e) {
            return TransferStatus.NO_ROUTE;
        } catch (RoutingUnavailableException e) {
            return TransferStatus.UNKNOWN;
        }
    }

    @Override
    public PricingResult calculate(PricingInput input) {
        return data.calculate(input);
    }
}
