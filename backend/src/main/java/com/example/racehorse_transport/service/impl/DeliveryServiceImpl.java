package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.dto.field.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.DeliveryService;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.PaymentAmountLimits;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

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
    private final SystemLogRepository systemLogRepository;
    private final com.example.racehorse_transport.service.HorseDeliveryLocationService horseDeliveryLocationService;
    private final com.example.racehorse_transport.repository.StaffRepository staffRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public BookingResponse arriveDelivery(Integer bookingId, DeliveryArriveRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + bookingId));

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> arrivalMap = new HashMap<>();
        arrivalMap.put("arrivedAt", System.currentTimeMillis());
        arrivalMap.put("proofImageUrl", request != null ? request.getProofImageUrl() : null);
        arrivalMap.put("note", request != null ? request.getNote() : null);
        arrivalMap.put("graceWindowHours", 2); // 2 giờ chờ miễn phí

        payload.setArrival(arrivalMap);
        appendHistory(payload, request != null && request.getDriverUsername() != null ? request.getDriverUsername() : "Tài xế",
                "Xe tới điểm giao đích");
        booking.setStatus("arrived");
        savePayload(booking, payload);

        Booking updated = bookingRepository.save(booking);
        logAction("DELIVERY_ARRIVE", updated.getId(), "Xe đã tới điểm giao đích đơn BK-" + updated.getId());

        log.info("Driver checked in arrival at destination for booking ID={}", updated.getId());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse completeHandover(Integer bookingId, HandoverCompleteRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng ID: " + bookingId));

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> handoverMap = new HashMap<>();
        handoverMap.put("recipientName", request.getRecipientName());
        handoverMap.put("recipientPhone", request.getRecipientPhone());
        handoverMap.put("recipientSignature", request.getRecipientSignature());
        handoverMap.put("proofImageUrl", request.getProofImageUrl());
        handoverMap.put("handoverAt", System.currentTimeMillis());
        handoverMap.put("note", request.getNote());

        payload.setHandover(handoverMap);

        horseDeliveryLocationService.recordBookingHandover(booking, payload,
                request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                request.getProofImageUrl(), System.currentTimeMillis());

        // Kiểm tra xem đơn hàng có sự cố nào có chi phí thuộc phần KHÁCH HÀNG CHỊU không
        List<Incident> customerIncidents = incidentRepository.findByBookingID_IdAndCostBearer(booking.getId(), "CUSTOMER");
        boolean hasCustomerIncurredCosts = customerIncidents.stream()
                .anyMatch(i -> i.getActualCost() != null && i.getActualCost().compareTo(BigDecimal.ZERO) > 0);

        if (hasCustomerIncurredCosts) {
            // Chuyển sang chờ quyết toán phát sinh (Flow 6 - Nhánh có phát sinh)
            booking.setStatus("delivered_pending_settlement");
            appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Hoàn tất bàn giao ngựa");
        } else {
            // Không có chi phí phát sinh: Đóng đơn ngay lập tức (COMPLETED), giải phóng xe về AVAILABLE
            booking.setStatus("completed");
            appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Hoàn tất bàn giao ngựa");

            // Giải phóng xe về AVAILABLE tại Trụ sở chính VN
            releaseVehiclesToAvailable(booking.getId());
        }

        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("HANDOVER_COMPLETE", updated.getId(),
                "Hoàn tất ký nhận bàn giao ngựa cho " + request.getRecipientName() + ", trạng thái: " + updated.getStatus());

        log.info("Handover completed for booking ID={}, new status={}", updated.getId(), updated.getStatus());
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public BookingResponse reportDeliveryTimeout(Integer bookingId, DeliveryTimeoutRequest request, String username, String role) {
        Booking booking = bookingRepository.findById(bookingId)
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
        BookingDetailsPayload tripPayload = extractPayload(booking);
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

        // Tạo bản ghi sự cố loại HANDOVER_TIMEOUT với bên chịu chi phí là CUSTOMER
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
                .actualCost(request.getEstimatedCost())
                .costBearer("CUSTOMER") // Chi phí lưu ca xe và tiền chuồng do Khách hàng chịu theo hợp đồng
                .tripId(trip.getTripId())
                .locationLat(where[0])
                .locationLng(where[1])
                .staffID(staffRepository.findByUser_Username(username).orElse(null))
                .status("active")
                .createdAt(Instant.now())
                .build();
        incidentRepository.save(incident);

        BookingDetailsPayload payload = extractPayload(booking);
        if ("RETURN_TO_HQ".equalsIgnoreCase(action)) {
            booking.setStatus("returning_to_hq");
            appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Chở ngựa về trụ sở");
        } else {
            booking.setStatus("handover_timeout");
            appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Quá hạn bàn giao: Lưu chuồng tạm");
        }

        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("DELIVERY_TIMEOUT", updated.getId(),
                "Kích hoạt xử lý ngoại lệ bàn giao quá giờ: " + action + ", chi phí dự kiến: " + request.getEstimatedCost());

        log.info("Delivery timeout reported for booking ID={}, action={}", updated.getId(), action);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void returnToHq(ReturnToHqRequest request) {
        if (request.getVehicleId() != null) {
            Vehicle vehicle = vehicleRepository.findById(request.getVehicleId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy xe ID: " + request.getVehicleId()));

            if ("NEEDS_MAINTENANCE".equalsIgnoreCase(request.getVehicleCondition())) {
                vehicle.setStatus("INACTIVE"); // Xe cần bảo dưỡng đại tu
            } else {
                vehicle.setStatus("AVAILABLE"); // Xe sẵn sàng tại Trụ sở chính VN
            }
            vehicleRepository.save(vehicle);

            logAction("VEHICLE_RETURNED_HQ", vehicle.getId(),
                    "Xe " + vehicle.getLicensePlate() + " đã về tới Trụ sở chính Việt Nam. Trạng thái: " + vehicle.getStatus());
            log.info("Vehicle ID={} returned to Central HQ, status={}", vehicle.getId(), vehicle.getStatus());
        }
    }

    private void releaseVehiclesToAvailable(Integer bookingId) {
        List<BookingVehicle> bvs = bookingVehicleRepository.findByBookingID_Id(bookingId);
        for (BookingVehicle bv : bvs) {
            if (bv.getVehicleID() != null) {
                Vehicle v = bv.getVehicleID();
                v.setStatus("AVAILABLE");
                vehicleRepository.save(v);
                log.info("Released Vehicle ID={} to AVAILABLE at Central HQ", v.getId());
            }
        }
    }

    // Vị trí sự cố quá hạn: tọa độ mốc điểm giao nếu có, không thì điểm cuối của lộ trình đã chốt
    private Double[] deliveryLocation(Map<String, Object> deliveryPoint, BookingDetailsPayload payload) {
        if (deliveryPoint.get("lat") instanceof Number lat && deliveryPoint.get("lng") instanceof Number lng)
            return new Double[]{lat.doubleValue(), lng.doubleValue()};
        if (payload.getRoute() instanceof Map<?, ?> route && route.get("path") instanceof List<?> path && !path.isEmpty()
                && path.get(path.size() - 1) instanceof List<?> last && last.size() == 2
                && last.get(0) instanceof Number lat && last.get(1) instanceof Number lng)
            return new Double[]{lat.doubleValue(), lng.doubleValue()};
        return new Double[]{null, null};
    }

    private BookingDetailsPayload extractPayload(Booking b) {
        return BookingDetailsJson.read(objectMapper, b);
    }

    private void savePayload(Booking b, BookingDetailsPayload payload) {
        try {
            b.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.error("Could not serialize payload for booking ID={}: {}", b.getId(), ex.getMessage());
        }
    }

    private void appendHistory(BookingDetailsPayload payload, String actor, String text) {
        if (payload.getHistory() == null) {
            payload.setHistory(new ArrayList<>());
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("actor", actor);
        entry.put("text", text);
        payload.getHistory().add(entry);
    }

    private void logAction(String actionType, Integer targetRecordId, String newData) {
        try {
            SystemLog sysLog = SystemLog.builder()
                    .actionType(actionType)
                    .targetTable("BOOKING")
                    .targetRecordID(targetRecordId)
                    .newData(newData)
                    .createdAt(Instant.now())
                    .build();
            systemLogRepository.save(sysLog);
        } catch (Exception ex) {
            // Nhật ký lỗi không được làm hỏng thao tác chính, nhưng phải để lại dấu vết
            log.warn("Không ghi được nhật ký hệ thống {}: {}", actionType, ex.getMessage());
        }
    }

    private BookingResponse mapToResponse(Booking b) {
        String formattedId = "BK-" + String.format("%03d", b.getId());
        String customerUsername = "";
        String customerFullName = "";

        if (b.getCustomerID() != null) {
            customerFullName = b.getCustomerID().getFullName() != null ? b.getCustomerID().getFullName() : "";
            if (b.getCustomerID().getUser() != null) {
                customerUsername = b.getCustomerID().getUser().getUsername();
            }
        }

        BookingDetailsPayload payload = extractPayload(b);
        List<BookingHorseDto> horseDtos = payload.getHorses() != null ? payload.getHorses() : new ArrayList<>();

        return BookingResponse.builder()
                .id(formattedId)
                .numericId(b.getId())
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
                .trips(payload.getTrips())
                .route(payload.getRoute())
                .gate(payload.getGate())
                .plan(payload.getPlan())
                .photoEvidence(com.example.racehorse_transport.service.CaptureService.visibleEvidence(payload))
                .quote(payload.getQuote())
                .payment(payload.getPayment())
                .medical(payload.getMedical())
                .intake(payload.getIntake())
                .rejection(payload.getRejection())
                .history(payload.getHistory())
                .build();
    }
}
