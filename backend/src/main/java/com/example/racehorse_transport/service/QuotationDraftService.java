package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.quotation.QuotationResponseDto;
import com.example.racehorse_transport.dto.operation.PickupAddOnResponseDto;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.HorseLeg;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuotationDraftService {
    record DeliveryReplacement(Quotation quotation,
            com.example.racehorse_transport.dto.booking.DeliveryChangeResponseDto preview) {}

    DeliveryReplacement replaceDelivery(Quotation previous, List<BookingHorse> active,
            List<com.example.racehorse_transport.entity.Payment> payments, DeliveryRouteService.DeliveryPricing delivery,
            java.time.LocalDate date, java.math.BigDecimal expected);

    QuotationResponseDto create(Long bookingId);

    List<Instant> validateInsurance(Quotation quotation, Instant now);

    void setTotals(Quotation quotation, List<QuotationLine> lines);

    Optional<Quotation> replaceAfterExclusion(BookingHorse excluded);

    Optional<Quotation> replaceAfterPickupChoice(BookingHorse horse, HorseLeg retry, List<BookingHorse> riding,
                                                boolean waivePickup, boolean exclude, boolean preview);

    List<PickupAddOnResponseDto> pickupAddOns(Long horseId);

    PickupAddOnResponseDto completePickupAddOn(Long horseId, Long serviceId, Instant at);
}
