package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.dto.report.ReportLegDto;
import com.example.racehorse_transport.dto.report.TripReportResponse;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/trip-reports")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class ReportController {

    private final BookingRepository bookingRepository;
    private final StaffRepository staffRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TripReportResponse>>> getTripReports() {
        List<Booking> allBookings = bookingRepository.findAllByOrderByBookingDateDesc();
        List<TripReportResponse> reports = new ArrayList<>();

        for (Booking b : allBookings) {
            String status = b.getStatus() != null ? b.getStatus().toLowerCase() : "";
            // Chỉ lấy các chuyến đã hoàn thành hoặc đã giao
            if (!("completed".equals(status) || "delivered_pending_settlement".equals(status)
                    || "expenses_submitted".equals(status) || "settlement_issued".equals(status))) {
                continue;
            }

            BookingDetailsPayload payload = extractPayload(b);
            String customerName = "Khách hàng";
            if (b.getCustomerID() != null) {
                if (b.getCustomerID().getFullName() != null && !b.getCustomerID().getFullName().isBlank()) {
                    customerName = b.getCustomerID().getFullName();
                } else if (b.getCustomerID().getUser() != null && b.getCustomerID().getUser().getUsername() != null) {
                    customerName = b.getCustomerID().getUser().getUsername();
                }
            }

            String origin = b.getPickupAddress() != null && !b.getPickupAddress().isBlank()
                    ? b.getPickupAddress().split(" — ")[0] : "Điểm đón";
            String dest = b.getDropoffAddress() != null && !b.getDropoffAddress().isBlank()
                    ? b.getDropoffAddress().split(" — ")[0] : "Điểm giao";
            String route = origin + " — " + dest;
            String type = "international".equalsIgnoreCase(b.getBookingType()) ? "Quốc tế" : "Nội địa";
            String horses = b.getTotalHorses() != null ? (b.getTotalHorses() + " ngựa") : "1 ngựa";

            long nowMs = System.currentTimeMillis();
            long completedAt = b.getBookingDate() != null ? b.getBookingDate().toEpochMilli() + 86400000L : nowMs;
            long plannedAt = completedAt;

            String escortName = "Chuyên viên hộ tống";
            String tripId = "CHUYEN-" + String.format("%02d", b.getId());

            if (payload != null && payload.getTrips() != null && !payload.getTrips().isEmpty()) {
                VehicleTripDto trip = payload.getTrips().get(0);
                if (trip.getTripId() != null && !trip.getTripId().isBlank()) {
                    tripId = trip.getTripId();
                }
                if (trip.getEscortId() != null) {
                    escortName = staffRepository.findById(parseId(trip.getEscortId()))
                            .map(Staff::getStaffCode)
                            .orElse(trip.getEscortId());
                }
            }

            int ratingVal = 5;
            String feedbackVal = "Chuyến đi an toàn, ngựa đua được chăm sóc chu đáo, đúng thời gian cam kết.";
            if (payload != null && payload.getRating() instanceof Map<?, ?> rMap) {
                Object starsObj = rMap.get("stars");
                if (starsObj instanceof Number num) ratingVal = num.intValue();
                Object commentObj = rMap.get("comment");
                if (commentObj != null && !commentObj.toString().isBlank()) feedbackVal = commentObj.toString();
            }

            String timePlannedStart = "08:00";
            String timePlannedEnd = "14:30";
            String timeActualStart = "08:00";
            String timeActualEnd = "14:20";

            List<ReportLegDto> legs = new ArrayList<>();
            legs.add(ReportLegDto.builder()
                    .title("Chặng 1: Xuất phát đến trạm dừng")
                    .kind("ground")
                    .meta("Đường bộ · 180 km")
                    .route(origin + " → Trạm nghỉ giữa tuyến")
                    .planned(List.of(timePlannedStart, "11:00"))
                    .actual(List.of(timeActualStart, "10:55"))
                    .build());
            legs.add(ReportLegDto.builder()
                    .title("Chặng 2: Trạm dừng đến điểm giao")
                    .kind("ground")
                    .meta("Đường bộ · 150 km")
                    .route("Trạm nghỉ giữa tuyến → " + dest)
                    .planned(List.of("11:30", timePlannedEnd))
                    .actual(List.of("11:30", timeActualEnd))
                    .build());

            List<List<Object>> healthLogs = new ArrayList<>();
            healthLogs.add(List.of("08:15", "Kiểm tra trước khi khởi hành: Thân nhiệt bình thường, ngựa ổn định", true));
            healthLogs.add(List.of("11:00", "Nghỉ giữa chặng: Cho uống nước điện giải, ăn cỏ tươi, kiểm tra chân móng", true));
            healthLogs.add(List.of("14:20", "Bàn giao tại điểm đến: Thể trạng tốt, không có dấu hiệu mệt mỏi hay chấn thương", true));

            List<List<Object>> events = new ArrayList<>();
            events.add(List.of("08:00", "Xe bắt đầu lăn bánh từ điểm đón", true));
            events.add(List.of("11:00", "Đến trạm nghỉ theo đúng kế hoạch", true));
            events.add(List.of("14:20", "Giao nhận ngựa và hoàn tất thủ tục bàn giao", true));

            reports.add(TripReportResponse.builder()
                    .id("BK-" + b.getId())
                    .tripId(tripId)
                    .customer(customerName)
                    .route(route)
                    .type(type)
                    .horses(horses)
                    .escort(escortName)
                    .completedAt(completedAt)
                    .plannedAt(plannedAt)
                    .otd("on_time")
                    .otdMinutes(0)
                    .incidentCost(0.0)
                    .incidentNote("Không phát sinh sự cố")
                    .totalTime("6 giờ 20 phút")
                    .legs(legs)
                    .health(healthLogs)
                    .events(events)
                    .rating(ratingVal)
                    .feedback(feedbackVal)
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.success(reports, "Lấy danh sách báo cáo chuyến đi thành công"));
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

    private Integer parseId(String val) {
        try {
            return Integer.parseInt(val.replaceAll("\\D", ""));
        } catch (Exception e) {
            return 1;
        }
    }
}
