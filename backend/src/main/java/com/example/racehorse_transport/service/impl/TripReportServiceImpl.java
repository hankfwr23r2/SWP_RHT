package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.report.ReportLegDto;
import com.example.racehorse_transport.dto.report.TripReportResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.TripReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TripReportServiceImpl implements TripReportService {

    private final BookingRepository bookingRepository;
    private final StaffRepository staffRepository;
    private final IncidentRepository incidentRepository;
    private final ObjectMapper objectMapper;

    // Chuyến đã giao ngựa (kể cả đang quyết toán hoặc đã đóng đơn)
    private static final Set<String> REPORTED_STATUSES = Set.of("delivered_pending_settlement", "expenses_submitted",
            "settlement_issued", "payment_overdue", "completed");
    private static final long ON_TIME_TOLERANCE_MS = 15 * 60_000L; // lệch trong 15 phút vẫn tính đúng giờ
    private static final Map<String, String> CONDITION = Map.of(
            "normal", "Bình thường", "stress", "Căng thẳng", "sweating", "Đổ mồ hôi nhiều");

    // Báo cáo chỉ dựng từ dữ liệu thật của chuyến: mốc check-in, nhật ký an sinh, sự cố, phí cầu đường, điểm khách chấm.
    // Trường nào chuyến chưa có thì để trống, không điền giá trị mẫu.
    @Override
    @Transactional(readOnly = true)
    public List<TripReportResponse> findAll() {
        List<TripReportResponse> reports = new ArrayList<>();
        for (Booking b : bookingRepository.findAllByOrderByBookingDateDesc()) {
            String status = b.getStatus() != null ? b.getStatus().toLowerCase(Locale.ROOT) : "";
            if (!REPORTED_STATUSES.contains(status)) continue;
            reports.add(report(b, extractPayload(b)));
        }
        return reports;
    }

    private TripReportResponse report(Booking b, BookingDetailsPayload payload) {
        List<VehicleTripDto> trips = payload.getTrips() != null ? payload.getTrips() : List.of();
        boolean manyTrips = trips.size() > 1;

        List<ReportLegDto> legs = new ArrayList<>();
        List<List<Object>> health = new ArrayList<>();
        List<List<Object>> events = new ArrayList<>();
        List<Map<String, Object>> tolls = new ArrayList<>();
        Set<String> escorts = new LinkedHashSet<>();
        long tollCost = 0;
        Long startedAt = null, deliveredAt = null, plannedDelivery = null;

        for (int t = 0; t < trips.size(); t++) {
            VehicleTripDto trip = trips.get(t);
            String prefix = manyTrips ? "Xe " + (t + 1) + " · " : "";
            if (trip.getEscortId() != null) escorts.add(staffLabel(trip.getEscortId()));
            if (trip.getTolls() != null) {
                for (Map<String, Object> toll : trip.getTolls()) {
                    tolls.add(toll);
                    if (toll.get("amount") instanceof Number amount) tollCost += amount.longValue();
                }
            }
            Map<String, Object> run = trip.getRun();
            if (run == null) continue;
            Long started = num(run.get("startedAt"));
            Long delivered = num(run.get("deliveredAt"));
            startedAt = min(startedAt, started);
            deliveredAt = max(deliveredAt, delivered);
            if (started != null) events.add(List.of(started, prefix + "Bắt đầu hành trình", true));

            List<Map<String, Object>> checkpoints = maps(run.get("checkpoints"));
            Map<String, String> labels = new HashMap<>();
            for (int i = 0; i < checkpoints.size(); i++) {
                Map<String, Object> cp = checkpoints.get(i);
                String label = text(cp.get("label"), text(cp.get("place"), text(cp.get("id"), "Mốc " + (i + 1))));
                labels.put(text(cp.get("id"), ""), label);
                Long planned = num(cp.get("plannedAt"));
                Long arrived = num(cp.get("arrivedAt"));
                String type = text(cp.get("type"), "");
                if ("delivery".equals(type)) plannedDelivery = max(plannedDelivery, planned);
                if (arrived != null) {
                    boolean onTime = planned == null || arrived <= planned + ON_TIME_TOLERANCE_MS;
                    events.add(List.of(arrived, prefix + ("delivery".equals(type) ? "Giao ngựa tại " : "Tới ") + label, onTime));
                }
                if (i == 0) continue;
                Map<String, Object> prev = checkpoints.get(i - 1);
                String prevLabel = text(prev.get("label"), text(prev.get("place"), "Mốc " + i));
                Long leftPrev = firstNonNull(num(prev.get("leftAt")), num(prev.get("doneAt")), num(prev.get("arrivedAt")));
                legs.add(ReportLegDto.builder()
                        .title(prefix + "Chặng " + i)
                        .kind("customs".equals(type) || "border".equals(type) ? "gate" : "ground")
                        .meta(switch (type) {
                            case "customs", "border" -> "Đến cửa khẩu";
                            case "delivery" -> "Đến điểm giao";
                            default -> "Đến trạm nghỉ";
                        })
                        .route(prevLabel + " → " + label)
                        .plannedStart(num(prev.get("plannedAt")))
                        .plannedEnd(planned)
                        .actualStart(leftPrev)
                        .actualEnd(arrived)
                        .build());
            }
            for (Map<String, Object> w : maps(run.get("welfare"))) {
                Long at = num(w.get("at"));
                if (at == null) continue;
                String condition = text(w.get("condition"), "");
                StringBuilder line = new StringBuilder(prefix)
                        .append(labels.getOrDefault(text(w.get("checkpointId"), ""), "Trạm nghỉ")).append(": ")
                        .append(CONDITION.getOrDefault(condition, condition.isEmpty() ? "Chưa ghi tình trạng" : condition));
                if (w.get("waterLiters") instanceof Number water) line.append(", uống ").append(water).append(" lít nước");
                if (Boolean.TRUE.equals(w.get("hay"))) line.append(", có cho ăn cỏ khô");
                String note = text(w.get("note"), "");
                if (!note.isEmpty()) line.append(". ").append(note);
                health.add(List.of(at, line.toString(), "normal".equals(condition)));
            }
            if (trip.getHomeReturn() != null && num(trip.getHomeReturn().get("backAt")) != null) {
                events.add(List.of(num(trip.getHomeReturn().get("backAt")), prefix + "Xe và kíp về tới trụ sở", true));
            }
        }

        double incidentCost = 0;
        List<String> incidentNames = new ArrayList<>();
        for (Incident incident : incidentRepository.findByBookingID_Id(b.getId())) {
            String name = text(incident.getIncidentName(), "Sự cố");
            incidentNames.add(name);
            if (incident.getCreatedAt() != null) events.add(List.of(incident.getCreatedAt().toEpochMilli(), "Sự cố: " + name, false));
            double expenses = expenseTotal(incident.getExpensesJson());
            if (expenses == 0 && incident.getActualCost() != null) expenses = incident.getActualCost().doubleValue();
            incidentCost += expenses;
        }
        events.sort(Comparator.comparingLong(row -> ((Number) row.get(0)).longValue()));
        health.sort(Comparator.comparingLong(row -> ((Number) row.get(0)).longValue()));

        String otd = null;
        Integer otdMinutes = null;
        if (deliveredAt != null && plannedDelivery != null) {
            long diff = deliveredAt - plannedDelivery;
            otdMinutes = (int) Math.round(Math.abs(diff) / 60_000.0);
            otd = Math.abs(diff) <= ON_TIME_TOLERANCE_MS ? "on_time" : diff < 0 ? "early" : "late";
        }

        Integer rating = null;
        String feedback = null;
        if (payload.getRating() instanceof Map<?, ?> r) {
            if (r.get("stars") instanceof Number stars) rating = stars.intValue();
            String comment = text(r.get("comment"), "");
            if (!comment.isEmpty()) feedback = comment;
        }

        return TripReportResponse.builder()
                .id("BK-" + b.getId())
                .tripId(trips.stream().map(VehicleTripDto::getTripId).filter(Objects::nonNull).reduce((x, y) -> x + ", " + y).orElse(null))
                .customer(customerName(b))
                .route(placeName(b.getPickupAddress(), "Điểm đón") + " — " + placeName(b.getDropoffAddress(), "Điểm giao"))
                .type("international".equalsIgnoreCase(b.getBookingType()) ? "Quốc tế" : "Nội địa")
                .horses((b.getTotalHorses() != null ? b.getTotalHorses() : (payload.getHorses() != null ? payload.getHorses().size() : 0)) + " ngựa")
                .escort(escorts.isEmpty() ? null : String.join(", ", escorts))
                .completedAt(deliveredAt)
                .plannedAt(plannedDelivery)
                .otd(otd)
                .otdMinutes(otdMinutes)
                .incidentCost(incidentCost)
                .tollCost(tollCost)
                .tolls(tolls)
                .incidentNote(incidentNames.isEmpty() ? "Không có sự cố" : String.join(", ", incidentNames))
                .totalTime(startedAt != null && deliveredAt != null ? duration(deliveredAt - startedAt) : null)
                .legs(legs)
                .health(health)
                .events(events)
                .rating(rating)
                .feedback(feedback)
                .build();
    }

    private String customerName(Booking b) {
        if (b.getCustomerID() == null) return null;
        if (b.getCustomerID().getFullName() != null && !b.getCustomerID().getFullName().isBlank()) return b.getCustomerID().getFullName();
        return b.getCustomerID().getUser() != null ? b.getCustomerID().getUser().getUsername() : null;
    }

    private static String placeName(String address, String fallback) {
        return address != null && !address.isBlank() ? address.split(" — ")[0] : fallback;
    }

    // Hộ tống ghi trên chuyến bằng mã nhân sự hoặc mã số: hiện "tên đăng nhập (mã)"
    private String staffLabel(String assigned) {
        Optional<Staff> member = staffRepository.findByStaffCode(assigned);
        if (member.isEmpty() && assigned.matches("\\d+")) member = staffRepository.findById(Integer.parseInt(assigned));
        return member.map(s -> (s.getUser() != null ? s.getUser().getUsername() : assigned)
                + (s.getStaffCode() != null ? " (" + s.getStaffCode() + ")" : "")).orElse(assigned);
    }

    private double expenseTotal(String expensesJson) {
        if (expensesJson == null || expensesJson.isBlank()) return 0;
        try {
            double total = 0;
            for (var node : objectMapper.readTree(expensesJson)) total += node.path("amount").asDouble(0);
            return total;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String duration(long ms) {
        long minutes = Math.round(ms / 60_000.0);
        long h = minutes / 60, m = minutes % 60;
        return h > 0 ? h + " giờ " + m + " phút" : m + " phút";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) if (item instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
        return out;
    }

    private static Long num(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static String text(Object value, String fallback) {
        return value != null && !value.toString().isBlank() ? value.toString() : fallback;
    }

    private static Long min(Long a, Long b) {
        return a == null ? b : b == null ? a : Math.min(a, b);
    }

    private static Long max(Long a, Long b) {
        return a == null ? b : b == null ? a : Math.max(a, b);
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    private BookingDetailsPayload extractPayload(Booking booking) {
        if (booking.getDetailsJson() == null || booking.getDetailsJson().isBlank()) {
            return new BookingDetailsPayload();
        }
        try {
            return objectMapper.readValue(booking.getDetailsJson(), BookingDetailsPayload.class);
        } catch (Exception e) {
            return new BookingDetailsPayload();
        }
    }
}
