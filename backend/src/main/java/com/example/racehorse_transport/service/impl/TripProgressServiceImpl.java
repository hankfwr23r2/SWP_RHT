package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.field.TripUpdateRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Log;
import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.LogRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.CaptureService;
import com.example.racehorse_transport.service.DeliveryService;
import com.example.racehorse_transport.service.OperationalLogService;
import com.example.racehorse_transport.service.StaffNotificationService;
import com.example.racehorse_transport.service.TripProgressService;
import com.example.racehorse_transport.util.BookingHistory;
import com.example.racehorse_transport.util.BookingTripRules;
import com.example.racehorse_transport.util.BookingDetailsJson;
import com.example.racehorse_transport.util.HorseInsurancePolicy;
import com.example.racehorse_transport.util.MicrochipPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripProgressServiceImpl implements TripProgressService {

    private final CaptureService captureService;
    private final BookingRepository bookingRepository;
    private final HorseRepository horseRepository;
    private final QuotationRepository quotationRepository;
    private final NotificationRepository notificationRepository;
    private final LogRepository logRepository;
    private final VehicleRepository vehicleRepository;
    private final StaffRepository staffRepository;
    private final DeliveryService deliveryService;
    private final IncidentRepository incidentRepository;
    private final StaffNotificationService staffNotifier;
    private final OperationalLogService operationalLog;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BookingQueryService bookingQueryService;
    private final com.example.racehorse_transport.service.BillMailService billMailService;

    private static final long MAX_TOLL_PER_RECEIPT = 10_000_000L;
    // Chia việc trong kíp xe, khớp với màn hình tài xế và hộ tống. Nhận lệnh và xác nhận về trụ sở thì ai cũng làm.
    private static final Set<String> DRIVER_ACTIONS = Set.of("DEPART_TO_PICKUP", "ARRIVE_PICKUP", "COLLECT_ORIGINALS",
            "UPLOAD_HANDOVER", "START_JOURNEY", "ARRIVE_CHECKPOINT", "CONTINUE_JOURNEY", "CUSTOMS_CLEARED",
            "RETURN_ORIGINALS", "COMPLETE_DELIVERY", "CHIP_MISMATCH", "RETURN_HORSES", "ADD_TOLL", "REMOVE_TOLL");
    private static final Set<String> ESCORT_ACTIONS = Set.of("SCAN_CHIP", "EXCLUDE_UNFIT_HORSE", "SUBMIT_WELFARE");

    private void validateJourneyStart(Booking booking, BookingDetailsPayloadDto payload, VehicleTripDto trip) {
        if (trip.getDepartedAt() == null) {
            throw new IllegalStateException("Xe cần xuất phát đến điểm đón trước khi bắt đầu vận chuyển.");
        }
        Map<String, Object> run = trip.getRun();
        if (run != null && run.get("startedAt") != null) {
            throw new IllegalStateException("Hành trình của chuyến xe đã bắt đầu.");
        }
        String missing = BookingTripRules.missingPickupStep(payload, trip);
        if (missing != null) throw new IllegalStateException(missing);

        Map<?, ?> payment = payload.getPayment() instanceof Map<?, ?> p ? p : Map.of();
        if (!Boolean.TRUE.equals(payment.get("balancePaid"))) {
            throw new IllegalStateException("Khách hàng cần thanh toán đủ 70% còn lại trước khi xe xuất phát.");
        }
    }

    // Xe vừa bị loại hết ngựa trong khi xe khác của đơn còn chở: xe rỗng không chạy tuyến mà quay về trụ sở
    private void sendEmptyTripHome(Booking booking, BookingDetailsPayloadDto payload, VehicleTripDto trip, long now) {
        if (!BookingTripRules.horsesOnTrip(payload, trip).isEmpty() || trip.getDepartedAt() == null || trip.getHomeReturn() != null) return;
        if (payload.getTrips().stream().allMatch(t -> BookingTripRules.horsesOnTrip(payload, t).isEmpty())) return; // cả đơn không còn ngựa: chờ quản lý hủy
        Map<String, Object> hr = new HashMap<>();
        hr.put("fromPlace", booking.getPickupAddress() != null ? booking.getPickupAddress() : "Điểm đón");
        hr.put("earliestAt", now + 1000);
        hr.put("eta", now + 7200000L);
        trip.setHomeReturn(hr);
        BookingHistory.appendWithDefaultActor(payload, "Hệ thống", "Chuyến " + trip.getTripId() + " không còn ngựa để chở, xe về trụ sở");
    }

    // Loại hết ngựa ở điểm đón thì xe không có gì để chở: đơn sang "Không nhận được ngựa" chờ quản lý hủy
    private void markPickupFailedIfNoHorseLeft(Booking booking, BookingDetailsPayloadDto payload, String actor, String reason) {
        if (payload.getTrips() == null || payload.getTrips().stream().anyMatch(t -> !BookingTripRules.horsesOnTrip(payload, t).isEmpty())) return;
        booking.setStatus("pickup_failed");
        BookingHistory.appendWithDefaultActor(payload, "Hệ thống", "Không nhận được ngựa: " + reason + ". Chờ quản lý hủy đơn");
        String bookingCode = booking.getCode();
        Instant now = Instant.now();
        notificationRepository.save(Notification.builder()
                .recipientRole("manager")
                .title("Không nhận được ngựa · " + bookingCode)
                .message(actor + " báo: " + reason + " ở điểm đón. Xe và kíp đang chờ quản lý hủy đơn.")
                .link("/manager/incidents")
                .bookingId(bookingCode)
                .isRead(false)
                .createdAt(now)
                .build());
        if (booking.getCustomerID() != null && booking.getCustomerID().getUser() != null) {
            notificationRepository.save(Notification.builder()
                    .recipientRole("customer")
                    .recipientUsername(booking.getCustomerID().getUser().getUsername())
                    .title("Không nhận được ngựa · " + bookingCode)
                    .message("Kíp xe không nhận được ngựa của đơn " + bookingCode + ": " + reason
                            + ". Quản lý sẽ liên hệ với bạn để xử lý đơn.")
                    .link("/orders/" + bookingCode)
                    .bookingId(bookingCode)
                    .isRead(false)
                    .createdAt(now)
                    .build());
        }
    }

    // Kíp xe vừa nhận xong ngựa: nhắc khách thanh toán 70% còn lại (một lần) để xe được xuất phát
    private void notifyBalanceDueOnce(Booking booking, BookingDetailsPayloadDto payload) {
        if (!"en_route_to_pickup".equalsIgnoreCase(booking.getStatus()) || !BookingTripRules.pickupComplete(payload)) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> payment = payload.getPayment() instanceof Map<?, ?> m
                ? new HashMap<>((Map<String, Object>) m) : new HashMap<>();
        if (Boolean.TRUE.equals(payment.get("balancePaid")) || payment.get("balanceRequestedAt") != null) return;
        Instant now = Instant.now();
        payment.put("balanceRequestedAt", now.toEpochMilli());
        payload.setPayment(payment);
        BookingHistory.appendWithDefaultActor(payload, "Hệ thống", "Kíp xe đã nhận ngựa, chờ khách thanh toán 70% còn lại");
        // Mail nhắc kèm số tiền còn lại theo báo giá (gửi sau khi lưu đơn thành công)
        Object balance = payload.getQuote() instanceof Map<?, ?> quote ? quote.get("balance") : null;
        if (balance != null) billMailService.balanceDue(booking, new BigDecimal(balance.toString()));
        if (booking.getCustomerID() == null || booking.getCustomerID().getUser() == null) return;
        String bookingCode = booking.getCode();
        notificationRepository.save(Notification.builder()
                .recipientRole("customer")
                .recipientUsername(booking.getCustomerID().getUser().getUsername())
                .title("Thanh toán 70% còn lại · " + bookingCode)
                .message("Kíp xe đã nhận ngựa và ký biên bản giao nhận. Bạn thanh toán 70% còn lại để xe bắt đầu hành trình.")
                .link("/orders/" + bookingCode)
                .bookingId(bookingCode)
                .isRead(false)
                .createdAt(now)
                .build());
    }

    @Override
    public boolean isPickupComplete(Booking booking) {
        return BookingTripRules.pickupComplete(BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking));
    }

    @Override
    @Transactional
    public BookingResponseDto updateTripProgress(String bookingIdentifier, TripUpdateRequestDto request,
            String username, String role) {
        Integer bookingId = parseBookingId(bookingIdentifier);
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));

        BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
        List<VehicleTripDto> trips = payload.getTrips();
        if (trips == null || trips.isEmpty()) {
            throw new IllegalStateException("Đơn hàng chưa có thông tin chuyến xe!");
        }

        String tripId = request.getTripId();
        VehicleTripDto targetTrip = trips.stream()
                .filter(t -> t.getTripId() != null && t.getTripId().equalsIgnoreCase(tripId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chuyến: " + tripId));
        String action = request.getAction() != null ? request.getAction() : "";
        validateTripUpdateActor(booking, targetTrip, action, username, role);
        Map<String, Object> activeRun = targetTrip.getRun();
        if (activeRun != null && activeRun.get("startedAt") != null) {
            activeRun.put("checkpoints", BookingTripRules.ensureRouteCheckpoints(booking, payload, targetTrip));
            targetTrip.setRun(activeRun);
        }

        String actor = username;
        Map<String, Object> reqPayload = request.getPayload() != null ? request.getPayload() : new HashMap<>();
        long now = System.currentTimeMillis();

        switch (action.toUpperCase()) {
            case "ACKNOWLEDGE":
            case "ACK": {
                // Tài xế / hộ tống chỉ nhận lệnh cho chính mình; nhân viên điều hành mới ghi thay theo "who"
                String who = "DRIVER".equalsIgnoreCase(role) ? "driver" : "ESCORT".equalsIgnoreCase(role) ? "escort"
                        : String.valueOf(reqPayload.getOrDefault("who", "driver"));
                Map<String, Object> acks = targetTrip.getAcks() != null ? targetTrip.getAcks() : new HashMap<>();
                acks.put(who, now);
                targetTrip.setAcks(acks);
                BookingHistory.appendWithDefaultActor(payload, actor, ("driver".equalsIgnoreCase(who) ? "Tài xế" : "Hộ tống") + " nhận lệnh điều xe");

                // Nếu đơn đã sẵn sàng hoặc đang di chuyển thì chỉ ghi nhận ack
                String currentStatus = booking.getStatus() != null ? booking.getStatus().toLowerCase() : "";
                if ("ready_for_pickup".equals(currentStatus) || "en_route_to_pickup".equals(currentStatus) || "in_transit".equals(currentStatus)) {
                    break;
                }

                // Kiểm tra xem kíp xe (Driver và Escort) của các chuyến đã xác nhận đủ chưa
                boolean allTripsAcked = trips.stream().allMatch(t -> {
                    Map<String, Object> a = t.getAcks();
                    return a != null && a.get("driver") != null && a.get("escort") != null;
                });

                if (allTripsAcked) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> clearanceMap = payload.getClearance() instanceof Map<?, ?> existingClr
                            ? new HashMap<>((Map<String, Object>) existingClr)
                            : new HashMap<>();

                    clearanceMap.put("crewAckedAt", now);

                    if (clearanceMap.get("doneAt") != null) {
                        booking.setStatus("ready_for_pickup");
                        BookingHistory.appendWithDefaultActor(payload, "Hệ thống", "Sẵn sàng đón ngựa");
                        log.info("Booking ID={} advanced to ready_for_pickup because clearance was already done", booking.getId());
                    }
                    payload.setClearance(clearanceMap);
                }
                break;
            }
            case "SET_DRIVER_PACK": {
                @SuppressWarnings("unchecked")
                List<String> items = reqPayload.get("items") instanceof List<?> list
                        ? (List<String>) list
                        : new ArrayList<>();
                Map<String, Object> pack = new HashMap<>();
                pack.put("items", items);
                pack.put("at", now);
                pack.put("by", actor);
                targetTrip.setDriverPack(pack);
                BookingHistory.appendWithDefaultActor(payload, actor, "Lập bộ giấy tờ bàn giao cho tài xế");
                break;
            }
            case "DEPART_TO_PICKUP": {
                if (targetTrip.getDepartedAt() != null) throw new IllegalStateException("Xe của chuyến này đã xuất phát.");
                // Chỉ xuất phát khi giấy tờ đã xong (Sẵn sàng đón ngựa) hoặc một xe khác của đơn đã xuất phát
                boolean ready = "ready_for_pickup".equalsIgnoreCase(booking.getStatus());
                if (!ready && trips.stream().noneMatch(t -> t.getDepartedAt() != null)) {
                    throw new IllegalStateException("Đơn chưa sẵn sàng đón ngựa, chưa thể xuất phát.");
                }
                targetTrip.setDepartedAt(now);
                if (ready) booking.setStatus("en_route_to_pickup");
                BookingHistory.appendWithDefaultActor(payload, actor, "Xe xuất phát đến điểm đón");
                break;
            }
            case "ARRIVE_PICKUP": {
                if (targetTrip.getDepartedAt() == null) throw new IllegalStateException("Xe phải xuất phát đến điểm đón trước khi báo đã tới.");
                if (targetTrip.getRun() != null && targetTrip.getRun().get("startedAt") != null) {
                    throw new IllegalStateException("Hành trình vận chuyển đã bắt đầu.");
                }
                if (BookingTripRules.isBlank(reqPayload.get("photo"))) throw new IllegalArgumentException("Cần chụp ảnh tại điểm đón.");
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                String pickupLoc = booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()
                        ? booking.getPickupAddress() : "Điểm đón";
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    cp.put("type", "pickup");
                    cp.put("label", "Điểm đón");
                    cp.put("place", pickupLoc);
                    cp.put("arrivedAt", now);
                    cp.put("photo", reqPayload.get("photo"));
                    checkpoints.add(cp);
                } else {
                    Map<String, Object> first = checkpoints.get(0);
                    first.putIfAbsent("id", "pickup");
                    first.putIfAbsent("type", "pickup");
                    first.putIfAbsent("label", "Điểm đón");
                    first.putIfAbsent("place", pickupLoc);
                    first.put("arrivedAt", now);
                    first.put("photo", reqPayload.get("photo"));
                }
                targetTrip.setRun(run);
                BookingHistory.appendWithDefaultActor(payload, actor, "Tài xế đã có mặt tại điểm đón");
                break;
            }
            case "SCAN_CHIP": {
                String chip = String.valueOf(reqPayload.getOrDefault("chip", "")).toUpperCase().trim();
                if (chip.isEmpty()) throw new IllegalArgumentException("Chưa nhập số microchip.");
                // Chỉ ghi nhận chip của ngựa đang đi trên chuyến này; số khác là quét nhầm hoặc ngựa không đúng hồ sơ
                // So sau khi bỏ dấu cách/gạch nối; ghi lại đúng mã trong hồ sơ để bước xuất phát đối chiếu
                String scannedChip = MicrochipPolicy.normalize(chip);
                String recordedChip = BookingTripRules.horsesOnTrip(payload, targetTrip).stream()
                        .map(BookingHorseDto::getMicrochip)
                        .filter(m -> m != null && scannedChip.equals(MicrochipPolicy.normalize(m)))
                        .map(m -> m.trim().toUpperCase())
                        .findFirst().orElse(null);
                if (recordedChip == null) {
                    throw new IllegalArgumentException("Chip " + chip + " không khớp ngựa nào trên chuyến này. "
                            + "Quét lại. Nếu vẫn sai, tài xế báo \"Chip không khớp hồ sơ\" cho con ngựa đó.");
                }
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                String pickupLoc = booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()
                        ? booking.getPickupAddress() : "Điểm đón";
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    cp.put("type", "pickup");
                    cp.put("label", "Điểm đón");
                    cp.put("place", pickupLoc);
                    checkpoints.add(cp);
                } else {
                    Map<String, Object> first = checkpoints.get(0);
                    first.putIfAbsent("id", "pickup");
                    first.putIfAbsent("type", "pickup");
                    first.putIfAbsent("label", "Điểm đón");
                    first.putIfAbsent("place", pickupLoc);
                }
                Map<String, Object> pickup = checkpoints.get(0);
                @SuppressWarnings("unchecked")
                List<String> chips = (List<String>) pickup.computeIfAbsent("chips", k -> new ArrayList<>());
                if (!chips.contains(recordedChip)) {
                    chips.add(recordedChip);
                }
                targetTrip.setRun(run);
                BookingHistory.appendWithDefaultActor(payload, actor, "Quét mã microchip");
                break;
            }
            case "EXCLUDE_UNFIT_HORSE": {
                // Chỉ loại ngựa ở điểm đón; xe đã chạy thì xử lý sức khỏe ngựa bằng báo sự cố
                if (targetTrip.getRun() != null && targetTrip.getRun().get("startedAt") != null) {
                    throw new IllegalStateException("Hành trình đã bắt đầu, không loại ngựa ở bước nhận ngựa được nữa. Hãy báo sự cố sức khỏe ngựa.");
                }
                String horseId = String.valueOf(reqPayload.getOrDefault("horseId", "")).trim();
                String reason = String.valueOf(reqPayload.getOrDefault("reason", "Ốm / Sốt không đủ điều kiện khởi hành")).trim();
                String symptoms = String.valueOf(reqPayload.getOrDefault("symptoms", "")).trim();
                String photo = String.valueOf(reqPayload.getOrDefault("photo", "")).trim();
                Double temp = reqPayload.get("temperature") != null ? Double.valueOf(reqPayload.get("temperature").toString()) : null;
                BigDecimal discountAmount = BigDecimal.ZERO;
                if (reqPayload.get("discountAmount") != null) {
                    try {
                        discountAmount = new BigDecimal(reqPayload.get("discountAmount").toString());
                    } catch (NumberFormatException ex) {
                        // Số tiền sai thì dừng, không âm thầm giảm trừ 0 đồng
                        throw new IllegalArgumentException("Số tiền giảm trừ không hợp lệ: " + reqPayload.get("discountAmount"));
                    }
                }

                // 1. Cập nhật trạng thái cá thể ngựa trong payload
                String horseName = horseId;
                if (payload.getHorses() != null) {
                    for (BookingHorseDto h : payload.getHorses()) {
                        if (horseId.equalsIgnoreCase(h.getHorseId())) {
                            horseName = h.getName() != null ? h.getName() : horseId;
                            h.setStatus("UNFIT");
                            Map<String, Object> unfitInfo = new HashMap<>();
                            unfitInfo.put("reason", reason);
                            unfitInfo.put("symptoms", symptoms);
                            unfitInfo.put("photo", photo);
                            if (temp != null) {
                                unfitInfo.put("temperature", temp);
                            }
                            unfitInfo.put("at", now);
                            unfitInfo.put("by", actor);
                            unfitInfo.put("discountAmount", discountAmount.doubleValue());
                            h.setUnfitInfo(unfitInfo);
                            break;
                        }
                    }
                }

                // 2. Loại bỏ cá thể ngựa khỏi trip.horseIds và pickup chips
                if (targetTrip.getHorseIds() != null) {
                    targetTrip.getHorseIds().removeIf(id -> id.equalsIgnoreCase(horseId));
                }
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                if (!checkpoints.isEmpty()) {
                    Map<String, Object> pickup = checkpoints.get(0);
                    if (payload.getHorses() != null) {
                        for (BookingHorseDto h : payload.getHorses()) {
                            if (horseId.equalsIgnoreCase(h.getHorseId()) && h.getMicrochip() != null) {
                                @SuppressWarnings("unchecked")
                                List<String> chips = (List<String>) pickup.get("chips");
                                if (chips != null) {
                                    chips.remove(h.getMicrochip().toUpperCase());
                                }
                            }
                        }
                    }
                }
                targetTrip.setRun(run);

                // 3. Giảm trừ chi phí cho khách hàng
                if (discountAmount.compareTo(BigDecimal.ZERO) > 0 && payload.getQuote() instanceof Map<?, ?> rawQuoteMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> quoteMap = new HashMap<>((Map<String, Object>) rawQuoteMap);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> adjustments = new ArrayList<>();
                    if (quoteMap.get("adjustments") instanceof List<?> rawList) {
                        for (Object o : rawList) {
                            if (o instanceof Map<?, ?> m) {
                                adjustments.add(new HashMap<>((Map<String, Object>) m));
                            }
                        }
                    }

                    Map<String, Object> adj = new HashMap<>();
                    adj.put("label", "Giảm trừ không tiếp nhận ngựa " + horseName + " (" + reason + ")");
                    adj.put("amount", discountAmount.negate().doubleValue());
                    adjustments.add(adj);
                    quoteMap.put("adjustments", adjustments);

                    BigDecimal currentTotal = quoteMap.get("total") != null ? new BigDecimal(quoteMap.get("total").toString()) : BigDecimal.ZERO;
                    BigDecimal currentBalance = quoteMap.get("balance") != null ? new BigDecimal(quoteMap.get("balance").toString()) : BigDecimal.ZERO;
                    BigDecimal newTotal = currentTotal.subtract(discountAmount).max(BigDecimal.ZERO);
                    BigDecimal newBalance = currentBalance.subtract(discountAmount).max(BigDecimal.ZERO);

                    quoteMap.put("total", newTotal.doubleValue());
                    quoteMap.put("balance", newBalance.doubleValue());
                    payload.setQuote(quoteMap);

                    booking.setTotalBookingPrice(newTotal);
                    booking.setTotalHorses(Math.max(1, (booking.getTotalHorses() != null ? booking.getTotalHorses() : 1) - 1));

                    final BigDecimal finalDiscount = discountAmount;
                    quotationRepository.findTopByBookingIdOrderBySentAtDesc(booking.getId()).ifPresent(q -> {
                        q.setTotalAmount(newTotal);
                        q.setBalanceAmount(newBalance);
                        BigDecimal existingAdj = q.getAdjustmentAmount() != null ? q.getAdjustmentAmount() : BigDecimal.ZERO;
                        q.setAdjustmentAmount(existingAdj.subtract(finalDiscount));
                        quotationRepository.save(q);
                    });
                }

                // 4. Ghi log cảnh báo khẩn cấp an sinh
                Log emergencyLog = new Log();

                emergencyLog.setBookingID(booking);
                emergencyLog.setLogType("EMERGENCY_HEALTH_ALERT");
                emergencyLog.setStatus("UNFIT_FOR_TRAVEL");
                emergencyLog.setNote("TỪ CHỐI TIẾP NHẬN NGỰA TẠI ĐIỂM ĐÓN [" + horseName + "]: " + reason
                        + (temp != null ? " | Thân nhiệt: " + temp + "°C" : "")
                        + (!symptoms.isBlank() ? " | Triệu chứng: " + symptoms : "")
                        + " | Giảm trừ chi phí khách: " + discountAmount + " VND");
                emergencyLog.setCreatedAt(Instant.now());
                logRepository.save(emergencyLog);

                // 5. Ghi lịch sử đơn hàng
                BookingHistory.appendWithDefaultActor(payload, actor, "Từ chối tiếp nhận ngựa");
                sendEmptyTripHome(booking, payload, targetTrip, now);
                markPickupFailedIfNoHorseLeft(booking, payload, actor, "Ngựa không đủ điều kiện sức khỏe");
                break;
            }
            case "CHIP_MISMATCH": {
                // Tài xế báo ngựa ở điểm đón không đúng hồ sơ: loại con đó khỏi chuyến, không giảm trừ tiền (lỗi phía khách)
                if (targetTrip.getRun() != null && targetTrip.getRun().get("startedAt") != null) {
                    throw new IllegalStateException("Hành trình đã bắt đầu, không báo chip không khớp được nữa.");
                }
                Map<String, Object> pickup = BookingTripRules.pickupCheckpoint(targetTrip);
                if (pickup == null || pickup.get("arrivedAt") == null) {
                    throw new IllegalStateException("Tài xế cần xác nhận đã đến điểm đón trước khi báo chip không khớp.");
                }
                String horseId = String.valueOf(reqPayload.getOrDefault("horseId", "")).trim();
                String chipRead = String.valueOf(reqPayload.getOrDefault("chipRead", "")).toUpperCase().trim();
                String note = String.valueOf(reqPayload.getOrDefault("note", "")).trim();
                if (chipRead.isEmpty()) throw new IllegalArgumentException("Nhập số chip đọc được trên máy quét.");
                BookingHorseDto horse = BookingTripRules.horsesOnTrip(payload, targetTrip).stream()
                        .filter(h -> horseId.equalsIgnoreCase(h.getHorseId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Ngựa này không còn trên chuyến."));
                if (horse.getMicrochip() != null && chipRead.equalsIgnoreCase(horse.getMicrochip().trim())) {
                    throw new IllegalArgumentException("Chip " + chipRead + " khớp hồ sơ của " + horse.getName() + ". Hộ tống quét lại để ghi nhận.");
                }
                horse.setStatus("CHIP_MISMATCH");
                Map<String, Object> info = new HashMap<>();
                info.put("reason", "Chip không khớp hồ sơ");
                info.put("chipExpected", horse.getMicrochip());
                info.put("chipRead", chipRead);
                info.put("note", note);
                info.put("at", now);
                info.put("by", actor);
                horse.setUnfitInfo(info);
                targetTrip.getHorseIds().removeIf(id -> id.equalsIgnoreCase(horseId));
                if (horse.getMicrochip() != null && pickup.get("chips") instanceof List<?> chips) {
                    chips.remove(horse.getMicrochip().toUpperCase());
                }
                BookingHistory.appendWithDefaultActor(payload, actor, "Báo chip không khớp hồ sơ: " + horse.getName()
                        + " (hồ sơ " + horse.getMicrochip() + ", đọc được " + chipRead + ")");
                sendEmptyTripHome(booking, payload, targetTrip, now);
                markPickupFailedIfNoHorseLeft(booking, payload, actor, "Chip ngựa không khớp hồ sơ");
                break;
            }
            case "COLLECT_ORIGINALS": {
                Object items = reqPayload.get("items");
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                String pickupLoc = booking.getPickupAddress() != null && !booking.getPickupAddress().isBlank()
                        ? booking.getPickupAddress() : "Điểm đón";
                if (checkpoints.isEmpty()) {
                    Map<String, Object> cp = new HashMap<>();
                    cp.put("id", "pickup");
                    cp.put("type", "pickup");
                    cp.put("label", "Điểm đón");
                    cp.put("place", pickupLoc);
                    checkpoints.add(cp);
                } else {
                    Map<String, Object> first = checkpoints.get(0);
                    first.putIfAbsent("id", "pickup");
                    first.putIfAbsent("type", "pickup");
                    first.putIfAbsent("label", "Điểm đón");
                    first.putIfAbsent("place", pickupLoc);
                }
                checkpoints.get(0).put("originals", items);
                targetTrip.setRun(run);
                BookingHistory.appendWithDefaultActor(payload, actor, "Tài xế đã thu đủ hồ sơ gốc");
                break;
            }
            case "UPLOAD_HANDOVER": {
                String photo = String.valueOf(reqPayload.getOrDefault("photo", ""));
                String checkpointId = String.valueOf(reqPayload.getOrDefault("checkpointId", "pickup"));
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                Map<String, Object> handoverCheckpoint = checkpoints.stream()
                        .filter(cp -> checkpointId.equals(String.valueOf(cp.get("id"))))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy điểm bàn giao: " + checkpointId));
                if (!"pickup".equals(handoverCheckpoint.get("type")) && !"delivery".equals(handoverCheckpoint.get("type"))) {
                    throw new IllegalStateException("Chỉ nhận biên bản bàn giao tại điểm đón hoặc điểm giao.");
                }
                if (BookingTripRules.isBlank(photo)) throw new IllegalArgumentException("Cần ảnh biên bản bàn giao có chữ ký.");
                handoverCheckpoint.put("handoverPhoto", photo);
                targetTrip.setRun(run);
                BookingHistory.appendWithDefaultActor(payload, actor, "Tải ảnh biên bản giao nhận có chữ ký");
                break;
            }
            case "START_JOURNEY": {
                if ("returning_horses".equals(booking.getStatus()) || "cancelled".equals(booking.getStatus())) {
                    throw new IllegalStateException("Đơn hàng đã hủy, không thể bắt đầu vận chuyển.");
                }
                HorseInsurancePolicy.requireDepartureReady(payload.getHorses(), targetTrip.getHorseIds(), java.time.Instant.now());
                validateJourneyStart(booking, payload, targetTrip);
                booking.setStatus("in_transit");
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                List<Map<String, Object>> checkpoints = BookingTripRules.ensureRouteCheckpoints(booking, payload, targetTrip);
                if (checkpoints.isEmpty() || !"pickup".equals(String.valueOf(checkpoints.get(0).get("type")))) {
                    throw new IllegalStateException("Chuyến chưa có điểm đón.");
                }
                checkpoints.get(0).put("doneAt", now);
                run.put("startedAt", now);
                run.put("checkpoints", checkpoints);
                targetTrip.setRun(run);
                BookingHistory.appendWithDefaultActor(payload, actor, "Bắt đầu vận chuyển");

                // Chỉ chuyển những ngựa còn được nhận lên chuyến này sang IN_TRANSIT.
                for (BookingHorseDto hDto : BookingTripRules.horsesOnTrip(payload, targetTrip)) {
                    if (hDto.getHorseId() != null && !hDto.getHorseId().isBlank()) {
                        try {
                            String numId = hDto.getHorseId().replaceAll("\\D", "");
                            if (!numId.isEmpty()) {
                                Integer hId = Integer.parseInt(numId);
                                horseRepository.findById(hId).ifPresent(h -> {
                                    h.setTransitStatus("IN_TRANSIT");
                                    horseRepository.save(h);
                                });
                            }
                        } catch (Exception ex) {
                            log.warn("Không thể cập nhật transitStatus cho ngựa {}: {}", hDto.getHorseId(), ex.getMessage());
                        }
                    }
                }
                break;
            }
            case "ARRIVE_CHECKPOINT": {
                String cpId = String.valueOf(reqPayload.getOrDefault("checkpointId", ""));
                String photo = String.valueOf(reqPayload.getOrDefault("photo", ""));
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> checkpoints = (List<Map<String, Object>>) run.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                if (run.get("startedAt") == null) throw new IllegalStateException("Hành trình chưa bắt đầu.");
                if (photo.isBlank()) throw new IllegalArgumentException("Cần chụp ảnh tại điểm dừng.");
                Map<String, Object> arrivedCheckpoint = null;
                for (Map<String, Object> cp : checkpoints) {
                    if (cpId.equals(cp.get("id"))) {
                        arrivedCheckpoint = cp;
                        break;
                    }
                }
                if (arrivedCheckpoint == null) throw new IllegalArgumentException("Không tìm thấy điểm dừng: " + cpId);
                if ("pickup".equals(arrivedCheckpoint.get("type")) || "customs".equals(arrivedCheckpoint.get("type"))) {
                    throw new IllegalStateException("Điểm dừng này phải hoàn tất bằng thao tác riêng của nó.");
                }
                Map<String, Object> expectedCheckpoint = checkpoints.stream()
                        .filter(cp -> cp.get("doneAt") == null)
                        .findFirst()
                        .orElse(null);
                if (expectedCheckpoint == null || !cpId.equals(String.valueOf(expectedCheckpoint.get("id")))) {
                    throw new IllegalStateException("Hãy báo tới điểm dừng kế tiếp chưa hoàn tất theo đúng thứ tự lộ trình.");
                }
                arrivedCheckpoint.put("arrivedAt", now);
                arrivedCheckpoint.put("photo", photo);
                if ("border".equals(arrivedCheckpoint.get("type"))) arrivedCheckpoint.put("doneAt", now);
                targetTrip.setRun(run);
                BookingHistory.appendWithDefaultActor(payload, actor, "Xe tới trạm dừng");
                break;
            }
            case "SUBMIT_WELFARE": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> welfareList = (List<Map<String, Object>>) run.computeIfAbsent("welfare", k -> new ArrayList<>());
                String checkpointId = String.valueOf(reqPayload.getOrDefault("checkpointId", ""));
                if (run.get("startedAt") == null) throw new IllegalStateException("Hành trình chưa bắt đầu.");
                Map<String, Object> checkpoint = BookingTripRules.checkpointMaps(run.get("checkpoints")).stream()
                        .filter(cp -> checkpointId.equals(String.valueOf(cp.get("id"))))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy điểm ghi nhật ký sức khỏe: " + checkpointId));
                if (checkpoint.get("incidentId") != null)
                    throw new IllegalStateException("Ghi kết quả chăm sóc và xác nhận sức khỏe trong thẻ SOS.");
                if (!"rest".equals(checkpoint.get("type")) && !"delivery".equals(checkpoint.get("type"))) {
                    throw new IllegalStateException("Chỉ ghi nhật ký sức khỏe tại trạm dừng nghỉ hoặc điểm giao.");
                }
                Map<String, Object> expectedWelfareCheckpoint = BookingTripRules.checkpointMaps(run.get("checkpoints")).stream()
                        .filter(cp -> cp.get("doneAt") == null)
                        .findFirst().orElse(null);
                if (expectedWelfareCheckpoint == null || !checkpointId.equals(String.valueOf(expectedWelfareCheckpoint.get("id")))) {
                    throw new IllegalStateException("Hãy ghi nhật ký sức khỏe tại điểm dừng hiện tại của lộ trình.");
                }
                if (checkpoint.get("arrivedAt") == null) throw new IllegalStateException("Hãy báo tới điểm dừng này trước khi ghi nhật ký sức khỏe.");
                if (BookingTripRules.isBlank(reqPayload.get("photo"))) throw new IllegalArgumentException("Cần ảnh kèm nhật ký sức khỏe.");
                if (welfareList.stream().anyMatch(item -> checkpointId.equals(String.valueOf(item.get("checkpointId"))))) {
                    throw new IllegalStateException("Điểm dừng này đã có nhật ký sức khỏe.");
                }
                Map<String, Object> w = new HashMap<>(reqPayload);
                w.put("id", "welfare-" + now);
                w.put("checkpointId", checkpointId);
                w.put("at", now);
                w.put("by", actor);
                welfareList.add(w);
                targetTrip.setRun(run);
                BookingHistory.appendWithDefaultActor(payload, actor, "Hộ tống gửi nhật ký an sinh ngựa");
                break;
            }
            case "CONTINUE_JOURNEY": {
                Map<String, Object> journeyRun = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                if (journeyRun.get("startedAt") == null) throw new IllegalStateException("Hành trình chưa bắt đầu.");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> journeyCheckpoints = (List<Map<String, Object>>) journeyRun.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                Map<String, Object> customsStop = journeyCheckpoints.stream()
                        .filter(cp -> "customs".equals(cp.get("type")) && cp.get("doneAt") != null && cp.get("leftAt") == null)
                        .findFirst().orElse(null);
                if (customsStop != null) {
                    customsStop.put("leftAt", now);
                } else {
                    Map<String, Object> restStop = journeyCheckpoints.stream()
                            .filter(cp -> "rest".equals(cp.get("type")) && cp.get("arrivedAt") != null && cp.get("doneAt") == null)
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Không có trạm dừng nghỉ nào đang chờ đi tiếp."));
                    Map<String, Object> currentStop = journeyCheckpoints.stream()
                            .filter(cp -> cp.get("doneAt") == null)
                            .findFirst().orElse(null);
                    if (currentStop != restStop) throw new IllegalStateException("Hãy đi tiếp từ điểm dừng hiện tại của lộ trình.");
                    if (restStop.get("incidentId") != null)
                        throw new IllegalStateException("Rời trạm SOS bằng chức năng tiếp tục phương án sự cố sau khi hộ tống xác nhận sức khỏe.");
                    String restId = String.valueOf(restStop.get("id"));
                    List<Map<String, Object>> welfare = BookingTripRules.mapList(journeyRun.get("welfare"));
                    if (welfare.stream().noneMatch(item -> restId.equals(String.valueOf(item.get("checkpointId"))))) {
                        throw new IllegalStateException("Hộ tống phải ghi nhật ký sức khỏe ngựa trước khi rời trạm dừng nghỉ.");
                    }
                    restStop.put("doneAt", now);
                    restStop.put("leftAt", now);
                }
                targetTrip.setRun(journeyRun);
                BookingHistory.appendWithDefaultActor(payload, actor, "Xe tiếp tục hành trình");
                break;
            }
            case "CUSTOMS_CLEARED": {
                Map<String, Object> customsRun = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> customsCheckpoints = (List<Map<String, Object>>) customsRun.computeIfAbsent("checkpoints", k -> new ArrayList<>());
                Map<String, Object> customs = customsCheckpoints.stream()
                        .filter(cp -> "customs".equals(cp.get("type")) && cp.get("doneAt") == null)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Không có cửa khẩu nào đang chờ thông quan."));
                Map<String, Object> expectedCustoms = customsCheckpoints.stream()
                        .filter(cp -> cp.get("doneAt") == null)
                        .findFirst().orElse(null);
                if (expectedCustoms != customs) throw new IllegalStateException("Hãy tới cửa khẩu trước khi làm thủ tục thông quan.");
                Object stampPhotos = reqPayload.get("stampPhotos");
                if (!(stampPhotos instanceof List<?> photos) || photos.isEmpty()) {
                    throw new IllegalArgumentException("Cần chứng từ hải quan đã đóng dấu.");
                }
                customs.put("stampPhotos", stampPhotos);
                customs.putIfAbsent("arrivedAt", now);
                customs.put("doneAt", now);
                targetTrip.setRun(customsRun);
                BookingHistory.appendWithDefaultActor(payload, actor, "Hoàn tất thông quan");
                break;
            }
            case "RETURN_ORIGINALS": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                if (run.get("startedAt") == null) throw new IllegalStateException("Hành trình chưa bắt đầu.");
                String checkpointId = String.valueOf(reqPayload.getOrDefault("checkpointId", "delivery"));
                List<Map<String, Object>> checkpoints = BookingTripRules.checkpointMaps(run.get("checkpoints"));
                Map<String, Object> delivery = checkpoints.stream()
                        .filter(cp -> "delivery".equals(cp.get("type")) && checkpointId.equals(String.valueOf(cp.get("id"))))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Chuyến chưa có điểm giao."));
                if (delivery.get("arrivedAt") == null) {
                    throw new IllegalStateException("Hãy báo tới điểm giao trước khi xác nhận đã trả hồ sơ gốc.");
                }
                if (delivery.get("doneAt") != null) {
                    throw new IllegalStateException("Đã giao xong.");
                }
                boolean returned = Boolean.TRUE.equals(reqPayload.get("returnedOriginals"));
                delivery.put("returnedOriginals", returned);
                if (returned) {
                    delivery.put("returnedOriginalsAt", now);
                    delivery.put("returnedOriginalsBy", actor);
                } else {
                    delivery.remove("returnedOriginalsAt");
                    delivery.remove("returnedOriginalsBy");
                }
                run.put("checkpoints", checkpoints);
                targetTrip.setRun(run);
                BookingHistory.appendWithDefaultActor(payload, actor, returned
                        ? "Xác nhận trả chứng từ gốc"
                        : "Hủy xác nhận trả chứng từ gốc");
                break;
            }
            case "COMPLETE_DELIVERY": {
                Map<String, Object> run = targetTrip.getRun() != null ? targetTrip.getRun() : new HashMap<>();
                if (run.get("startedAt") == null) throw new IllegalStateException("Hành trình chưa bắt đầu.");
                Map<String, Object> delivery = BookingTripRules.checkpointMaps(run.get("checkpoints")).stream()
                        .filter(cp -> "delivery".equals(cp.get("type")))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Chuyến chưa có điểm giao."));
                Map<String, Object> expectedDelivery = BookingTripRules.checkpointMaps(run.get("checkpoints")).stream()
                        .filter(cp -> cp.get("doneAt") == null)
                        .findFirst().orElse(null);
                if (expectedDelivery == null || !"delivery".equals(expectedDelivery.get("type"))) {
                    throw new IllegalStateException("Hãy hoàn tất các điểm dừng trước đó trên lộ trình trước khi giao.");
                }
                if (delivery.get("arrivedAt") == null || BookingTripRules.isBlank(delivery.get("photo"))) {
                    throw new IllegalStateException("Hãy báo tới điểm giao kèm ảnh trước khi hoàn tất giao.");
                }
                if (BookingTripRules.isBlank(delivery.get("handoverPhoto"))) {
                    throw new IllegalStateException("Hãy tải lên biên bản bàn giao có chữ ký trước khi hoàn tất giao.");
                }
                if (!Boolean.TRUE.equals(delivery.get("returnedOriginals"))) {
                    throw new IllegalStateException("Hãy xác nhận đã trả hồ sơ gốc trước khi hoàn tất giao.");
                }
                List<Map<String, Object>> deliveryWelfare = BookingTripRules.mapList(run.get("welfare"));
                if (deliveryWelfare.stream().noneMatch(item -> "delivery".equals(String.valueOf(item.get("checkpointId"))))) {
                    throw new IllegalStateException("Hộ tống phải ghi nhật ký sức khỏe ngựa lần cuối trước khi hoàn tất giao.");
                }
                run.put("deliveredAt", now);
                // Người nhận tới muộn và ngựa đã được giao: đóng sự cố quá hạn bàn giao của chuyến
                for (Incident timeout : incidentRepository.findByBookingID_Id(booking.getId())) {
                    if ("HANDOVER_TIMEOUT".equalsIgnoreCase(timeout.getIncidentType()) && "active".equalsIgnoreCase(timeout.getStatus())
                            && (timeout.getTripId() == null || timeout.getTripId().equals(targetTrip.getTripId()))) {
                        timeout.setStatus("resolved");
                        timeout.setResolvedAt(Instant.ofEpochMilli(now));
                        incidentRepository.save(timeout);
                    }
                }
                Object deliveryCheckpoints = run.get("checkpoints");
                if (deliveryCheckpoints instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> cp && "delivery".equals(cp.get("type"))) {
                            @SuppressWarnings("unchecked") Map<Object, Object> mutable = (Map<Object, Object>) cp;
                            mutable.put("doneAt", now);
                        }
                    }
                }
                targetTrip.setRun(run);

                Map<String, Object> hr = new HashMap<>();
                hr.put("fromPlace", booking.getDropoffAddress() != null ? booking.getDropoffAddress() : "Điểm giao");
                hr.put("earliestAt", now + 1000);
                hr.put("eta", now + 7200000L);
                targetTrip.setHomeReturn(hr);

                BookingHistory.appendWithDefaultActor(payload, actor, "Hoàn tất bàn giao ngựa");
                deliveryService.completeTrip(booking, payload, targetTrip,
                        actor, String.valueOf(delivery.get("handoverPhoto")), now);
                break;
            }
            case "RETURN_HORSES": {
                // Quản lý hủy chuyến khi ngựa đã trên xe: kíp trả lại đúng số ngựa và hồ sơ gốc đã nhận ở điểm đón, có biên bản
                if (!"returning_horses".equals(booking.getStatus())) {
                    throw new IllegalStateException("Đơn không ở bước chờ trả ngựa.");
                }
                // Lấy đúng map trong run (pickupCheckpoint trả bản sao) để ghi biên bản trả ngựa vào chuyến
                Map<String, Object> pickup = null;
                if (targetTrip.getRun() != null && targetTrip.getRun().get("checkpoints") instanceof List<?> rawCheckpoints) {
                    for (Object raw : rawCheckpoints) {
                        if (raw instanceof Map<?, ?> cp && ("pickup".equals(cp.get("type")) || "pickup".equals(cp.get("id")))) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> live = (Map<String, Object>) cp;
                            pickup = live;
                            break;
                        }
                    }
                }
                if (pickup == null || BookingTripRules.horsesOnTrip(payload, targetTrip).isEmpty()) {
                    throw new IllegalStateException("Xe này không chở ngựa nào nên không có ngựa để trả.");
                }
                if (pickup.get("returnedAt") != null) throw new IllegalStateException("Xe này đã trả ngựa rồi.");
                String recipient = String.valueOf(reqPayload.getOrDefault("recipientName", "")).trim();
                String recipientPhone = String.valueOf(reqPayload.getOrDefault("recipientPhone", "")).trim();
                if (recipient.isEmpty()) throw new IllegalArgumentException("Nhập tên người nhận lại ngựa.");
                Set<String> returnedChips = new HashSet<>();
                for (String chip : BookingTripRules.stringList(reqPayload.get("chips"))) returnedChips.add(MicrochipPolicy.normalize(chip));
                List<String> received = BookingTripRules.stringList(pickup.get("chips"));
                List<String> missingChips = received.stream()
                        .filter(chip -> !returnedChips.contains(MicrochipPolicy.normalize(chip)))
                        .toList();
                if (!missingChips.isEmpty()) {
                    throw new IllegalArgumentException("Cần quét lại chip của mọi ngựa đã nhận trước khi trả. Còn thiếu: "
                            + String.join(", ", missingChips));
                }
                List<String> returnedOriginals = BookingTripRules.stringList(reqPayload.get("originals"));
                List<String> collected = BookingTripRules.stringList(pickup.get("originals"));
                if (!returnedOriginals.containsAll(collected)) {
                    throw new IllegalArgumentException("Cần trả đủ hồ sơ gốc đã nhận ở điểm đón.");
                }
                if (BookingTripRules.isBlank(reqPayload.get("photo"))) throw new IllegalArgumentException("Cần ảnh biên bản trả ngựa có chữ ký hai bên.");
                pickup.put("returnedAt", now);
                pickup.put("returnedBy", actor);
                pickup.put("returnRecipient", recipient);
                if (!recipientPhone.isEmpty()) pickup.put("returnRecipientPhone", recipientPhone);
                pickup.put("returnChips", new ArrayList<>(received));
                pickup.put("returnOriginals", new ArrayList<>(collected));
                pickup.put("returnPhoto", reqPayload.get("photo"));
                BookingHistory.appendWithDefaultActor(payload, actor, "Trả ngựa và hồ sơ gốc cho " + recipient + " tại điểm đón, có biên bản");

                boolean allReturned = trips.stream()
                        .filter(t -> !BookingTripRules.horsesOnTrip(payload, t).isEmpty())
                        .allMatch(t -> BookingTripRules.pickupCheckpoint(t) != null && BookingTripRules.pickupCheckpoint(t).get("returnedAt") != null);
                if (allReturned) {
                    booking.setStatus("cancelled");
                    // Single HQ: mọi xe đã rời trụ sở phải chạy về và xác nhận tại trụ sở mới được nhả
                    for (VehicleTripDto trip : trips) {
                        if (trip.getDepartedAt() == null || trip.getHomeReturn() != null) continue;
                        Map<String, Object> hr = new HashMap<>();
                        hr.put("fromPlace", booking.getPickupAddress() != null ? booking.getPickupAddress() : "Điểm đón");
                        hr.put("earliestAt", now + 1000);
                        hr.put("eta", now + 7200000L);
                        trip.setHomeReturn(hr);
                    }
                    BookingHistory.appendWithDefaultActor(payload, "Hệ thống", "Đã trả đủ ngựa và hồ sơ gốc. Đơn đã hủy, kíp xe về trụ sở");
                    staffNotifier.toManagers(booking, "Đã trả ngựa · " + booking.getCode(),
                            "Kíp xe đã trả ngựa và hồ sơ gốc cho người giao, có biên bản. Đơn đã đóng, xe đang về trụ sở.",
                            "/manager/cancelled-orders");
                    staffNotifier.toCustomer(booking, "Đã nhận lại ngựa · " + booking.getCode(),
                            "Kíp xe đã trả ngựa và hồ sơ gốc cho " + recipient + ", có biên bản trả ngựa. Chuyến đã hủy.");
                }
                break;
            }
            case "CONFIRM_HOME": {
                Map<String, Object> hr = targetTrip.getHomeReturn();
                if (hr == null) throw new IllegalStateException("Chưa giao ngựa xong nên chưa thể xác nhận về trụ sở.");
                if (BookingTripRules.number(hr.get("earliestAt"), 0) > now) throw new IllegalStateException("Chưa tới thời điểm sớm nhất có thể về trụ sở.");
                if (BookingTripRules.isBlank(reqPayload.get("photo"))) throw new IllegalArgumentException("Cần ảnh chụp tại trụ sở.");
                hr.put("backAt", now);
                hr.put("photo", reqPayload.get("photo"));

                if (targetTrip.getVehicleId() != null) {
                    try {
                        Integer vId = Integer.parseInt(targetTrip.getVehicleId().replaceAll("[^0-9]", ""));
                        vehicleRepository.findById(vId).ifPresent(v -> {
                            v.setStatus("AVAILABLE");
                            vehicleRepository.save(v);
                        });
                    } catch (NumberFormatException ex) {
                        log.warn("Không nhả được xe {} khi về trụ sở: mã xe không hợp lệ", targetTrip.getVehicleId());
                    }
                }
                BookingHistory.appendWithDefaultActor(payload, actor, "Xe về tới trụ sở chính");
                break;
            }
            case "ADD_TOLL": {
                requireTollWindow(targetTrip, role);
                Object rawAmount = reqPayload.get("amount");
                long amount;
                try {
                    amount = new java.math.BigDecimal(String.valueOf(rawAmount)).longValueExact();
                } catch (RuntimeException ex) {
                    throw new IllegalArgumentException("Số tiền phí cầu đường phải là số nguyên VNĐ.");
                }
                if (amount <= 0 || amount > MAX_TOLL_PER_RECEIPT) {
                    throw new IllegalArgumentException("Số tiền phí cầu đường phải lớn hơn 0 và không quá 10.000.000đ mỗi hóa đơn.");
                }
                String photo = reqPayload.get("photo") == null ? "" : String.valueOf(reqPayload.get("photo"));
                if (BookingTripRules.isBlank(photo)) throw new IllegalArgumentException("Cần ảnh chụp hóa đơn phí cầu đường.");
                String station = reqPayload.get("station") == null ? "" : String.valueOf(reqPayload.get("station")).trim();
                if (station.length() > 120) throw new IllegalArgumentException("Tên trạm thu phí tối đa 120 ký tự.");
                captureService.attach(payload, bookingId, targetTrip.getTripId(), "TOLL", "toll", photo, username);
                Map<String, Object> toll = new HashMap<>();
                toll.put("id", "TOLL-" + java.util.UUID.randomUUID().toString().substring(0, 8));
                toll.put("amount", amount);
                toll.put("station", station);
                toll.put("photo", photo);
                toll.put("at", now);
                toll.put("by", actor);
                List<Map<String, Object>> tolls = targetTrip.getTolls() != null ? new ArrayList<>(targetTrip.getTolls()) : new ArrayList<>();
                tolls.add(toll);
                targetTrip.setTolls(tolls);
                BookingHistory.appendWithDefaultActor(payload, actor, "Ghi phí cầu đường " + amount + "đ" + (station.isEmpty() ? "" : " · " + station));
                break;
            }
            case "REMOVE_TOLL": {
                requireTollWindow(targetTrip, role);
                String tollId = String.valueOf(reqPayload.getOrDefault("tollId", ""));
                List<Map<String, Object>> tolls = targetTrip.getTolls() != null ? new ArrayList<>(targetTrip.getTolls()) : new ArrayList<>();
                if (!tolls.removeIf(t -> tollId.equals(String.valueOf(t.get("id"))))) {
                    throw new IllegalArgumentException("Không tìm thấy khoản phí cầu đường cần xóa.");
                }
                targetTrip.setTolls(tolls);
                BookingHistory.appendWithDefaultActor(payload, actor, "Xóa một khoản phí cầu đường đã ghi");
                break;
            }
            default: {
                log.warn("Unknown trip action: {}", action);
            }
        }

        notifyBalanceDueOnce(booking, payload);
        captureService.attachTripPhotos(payload, bookingId, tripId, action, reqPayload, username);
        savePayload(booking, payload);
        Booking updated = bookingRepository.save(booking);
        logAction("TRIP_UPDATE_" + action, updated.getId(), actor + " thực hiện " + action + " cho chuyến " + tripId);
        return bookingQueryService.mapToResponse(updated);
    }

    // Nhà xe chịu phí cầu đường từ lúc xe rời trụ sở đến khi xe xác nhận về trụ sở.
    private void requireTollWindow(VehicleTripDto trip, String role) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Chỉ tài xế được ghi phí cầu đường.");
        }
        if (trip.getDepartedAt() == null) {
            throw new IllegalStateException("Xe chưa rời trụ sở nên chưa ghi phí cầu đường được.");
        }
        if (trip.getHomeReturn() != null && trip.getHomeReturn().get("backAt") != null) {
            throw new IllegalStateException("Xe đã về trụ sở, không ghi thêm hoặc sửa phí cầu đường được nữa.");
        }
    }

    private void validateTripUpdateActor(Booking targetBooking, VehicleTripDto targetTrip,
            String action, String username, String role) {
        if (!"DRIVER".equalsIgnoreCase(role) && !"ESCORT".equalsIgnoreCase(role)) return;

        Staff staff = staffRepository.findByUser_Username(username)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Không tìm thấy hồ sơ nhân sự của tài khoản đăng nhập."));
        if (staff.getRole() == null || !staff.getRole().equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Vai trò của tài khoản không khớp với hồ sơ nhân sự.");
        }
        String staffCode = staff.getStaffCode() != null ? staff.getStaffCode() : "";
        String staffId = staff.getId() != null ? staff.getId().toString() : "";
        String staffName = staff.getUser() != null && staff.getUser().getUsername() != null
                ? staff.getUser().getUsername() : "";
        String assigned = "DRIVER".equalsIgnoreCase(role) ? targetTrip.getDriverId() : targetTrip.getEscortId();
        if (assigned == null || !BookingTripRules.matchesStaffIdentifier(assigned, staffCode, staffId, username, staffName, role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không được phân công cho chuyến xe này.");
        }

        String normalizedAction = action.toUpperCase(java.util.Locale.ROOT);
        if (DRIVER_ACTIONS.contains(normalizedAction) && !"DRIVER".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Thao tác này do tài xế của chuyến thực hiện.");
        }
        if (ESCORT_ACTIONS.contains(normalizedAction) && !"ESCORT".equalsIgnoreCase(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Thao tác này do hộ tống của chuyến thực hiện.");
        }
        if (!"DEPART_TO_PICKUP".equals(normalizedAction) && !"ARRIVE_PICKUP".equals(normalizedAction)) return;

        for (Booking booking : bookingRepository.findAll()) {
            BookingDetailsPayloadDto payload = BookingDetailsJson.readWithMedicalDefaults(objectMapper, booking);
            if (payload.getTrips() == null) continue;
            String status = booking.getStatus() == null ? "" : booking.getStatus().toLowerCase(java.util.Locale.ROOT);
            for (VehicleTripDto trip : payload.getTrips()) {
                if (booking.getId().equals(targetBooking.getId())
                        && java.util.Objects.equals(trip.getTripId(), targetTrip.getTripId())) continue;
                if (!BookingTripRules.matchesStaffIdentifier(trip.getDriverId(), staffCode, staffId, username, staffName, role)) continue;
                // Chỉ xét chuyến của chính tài xế: đã về trụ sở thì rảnh dù xe khác của đơn còn chạy
                if (trip.getHomeReturn() != null && trip.getHomeReturn().get("backAt") != null) continue;
                boolean returning = trip.getHomeReturn() != null;
                boolean active = trip.getDepartedAt() != null && Set.of("en_route_to_pickup", "pickup_failed", "returning_horses", "in_transit",
                        "incident_reported", "pending_emergency_approval", "emergency_plan_active",
                        "arrived", "handover_timeout", "returning_to_hq", "delivered_pending_settlement").contains(status);
                if (returning || active) {
                    throw new IllegalStateException("Bạn đang thực hiện chuyến "
                            + booking.getCode()
                            + " và chưa xác nhận về trụ sở, không thể thao tác chuyến khác.");
                }
            }
        }
    }

    private Integer parseBookingId(String bookingIdentifier) {
        if (bookingIdentifier == null || bookingIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống!");
        }
        return bookingRepository.findIdByCode(bookingIdentifier.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng: " + bookingIdentifier));
    }

    private void logAction(String actionType, Integer targetRecordId, String newData) {
        try {
            operationalLog.record(actionType, "BOOKING", targetRecordId, newData, null);
        } catch (Exception ex) {
            log.warn("Failed to write operational log for action {}: {}", actionType, ex.getMessage());
        }
    }

    private void savePayload(Booking booking, BookingDetailsPayloadDto payload) {
        try {
            booking.setDetailsJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể lưu chi tiết đơn " + booking.getId() + ".", ex);
        }
    }
}
