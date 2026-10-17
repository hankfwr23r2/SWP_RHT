package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.booking.PartyContactDto;
import com.example.racehorse_transport.dto.field.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.DeliveryService;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public BookingResponse arriveDelivery(Integer bookingId, DeliveryArriveRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + bookingId));

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> arrivalMap = new HashMap<>();
        arrivalMap.put("arrivedAt", System.currentTimeMillis());
        arrivalMap.put("proofImageUrl", request != null ? request.getProofImageUrl() : null);
        arrivalMap.put("note", request != null ? request.getNote() : null);
        arrivalMap.put("graceWindowHours", 2); // 2 giờ chờ miễn phí

        payload.setArrival(arrivalMap);
        appendHistory(payload, request != null && request.getDriverUsername() != null ? request.getDriverUsername() : "Tài xế",
                "Đã tới điểm giao đích. Bắt đầu đếm thời gian chờ bàn giao (tối đa 2 giờ miễn phí).");
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
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + bookingId));

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> handoverMap = new HashMap<>();
        handoverMap.put("recipientName", request.getRecipientName());
        handoverMap.put("recipientPhone", request.getRecipientPhone());
        handoverMap.put("recipientSignature", request.getRecipientSignature());
        handoverMap.put("proofImageUrl", request.getProofImageUrl());
        handoverMap.put("handoverAt", System.currentTimeMillis());
        handoverMap.put("note", request.getNote());

        payload.setHandover(handoverMap);

        // Kiểm tra xem đơn hàng có sự cố nào có chi phí thuộc phần KHÁCH HÀNG CHỊU không
        List<Incident> customerIncidents = incidentRepository.findByBookingID_IdAndCostBearer(booking.getId(), "CUSTOMER");
        boolean hasCustomerIncurredCosts = customerIncidents.stream()
                .anyMatch(i -> i.getActualCost() != null && i.getActualCost().compareTo(BigDecimal.ZERO) > 0);

        if (hasCustomerIncurredCosts) {
            // Chuyển sang chờ quyết toán phát sinh (Flow 6 - Nhánh có phát sinh)
            booking.setStatus("delivered_pending_settlement");
            appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Đã hoàn tất bàn giao ngựa cho " + request.getRecipientName()
                            + ". Đơn chuyển sang chờ quyết toán chi phí phát sinh (hóa đơn thú y/chuồng đệm đội ngoài).");
        } else {
            // Không có chi phí phát sinh: Đóng đơn ngay lập tức (COMPLETED), giải phóng xe về AVAILABLE
            booking.setStatus("completed");
            appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Đã hoàn tất bàn giao ngựa cho " + request.getRecipientName()
                            + ". Không phát sinh chi phí, đóng đơn hoàn tất.");

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
    public BookingResponse reportDeliveryTimeout(Integer bookingId, DeliveryTimeoutRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + bookingId));

        String action = request.getSelectedAction() != null ? request.getSelectedAction() : "STABLE_NEARBY";
        String reason = request.getTimeoutReason() != null ? request.getTimeoutReason() : "UNREACHABLE";

        // Tạo bản ghi sự cố loại HANDOVER_TIMEOUT với bên chịu chi phí là CUSTOMER
        Incident incident = Incident.builder()
                .bookingID(booking)
                .incidentType("HANDOVER_TIMEOUT")
                .incidentName("Quá hạn 2 giờ: Người nhận chưa nhận được ngựa (" + reason + ")")
                .description("Kích hoạt an sinh ngựa: " + ("STABLE_NEARBY".equalsIgnoreCase(action) ? "Thuê chuồng lưu trú khẩn cấp gần nhất" : "Chở ngựa quay về Trụ sở chính VN") + ". Ghi chú: " + request.getNote())
                .resolutionType("HANDOVER_TIMEOUT")
                .partnerName(request.getPartnerName())
                .partnerPhone(request.getPartnerPhone())
                .estimatedCost(request.getEstimatedCost())
                .actualCost(request.getEstimatedCost())
                .costBearer("CUSTOMER") // Chi phí lưu ca xe và tiền chuồng do Khách hàng chịu theo hợp đồng
                .status("active")
                .createdAt(Instant.now())
                .build();
        incidentRepository.save(incident);

        BookingDetailsPayload payload = extractPayload(booking);
        if ("RETURN_TO_HQ".equalsIgnoreCase(action)) {
            booking.setStatus("returning_to_hq");
            appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Người nhận từ chối/quá hạn nhận ngựa. Kíp xe kích hoạt phương án chở ngựa quay về Trụ sở chính Việt Nam.");
        } else {
            booking.setStatus("handover_timeout");
            appendHistory(payload, request.getDriverUsername() != null ? request.getDriverUsername() : "Kíp xe",
                    "Quá 2 giờ chưa bàn giao được. Kíp xe đã đưa ngựa vào chuồng lưu trú khẩn cấp tại: "
                            + (request.getPartnerName() != null ? request.getPartnerName() : "Trang trại đối tác gần nhất")
                            + " để bảo đảm an sinh. Phát sinh phí lưu chuồng & lưu ca xe tính vào quyết toán.");
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
    public BookingResponse issueSettlement(Integer bookingId, IssueSettlementRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng ID: " + bookingId));

        BookingDetailsPayload payload = extractPayload(booking);
        Map<String, Object> settlementMap = new HashMap<>();
        settlementMap.put("issuedAt", System.currentTimeMillis());
        settlementMap.put("extraCost", request.getExtraCost());
        settlementMap.put("lines", request.getLines());
        settlementMap.put("note", request.getNote());
        settlementMap.put("manager", request.getManagerUsername());

        payload.setSettlement(settlementMap);
        booking.setStatus("settlement_issued");

        appendHistory(payload, request.getManagerUsername() != null ? request.getManagerUsername() : "Quản lý",
                "Phát hành Bảng quyết toán phát sinh: " + request.getExtraCost() + " VND (kèm hóa đơn chứng từ đội ngoài). Chờ khách thanh toán.");

        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);

        logAction("SETTLEMENT_ISSUED", updated.getId(),
                "Quản lý phát hành quyết toán " + request.getExtraCost() + " VND cho đơn BK-" + updated.getId());

        log.info("Issued settlement statement for booking ID={}, amount={}", updated.getId(), request.getExtraCost());
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

    private BookingDetailsPayload extractPayload(Booking b) {
        if (b.getDetailsJson() != null && !b.getDetailsJson().trim().isEmpty()) {
            try {
                if (b.getDetailsJson().trim().startsWith("{")) {
                    return objectMapper.readValue(b.getDetailsJson().trim(), BookingDetailsPayload.class);
                }
            } catch (Exception ignored) {}
        }
        return new BookingDetailsPayload();
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
        } catch (Exception ignored) {}
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
                .destName(b.getDropoffAddress())
                .consignor(PartyContactDto.consignorOf(b))
                .consignee(PartyContactDto.consigneeOf(b))
                .totalHorses(b.getTotalHorses() != null ? b.getTotalHorses() : 1)
                .horses(horseDtos)
                .waybillNo(b.getWaybillNo())
                .trips(payload.getTrips())
                .route(payload.getRoute())
                .gate(payload.getGate())
                .plan(payload.getPlan())
                .quote(payload.getQuote())
                .payment(payload.getPayment())
                .medical(payload.getMedical())
                .intake(payload.getIntake())
                .rejection(payload.getRejection())
                .history(payload.getHistory())
                .build();
    }
}
