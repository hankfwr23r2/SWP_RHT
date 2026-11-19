package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.field.DeliveryArriveRequestDto;
import com.example.racehorse_transport.dto.field.DeliveryTimeoutRequestDto;
import com.example.racehorse_transport.dto.field.HandoverCompleteRequestDto;
import com.example.racehorse_transport.dto.field.ReturnToHqRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.DeliveryService;
import com.example.racehorse_transport.service.HorseDeliveryLocationService;
import com.example.racehorse_transport.service.OperationalLogService;
import com.example.racehorse_transport.service.StaffNotificationService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.BookingHistory;
import com.example.racehorse_transport.service.BookingVisibilityService;
import com.example.racehorse_transport.util.PaymentAmountLimits;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingVehicleRepository bookingVehicleRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final IncidentRepository incidentRepository;
    private final PaymentRepository paymentRepository;
    private final OperationalLogService operationalLog;
    private final HorseDeliveryLocationService locations;
    private final StaffRepository staffRepository;
    private final StaffNotificationService staffNotifier;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BookingVisibilityService bookingVisibility;

    @Override
    @Transactional
    public BookingResponseDto arriveDelivery(Integer bookingId, DeliveryArriveRequestDto request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + bookingId));

        BookingDetailsPayloadDto payload = extractPayload(booking);
        Map<String, Object> arrivalMap = new HashMap<>();
        arrivalMap.put("arrivedAt", System.currentTimeMillis());
        arrivalMap.put("proofImageUrl", request != null ? request.getProofImageUrl() : null);
        arrivalMap.put("note", request != null ? request.getNote() : null);
        arrivalMap.put("graceWindowHours", 2); // 2 giờ chờ miễn phí

        payload.setArrival(arrivalMap);
        BookingHistory.appendHistory(payload, request != null && request.getDriverUsername() != null ? request.getDriverUsername() : "Tài xế",
                "Xe tới điểm giao đích");
        booking.setStatus("arrived");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        logAction("DELIVERY_ARRIVE", updated.getId(), "Xe đã tới điểm giao đích đơn " + updated.getCode());

        log.info("Driver checked in arrival at destination for booking ID={}", updated.getId());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponseDto completeHandover(Integer bookingId, HandoverCompleteRequestDto request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + bookingId));

        BookingDetailsPayloadDto payload = extractPayload(booking);
        Map<String, Object> handoverMap = new HashMap<>();
        handoverMap.put("recipientName", request.getRecipientName());
        handoverMap.put("recipientPhone", request.getRecipientPhone());
        handoverMap.put("recipientSignature", request.getRecipientSignature());
        handoverMap.put("proofImageUrl", request.getProofImageUrl());
        handoverMap.put("handoverAt", System.currentTimeMillis());
        handoverMap.put("note", request.getNote());

        payload.setHandover(handoverMap);

        completeBooking(booking, payload,
                request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                request.getProofImageUrl(), System.currentTimeMillis());

        BookingHistory.appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                "Hoàn tất bàn giao ngựa, chờ đối soát nội bộ công ty");

        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("HANDOVER_COMPLETE", updated.getId(),
                "Hoàn tất ký nhận bàn giao ngựa cho " + request.getRecipientName() + ", trạng thái: " + updated.getStatus());

        log.info("Handover completed for booking ID={}, new status={}", updated.getId(), updated.getStatus());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponseDto reportDeliveryTimeout(Integer bookingId, DeliveryTimeoutRequestDto request, String username, String role) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + bookingId));
        request.setDriverUsername(username);
        if (!java.util.Set.of("in_transit", "arrived").contains(String.valueOf(booking.getStatus()))) {
            throw new IllegalStateException("Đơn không ở bước bàn giao nên không báo quá hạn được.");
        }
        String action = request.getSelectedAction() != null ? request.getSelectedAction() : "STABLE_NEARBY";
        if ("STABLE_NEARBY".equalsIgnoreCase(action)) {
            BigDecimal estimatedCost = request.getEstimatedCost();
            if (estimatedCost == null || estimatedCost.signum() <= 0
                    || estimatedCost.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("Chi phí lưu chuồng phải là số nguyên lớn hơn 0.");
            }
            if (PaymentAmountLimits.exceedsMaximum(estimatedCost)) {
                throw new IllegalArgumentException("Chi phí lưu chuồng vượt quá giới hạn thanh toán được hỗ trợ.");
            }
        }
        BookingDetailsPayloadDto tripPayload = extractPayload(booking);
        var trip = tripPayload.getTrips() == null ? null : tripPayload.getTrips().stream()
                .filter(t -> request.getTripId() == null || request.getTripId().equals(t.getTripId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến của đơn."));
        if (!"ADMIN".equalsIgnoreCase(role)) {
            // Chỉ tài xế được phân công cho chuyến này mới báo quá hạn
            var staff = staffRepository.findByUser_Username(username).orElse(null);
            String assigned = Objects.toString(trip.getDriverId(), "");
            boolean mine = "DRIVER".equalsIgnoreCase(role) && staff != null && (assigned.equalsIgnoreCase(Objects.toString(staff.getStaffCode(), ""))
                    || assigned.equals(Objects.toString(staff.getId(), "")) || assigned.equalsIgnoreCase(username));
            if (!mine) throw new org.springframework.security.access.AccessDeniedException("Chỉ tài xế của chuyến này được báo quá hạn bàn giao.");
        }
        Map<String, Object> deliveryPoint = trip.getRun() == null || !(trip.getRun().get("checkpoints") instanceof List<?> cps) ? null
                : cps.stream().filter(cp -> cp instanceof Map<?, ?> m && "delivery".equals(m.get("type")))
                        .map(cp -> { @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) cp; return m; })
                        .findFirst().orElse(null);
        if (deliveryPoint == null || !(deliveryPoint.get("arrivedAt") instanceof Number arrivedAt)) {
            throw new IllegalArgumentException("Xe cần tới điểm giao trước khi báo quá hạn bàn giao.");
        }
        long waitedMinutes = (Instant.now().toEpochMilli() - arrivedAt.longValue()) / 60000;
        if (waitedMinutes < 120 && !request.isConfirmEarly()) {
            throw new IllegalArgumentException("Mới chờ " + waitedMinutes + "/120 phút ở điểm giao. Cần xác nhận báo quá hạn sớm.");
        }
        Double[] where = deliveryLocation(deliveryPoint, tripPayload);

        String reason = request.getTimeoutReason() != null ? request.getTimeoutReason() : "UNREACHABLE";

        // Quá hạn bàn giao chỉ tạo sự cố nội bộ, không thu thêm tiền khách
        Incident incident = Incident.builder()
                .bookingID(booking)
                .incidentType("HANDOVER_TIMEOUT")
                .incidentName("Quá hạn 2 giờ: Người nhận chưa nhận được ngựa (" + reason + ")")
                .description("Kích hoạt an sinh ngựa: " + ("STABLE_NEARBY".equalsIgnoreCase(action) ? "Thuê chuồng lưu trú khẩn cấp gần nhất" : "Chở ngựa quay về Trụ sở chính VN")
                        + (waitedMinutes < 120 ? ". Tài xế báo sớm sau " + waitedMinutes + " phút chờ" : "") + ". Ghi chú: " + request.getNote())
                .resolutionType("HANDOVER_TIMEOUT")
                .partnerName(request.getPartnerName())
                .partnerPhone(request.getPartnerPhone())
                .estimatedCost(request.getEstimatedCost())
                .actualCost(null)
                .costBearer("COMPANY") // Công ty chịu toàn bộ chi phí chờ bàn giao và an sinh
                .tripId(trip.getTripId())
                .locationLat(where[0])
                .locationLng(where[1])
                .staffID(staffRepository.findByUser_Username(username).orElse(null))
                .status("active")
                .createdAt(Instant.now())
                .build();
        incidentRepository.save(incident);

        BookingDetailsPayloadDto payload = extractPayload(booking);
        if ("RETURN_TO_HQ".equalsIgnoreCase(action)) {
            booking.setStatus("returning_to_hq");
            BookingHistory.appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Chở ngựa về trụ sở");
        } else {
            booking.setStatus("handover_timeout");
            BookingHistory.appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Quá hạn bàn giao: Lưu chuồng tạm");
        }

        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("DELIVERY_TIMEOUT", updated.getId(),
                "Kích hoạt xử lý ngoại lệ bàn giao quá giờ: " + action + ", chi phí dự kiến: " + request.getEstimatedCost());

        staffNotifier.toManagers(updated, "Quá hạn bàn giao · " + updated.getCode(),
                ("RETURN_TO_HQ".equalsIgnoreCase(action) ? "Người nhận không tới, xe chở ngựa về trụ sở." : "Người nhận không tới quá 2 giờ, ngựa đưa vào chuồng lưu trú.")
                        + " Liên hệ khách để hẹn lại.", "/manager/incidents");
        log.info("Delivery timeout reported for booking ID={}, action={}", updated.getId(), action);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void returnToHq(ReturnToHqRequestDto request) {
        throw new IllegalStateException("Vui lòng xác nhận về trụ sở trên chuyến được phân công, kèm ảnh tại trụ sở.");
    }

    // Vị trí sự cố quá hạn: tọa độ mốc điểm giao nếu có, không thì điểm cuối của lộ trình đã chốt
    private Double[] deliveryLocation(Map<String, Object> deliveryPoint, BookingDetailsPayloadDto payload) {
        if (deliveryPoint.get("lat") instanceof Number lat && deliveryPoint.get("lng") instanceof Number lng)
            return new Double[]{lat.doubleValue(), lng.doubleValue()};
        if (payload.getRoute() instanceof Map<?, ?> route && route.get("path") instanceof List<?> path && !path.isEmpty()
                && path.get(path.size() - 1) instanceof List<?> last && last.size() == 2
                && last.get(0) instanceof Number lat && last.get(1) instanceof Number lng)
            return new Double[]{lat.doubleValue(), lng.doubleValue()};
        return new Double[]{null, null};
    }

    private BookingDetailsPayloadDto extractPayload(Booking b) {
        return BookingDetailsJson.read(objectMapper, b);
    }

    private void savePayload(Booking b, BookingDetailsPayloadDto payload) {
        try {
            b.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload for booking ID={}: {}", b.getId(), ex.getMessage());
        }
    }

    private void logAction(String actionType, Integer targetRecordId, String newData) {
        try {
            operationalLog.record(actionType, "BOOKING", targetRecordId, newData, null);
        } catch (Exception ex) {
            // Nhật ký lỗi không được làm hỏng thao tác chính, nhưng phải để lại dấu vết
            log.warn("Không ghi được nhật ký hệ thống {}: {}", actionType, ex.getMessage());
        }
    }

    private BookingResponseDto mapToResponse(Booking b) {
        String formattedId = b.getCode();
        String customerUsername = "";
        String customerFullName = "";

        if (b.getCustomerID() != null) {
            customerFullName = b.getCustomerID().getFullName() != null ? b.getCustomerID().getFullName() : "";
            if (b.getCustomerID().getUser() != null) {
                customerUsername = b.getCustomerID().getUser().getUsername();
            }
        }

        BookingDetailsPayloadDto payload = extractPayload(b);
        List<BookingHorseDto> horseDtos = payload.getHorses() != null ? payload.getHorses() : new ArrayList<>();

        return BookingResponseDto.builder()
                .id(formattedId)
                .type(b.getBookingType() != null ? b.getBookingType() : "domestic")
                .customer(customerUsername)
                .customerName(customerFullName)
                .status(b.getStatus() != null ? b.getStatus() : "pending_intake")
                .createdAt(b.getBookingDate() != null ? b.getBookingDate().toEpochMilli() : System.currentTimeMillis())
                .departAt(b.getDepartureDate() != null ? b.getDepartureDate().toEpochMilli() : System.currentTimeMillis())
                .originName(b.getPickupAddress())
                .originCountry(payload != null ? payload.getOriginCountry() : null)
                .destName(b.getDropoffAddress())
                .destCountry(payload != null ? payload.getDestCountry() : null)
                .consignor(PartyContactDto.consignorOf(b))
                .consignee(PartyContactDto.consigneeOf(b))
                .totalHorses(b.getTotalHorses() != null ? b.getTotalHorses() : 1)
                .horses(horseDtos)
                .waybillNo(b.getWaybillNo())
                .trips(bookingVisibility.visibleTrips(payload))
                .route(payload.getRoute())
                .gate(payload.getGate())
                .plan(payload.getPlan())
                .photoEvidence(bookingVisibility.visibleEvidence(payload))
                .quote(payload.getQuote())
                .payment(payload.getPayment())
                .medical(payload.getMedical())
                .intake(payload.getIntake())
                .rejection(payload.getRejection())
                .sendBack(payload.getSendBack())
                .history(payload.getHistory())
                .build();
    }

    @Override
    public void completeTrip(Booking booking, BookingDetailsPayloadDto payload, VehicleTripDto trip,
                      String actor, String photo, long time) {
        if (payload.getTrips().stream().allMatch(t -> t.getRun() != null
                && t.getRun().get("deliveredAt") != null)) {
            markDelivered(booking);
        }
        locations.recordTripDelivery(booking, payload, trip, actor, photo, time);
    }

    private void completeBooking(Booking booking, BookingDetailsPayloadDto payload, String actor, String photo, long time) {
        locations.recordBookingHandover(booking, payload, actor, photo, time);
        markDelivered(booking);
    }

    private void markDelivered(Booking booking) {
        booking.setStatus("delivered_pending_settlement");
    }
}
