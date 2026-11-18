package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.file.CaptureEvidenceDto;
import java.time.*;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;

public interface CaptureService {

    CaptureEvidenceDto upload(MultipartFile file, Integer bookingId, String tripId, String purpose,
            String target, long capturedAt, String username, String role);

    void attach(BookingDetailsPayloadDto payload, Integer bookingId, String tripId, String purpose, String target, String url, String actor);

    void attachTripPhotos(BookingDetailsPayloadDto payload, Integer bookingId, String tripId, String action, Map<String, Object> request, String actor);
}
