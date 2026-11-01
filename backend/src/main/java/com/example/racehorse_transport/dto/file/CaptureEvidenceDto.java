package com.example.racehorse_transport.dto.file;
import com.example.racehorse_transport.entity.CaptureAsset;
public record CaptureEvidenceDto(Long id, String url, Integer bookingId, String tripId, String purpose,
        String target, String capturedBy, long capturedAt, long recordedAt) {
    public static CaptureEvidenceDto of(CaptureAsset a) {
        return new CaptureEvidenceDto(a.getId(), a.getUrl(), a.getBookingId(), a.getTripId(), a.getPurpose(),
            a.getTarget(), a.getCapturedBy(), a.getCapturedAt().toEpochMilli(), a.getRecordedAt().toEpochMilli());
    }
}
