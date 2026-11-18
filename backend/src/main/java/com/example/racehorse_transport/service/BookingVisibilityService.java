package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.file.CaptureEvidenceDto;
import java.util.List;
import java.util.Map;

public interface BookingVisibilityService {

    boolean isCustomerViewer();

    List<VehicleTripDto> visibleTrips(BookingDetailsPayloadDto payload);

    Map<String, CaptureEvidenceDto> visibleEvidence(BookingDetailsPayloadDto payload);

    BookingResponseDto forCustomer(BookingResponseDto response);
}
