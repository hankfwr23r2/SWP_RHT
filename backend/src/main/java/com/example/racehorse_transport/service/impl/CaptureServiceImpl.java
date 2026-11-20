package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.file.CaptureEvidenceDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CaptureAsset;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CaptureAssetRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.CaptureService;
import com.example.racehorse_transport.service.StorageService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor
public class CaptureServiceImpl implements CaptureService {
    private final CaptureAssetRepository assets;
    private final BookingRepository bookings;
    private final StaffRepository staff;
    private final StorageService storage;
    private final ObjectMapper objectMapper;
    private static final Set<String> DRIVER_PURPOSES = Set.of("ARRIVE_PICKUP", "ARRIVE_CHECKPOINT", "UPLOAD_HANDOVER", "CUSTOMS_CLEARED", "CONFIRM_HOME", "TOLL", "RETURN_HORSES", "STABLE_HANDOVER");
    private static final Set<String> ESCORT_PURPOSES = Set.of("EXCLUDE_UNFIT_HORSE", "SUBMIT_WELFARE");
    private static final Set<String> BOTH_PURPOSES = Set.of("SOS", "EXPENSE");

    @Transactional
    public CaptureEvidenceDto upload(MultipartFile file, Integer bookingId, String tripId, String purpose,
            String target, long capturedAt, String username, String role) {
        if (tripId == null || tripId.length() > 100 || purpose == null || target == null || target.length() > 100)
            throw new IllegalArgumentException("Ngữ cảnh ảnh không hợp lệ.");
        if (!(BOTH_PURPOSES.contains(purpose) || ("DRIVER".equals(role) && DRIVER_PURPOSES.contains(purpose))
                || ("ESCORT".equals(role) && ESCORT_PURPOSES.contains(purpose))))
            throw new AccessDeniedException("Vai trò không được chụp loại chứng từ này.");
        Booking booking = bookings.findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn."));
        var payload = BookingDetailsJson.read(objectMapper, booking);
        var trip = payload.getTrips() == null ? null : payload.getTrips().stream().filter(t -> tripId.equals(t.getTripId())).findFirst().orElse(null);
        // Khách có thể trả quyết toán (đơn hoàn tất) khi kíp xe còn đang chạy về: vẫn cho chụp ảnh tại trụ sở để xác nhận về
        boolean crewStillDrivingBack = "CONFIRM_HOME".equals(purpose) && trip != null && trip.getHomeReturn() != null
                && trip.getHomeReturn().get("backAt") == null;
        if (Set.of("cancelled", "rejected", "quote_expired", "completed").contains(Objects.toString(booking.getStatus(), ""))
                && !crewStillDrivingBack)
            throw new IllegalStateException("Đơn đã đóng, không thể chụp chứng từ mới.");
        Staff actor = staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy nhân sự."));
        if (trip == null || !role.equalsIgnoreCase(actor.getRole())) throw new AccessDeniedException("Chuyến hoặc vai trò không hợp lệ.");
        String assigned = "DRIVER".equals(role) ? trip.getDriverId() : trip.getEscortId();
        if (assigned == null || !(assigned.equalsIgnoreCase(Objects.toString(actor.getStaffCode(), ""))
                || assigned.equals(Objects.toString(actor.getId(), "")) || assigned.equalsIgnoreCase(username)))
            throw new AccessDeniedException("Bạn không được phân công vào chuyến này.");
        Instant now = Instant.now();
        Instant shot;
        try { shot = Instant.ofEpochMilli(capturedAt); } catch (Exception e) { throw new IllegalArgumentException("Thời gian chụp không hợp lệ."); }
        // Client time is a declaration, not trusted evidence of physical capture time.
        if (shot.isBefore(now.minus(Duration.ofDays(1))) || shot.isAfter(now.plusSeconds(300)))
            throw new IllegalArgumentException("Thời gian chụp lệch quá xa. Kiểm tra đồng hồ thiết bị và chụp lại.");
        validateImage(file);
        String url = storage.uploadFile(file, "captures");
        CaptureAsset asset = CaptureAsset.builder().url(url).bookingId(bookingId).tripId(tripId).purpose(purpose).target(target)
            .capturedBy(username).capturedAt(shot).recordedAt(Instant.now()).build();
        return CaptureEvidenceDto.of(assets.save(asset));
    }

    // Phí cầu đường trên từng chuyến là chi phí nội bộ của nhà xe: bỏ khỏi dữ liệu trả cho khách


    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024 || !"image/jpeg".equals(file.getContentType()))
            throw new IllegalArgumentException("Ảnh chụp phải là JPEG, tối đa 5 MB.");
        try (var stream = ImageIO.createImageInputStream(file.getInputStream())) {
            if (stream == null) throw new IllegalArgumentException("Không đọc được ảnh.");
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("Tệp không phải ảnh hợp lệ.");
            var reader = readers.next();
            try {
                reader.setInput(stream);
                if (!"JPEG".equalsIgnoreCase(reader.getFormatName()) || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096)
                    throw new IllegalArgumentException("Ảnh JPEG không hợp lệ hoặc quá lớn.");
                if (reader.read(0) == null) throw new IllegalArgumentException("Ảnh bị hỏng.");
            } finally { reader.dispose(); }
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Không đọc được ảnh chụp."); }
    }

    @Transactional
    public void attach(BookingDetailsPayloadDto payload, Integer bookingId, String tripId, String purpose, String target, String url, String actor) {
        CaptureAsset asset = assets.findByUrl(url == null ? "" : url)
            .orElseThrow(() -> new IllegalArgumentException("Ảnh chưa được lưu qua camera. Vui lòng chụp và tải ảnh lên trước."));
        if (!bookingId.equals(asset.getBookingId()) || !tripId.equals(asset.getTripId()) || !purpose.equals(asset.getPurpose())
                || !target.equals(asset.getTarget()) || !actor.equals(asset.getCapturedBy()))
            throw new AccessDeniedException("Ảnh không thuộc người gửi, chuyến hoặc mốc nghiệp vụ này.");
        if (payload.getPhotoEvidence() == null) payload.setPhotoEvidence(new LinkedHashMap<>());
        payload.getPhotoEvidence().put(url, CaptureEvidenceDto.of(asset));
    }

    public void attachTripPhotos(BookingDetailsPayloadDto payload, Integer bookingId, String tripId, String action, Map<String, Object> request, String actor) {
        String purpose = action.toUpperCase(Locale.ROOT);
        String target = switch (purpose) {
            case "ARRIVE_PICKUP", "RETURN_HORSES" -> "pickup";
            case "ARRIVE_CHECKPOINT", "SUBMIT_WELFARE" -> Objects.toString(request.get("checkpointId"), "");
            case "UPLOAD_HANDOVER" -> Objects.toString(request.getOrDefault("checkpointId", "pickup"));
            case "EXCLUDE_UNFIT_HORSE" -> Objects.toString(request.get("horseId"), "");
            default -> "";
        };
        if (DRIVER_PURPOSES.contains(purpose) || ESCORT_PURPOSES.contains(purpose)) {
            if ("CUSTOMS_CLEARED".equals(purpose)) {
                if (request.get("stampPhotos") instanceof List<?> photos)
                    for (int i = 0; i < photos.size(); i++) attach(payload, bookingId, tripId, purpose, i == 0 ? "health_cert" : "ata_carnet", Objects.toString(photos.get(i), ""), actor);
            } else {
                attach(payload, bookingId, tripId, purpose, target, Objects.toString(request.get("photo"), ""), actor);
            }
        }
    }
}
