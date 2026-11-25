package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.complaint.ComplaintCreateRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintOnBehalfRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintReplyRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintResolveRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintResponseDto;
import com.example.racehorse_transport.dto.complaint.DeliveryComplaintResponseDto;
import com.example.racehorse_transport.dto.incident.EvidenceResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DeliveryComplaintPolicy;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.DeliveryComplaintService;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DeliveryComplaintServiceImpl implements DeliveryComplaintService {

    private static final String PHOTO_TYPE = "COMPLAINT_PHOTO";

    private final BookingRepository bookings;
    private final BookingHorseRepository bookingHorses;
    private final BookingStatusHistoryRepository history;
    private final IncidentRepository incidents;
    private final StaffRepository staff;
    private final DocumentRepository documents;
    private final DocumentService documentFiles;
    private final SystemConfigService configs;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public DeliveryComplaintResponseDto view(Long bookingId, String username) {
        var owned = bookings.findByIdAndCustomer_User_Username(bookingId, username);
        if (owned.isPresent()) {
            return response(state(owned.get()), false);
        }
        manager(username);
        return response(state(bookings.findById(bookingId).orElseThrow(DeliveryComplaintServiceImpl::bookingNotFound)), true);
    }

    @Override
    @Transactional
    public DeliveryComplaintResponseDto confirm(Long bookingId, String username) {
        var state = state(lockOwnedBooking(bookingId, username));
        if (state.booking().getDeliveryConfirmedAt() == null) {
            requireCustomerWindow(state);
            state.booking().setDeliveryConfirmedAt(state.now());
        }
        return response(state, false);
    }

    @Override
    @Transactional
    public DeliveryComplaintResponseDto submit(Long bookingId, ComplaintCreateRequestDto request, String username) {
        var state = state(lockOwnedBooking(bookingId, username));
        requireCustomerWindow(state);
        var incident = request.toEntity(state.booking(), horse(bookingId, request.bookingHorseId()));
        save(incident, null, request.photos(), state.booking().getCustomer().getUserId());
        return response(state.withComplaint(incident), false);
    }

    @Override
    @Transactional
    public DeliveryComplaintResponseDto submitOnBehalf(Long bookingId, ComplaintOnBehalfRequestDto request, String username) {
        var actor = manager(username);
        var state = state(lockBooking(bookingId));
        if (!state.managerCanSubmit()) {
            throw BookingRuleException.conflict("Đơn đã có khiếu nại hoặc ngoài hạn quản lý ghi nhận thay khách.");
        }
        var incident = request.toEntity(state.booking(), horse(bookingId, request.bookingHorseId()));
        save(incident, actor, request.photos(), actor.getUserId());
        return response(state.withComplaint(incident), true);
    }

    @Override
    @Transactional
    public DeliveryComplaintResponseDto reply(Long bookingId, ComplaintReplyRequestDto request, String username) {
        manager(username);
        var state = state(lockBooking(bookingId));
        request.applyTo(openComplaint(state));
        return response(state, true);
    }

    @Override
    @Transactional
    public DeliveryComplaintResponseDto resolve(Long bookingId, ComplaintResolveRequestDto request, String username) {
        var actor = manager(username);
        var state = state(lockBooking(bookingId));
        var complaint = openComplaint(state);
        request.applyTo(complaint);
        complaint.setComplaintDecision(request.decision());
        complaint.setComplaintDecidedBy(actor);
        complaint.setResolvedAt(state.now());
        complaint.setStatus(IncidentStatus.CLOSED);
        return response(state, true);
    }

    private Booking lockBooking(Long bookingId) {
        return bookings.findForUpdate(bookingId).orElseThrow(DeliveryComplaintServiceImpl::bookingNotFound);
    }

    private Booking lockOwnedBooking(Long bookingId, String username) {
        var booking = lockBooking(bookingId);
        if (!booking.getCustomer().getUser().getUsername().equals(username)) {
            throw bookingNotFound();
        }
        return booking;
    }

    private Staff manager(String username) {
        return staff.findByUser_Username(username)
                .filter(actor -> "MANAGER".equals(actor.getUser().getRole().getCode()))
                .orElseThrow(DeliveryComplaintServiceImpl::bookingNotFound);
    }

    private BookingHorse horse(Long bookingId, Long bookingHorseId) {
        if (bookingHorseId == null) {
            return null;
        }
        var selected = bookingHorses.findById(bookingHorseId)
                .filter(horse -> horse.getBooking().getId().equals(bookingId))
                .orElseThrow(() -> new BookingRuleException("bookingHorseId", "Ngựa được chọn không thuộc đơn này."));
        if (selected.getStatus() == BookingHorseStatus.EXCLUDED || selected.getStatus() == BookingHorseStatus.CANCELLED) {
            throw new BookingRuleException("bookingHorseId", "Ngựa đã bị loại hoặc hủy khỏi đơn.");
        }
        return selected;
    }

    private void save(Incident complaint, Staff reporter, List<MultipartFile> photos, Long uploaderId) {
        if (photos == null || photos.isEmpty()) {
            throw new BookingRuleException("photos", "Khiếu nại phải có ít nhất một ảnh.");
        }
        photos.forEach(DeliveryComplaintServiceImpl::requirePhoto);
        complaint.setTitle("Khiếu nại giao ngựa");
        complaint.setIncidentType(IncidentType.COMPLAINT);
        complaint.setSeverity(IncidentPolicy.defaultSeverity(complaint.getIncidentType()));
        complaint.setStatus(IncidentStatus.REPORTED);
        complaint.setReportedBy(reporter);
        incidents.saveAndFlush(complaint);
        for (var photo : photos) {
            documentFiles.attach(photo, PHOTO_TYPE, DocumentSubject.INCIDENT, complaint.getId(), uploaderId);
        }
    }

    private static void requirePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new BookingRuleException("photos", "Ảnh khiếu nại đang trống.");
        }
        try (var stream = photo.getInputStream(); var image = ImageIO.createImageInputStream(stream)) {
            if (image != null && ImageIO.getImageReaders(image).hasNext()) {
                return;
            }
        } catch (IOException ignored) {
            throw new BookingRuleException("photos", "Không đọc được ảnh khiếu nại.");
        }
        throw new BookingRuleException("photos", "Tệp khiếu nại phải là ảnh hợp lệ.");
    }

    private ComplaintState state(Booking booking) {
        var deliveredAt = history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(booking.getId(), BookingStatus.DELIVERED)
                .map(BookingStatusHistory::getChangedAt).orElse(null);
        var complaints = incidents.findByBooking_IdAndIncidentTypeOrderByReportedAtDescIdDesc(
                booking.getId(), IncidentType.COMPLAINT);
        var open = complaints.stream().filter(row -> row.getStatus() != IncidentStatus.CLOSED).findFirst();
        var complaint = open.or(() -> complaints.stream().findFirst());
        return new ComplaintState(booking, deliveredAt, clock.instant(),
                Duration.ofDays(configs.getInt("SETTLEMENT_AUTO_CLOSE_DAYS")),
                Duration.ofDays(configs.getInt("COMPLAINT_MANAGER_DEADLINE_DAYS")), complaint,
                complaints.stream().anyMatch(row -> row.getReportedBy() == null));
    }

    private DeliveryComplaintResponseDto response(ComplaintState state, boolean managerView) {
        var complaint = state.complaint().map(incident -> {
            var photos = documents.findByIncident_IdOrderById(incident.getId()).stream()
                    .filter(document -> PHOTO_TYPE.equals(document.getDocumentType().getCode()))
                    .map(document -> EvidenceResponseDto.from(document, null)).toList();
            return ComplaintResponseDto.from(incident, photos);
        }).orElse(null);
        return DeliveryComplaintResponseDto.from(state.booking(), state.deliveredAt(),
                !managerView && state.customerCanRespond(), managerView && state.managerCanSubmit(), complaint);
    }

    private static void requireCustomerWindow(ComplaintState state) {
        if (!state.customerCanRespond()) {
            throw BookingRuleException.conflict("Đơn đã có phản hồi hoặc ngoài hạn phản hồi giao ngựa.");
        }
    }

    private static Incident openComplaint(ComplaintState state) {
        var complaint = state.complaint().orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại."));
        if (complaint.getStatus() != IncidentStatus.REPORTED) {
            throw BookingRuleException.conflict("Khiếu nại đã được kết luận.");
        }
        return complaint;
    }

    private static ResourceNotFoundException bookingNotFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn.");
    }

    private record ComplaintState(Booking booking, Instant deliveredAt, Instant now, Duration customerWindow,
                                  Duration managerWindow, Optional<Incident> complaint, boolean customerSubmitted) {
        boolean customerCanRespond() {
            return DeliveryComplaintPolicy.canCustomerSubmit(booking.getStatus(), deliveredAt, now, customerWindow,
                    booking.getDeliveryConfirmedAt() != null || customerSubmitted || hasOpenComplaint());
        }

        boolean managerCanSubmit() {
            return DeliveryComplaintPolicy.canManagerSubmit(booking.getStatus(), deliveredAt, now,
                    managerWindow, hasOpenComplaint());
        }

        private boolean hasOpenComplaint() {
            return complaint.filter(row -> row.getStatus() != IncidentStatus.CLOSED).isPresent();
        }

        ComplaintState withComplaint(Incident incident) {
            return new ComplaintState(booking, deliveredAt, now, customerWindow, managerWindow, Optional.of(incident),
                    customerSubmitted || incident.getReportedBy() == null);
        }
    }
}
