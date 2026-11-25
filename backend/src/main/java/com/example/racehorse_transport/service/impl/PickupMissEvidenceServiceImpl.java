package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.HandoverRefusalRequestDto;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.PickupMissPolicy;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.*;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PickupMissEvidenceServiceImpl implements PickupMissEvidenceService {
    private final DocumentService documents;
    private final SystemConfigService configs;
    private final AuditService audit;
    private final StaffRepository staff;
    private final NotificationService notifications;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(BookingHorse horse, HandoverRefusalRequestDto request, MultipartFile photo,
                       Instant arrivedAt, Instant at, Long actorId) {
        Map<String, String> data = new HashMap<>(Map.of("reason", horse.getPickupMissReason()));
        if (request != null) {
            PickupMissPolicy.requireEvidence(request.reasonCode(), arrivedAt, at,
                    Duration.ofMinutes(configs.getInt("PICKUP_MISS_WAIT_MINUTES")), request.senderContact(),
                    request.customerContact(), request.emergencyContact());
            if (photo == null || photo.isEmpty() || photo.getContentType() == null
                    || !photo.getContentType().startsWith("image/")) {
                throw new DispatchRuleException("photo", "Cần ảnh ghi nhận đón hụt.");
            }
            documents.attach(photo, "PICKUP_MISS_PHOTO", DocumentSubject.BOOKING_HORSE, horse.getId(), actorId);
            data.put("reasonCode", request.reasonCode().name());
            if (request.senderContact() != null) data.put("senderContact", request.senderContact().strip());
            if (request.customerContact() != null) data.put("customerContact", request.customerContact().strip());
            if (request.emergencyContact() != null) data.put("emergencyContact", request.emergencyContact().strip());
        }
        audit(actorId, "PICKUP_MISSED", horse.getId(), data);
        var booking = horse.getBooking();
        var recipients = new HashSet<>(staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE));
        recipients.addAll(staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE));
        recipients.add(booking.getCustomer().getUser().getId());
        notifications.notify(List.copyOf(recipients), booking.getId(), "Đón hụt",
                "Chưa nhận được " + horse.getHorse().getName() + ". Xem chi tiết đơn để xử lý trước hạn.", null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void audit(Long actorId, String action, Long horseId, Map<String, String> data) {
        audit.record(actorId, action, "booking_horse", horseId, null, data);
    }
}
