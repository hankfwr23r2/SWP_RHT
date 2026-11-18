package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.file.CaptureEvidenceDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.service.BookingVisibilityService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class BookingVisibilityServiceImpl implements BookingVisibilityService {

    private static final Set<String> BOTH_PURPOSES = Set.of("SOS", "EXPENSE");

    @Override
    public Map<String, CaptureEvidenceDto> visibleEvidence(BookingDetailsPayloadDto payload) {
        if (payload == null || payload.getPhotoEvidence() == null) return Map.of();
        if (!isCustomerViewer()) return payload.getPhotoEvidence();
        Set<String> publishedReceipts = new HashSet<>();
        if (payload.getSettlement() instanceof Map<?, ?> settlement && settlement.get("items") instanceof List<?> items) {
            for (Object item : items) if (item instanceof Map<?, ?> entry && entry.get("photo") instanceof String url) publishedReceipts.add(url);
        }
        Map<String, CaptureEvidenceDto> visible = new LinkedHashMap<>();
        payload.getPhotoEvidence().forEach((url, evidence) -> {
            // Hóa đơn phí cầu đường là chi phí nội bộ của nhà xe: khách không xem
            if ("TOLL".equals(evidence.purpose())) return;
            if (!BOTH_PURPOSES.contains(evidence.purpose()) || publishedReceipts.contains(url)) visible.put(url, evidence);
        });
        return visible;
    }

    @Override
    public List<VehicleTripDto> visibleTrips(BookingDetailsPayloadDto payload) {
        if (payload == null || payload.getTrips() == null) return null;
        if (!isCustomerViewer()) return payload.getTrips();
        return payload.getTrips().stream().map(trip -> {
            var copy = new VehicleTripDto();
            org.springframework.beans.BeanUtils.copyProperties(trip, copy);
            copy.setTolls(null);
            return copy;
        }).toList();
    }
    @Override
    public boolean isCustomerViewer() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
    }

    @Override
    public BookingResponseDto forCustomer(BookingResponseDto response) {
        response.setSettlement(null);
        response.setHistory(null);
        response.setIntake(null);
        response.setPlan(null);
        if (response.getPhotoEvidence() != null) {
            var visible = new java.util.LinkedHashMap<>(response.getPhotoEvidence());
            visible.entrySet().removeIf(entry ->
                    java.util.Set.of("EXPENSE", "TOLL").contains(entry.getValue().purpose()));
            response.setPhotoEvidence(visible);
        }
        if (response.getTrips() != null) {
            response.getTrips().forEach(trip -> {
                trip.setTolls(null);
                trip.setDriverPack(null);
                trip.setAcks(null);
            });
        }
        return response;
    }
}
