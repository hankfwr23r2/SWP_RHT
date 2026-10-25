package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.util.BookingStatusLabels;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private String id; // "BK-001"
    private Integer numericId; // 1
    private String type; // "domestic", "international"
    private String customer; // username
    private String customerName; // Họ tên khách hàng
    private String status; // "pending_intake", "under_review", etc.
    private Long createdAt; // timestamp ms
    private Long departAt; // timestamp ms

    private String originName;
    private String originCountry;
    private String destName;
    private String destCountry;

    private PartyContactDto consignor;
    private PartyContactDto consignee;

    private Integer totalHorses;
    private Integer vehicleCount;
    private List<BookingHorseDto> horses;
    private String waybillNo;

    private List<com.example.racehorse_transport.dto.coordinator.VehicleTripDto> trips;
    private Integer routeId;
    private Object route;
    private String gate;
    private Object plan;
    private Object quote;
    private Object payment;
    // Việc khách được làm, do backend quyết định (CustomerBookingRules); frontend chỉ hiện nút theo các cờ này
    private Boolean canCancel;
    private Boolean canPayBalance;
    private java.math.BigDecimal cancelPenalty; // số tiền cọc mất nếu hủy lúc này
    private Object medical;
    private Object clearance;
    private Object settlement;
    private Object rating;
    private Object intake;
    private Object rejection;
    private List<java.util.Map<String, Object>> history;

    // Nhãn của trạng thái hiện tại, lấy từ BookingStatusLabels; mọi nơi dựng BookingResponse đều có sẵn
    public String getStatusLabel() {
        return BookingStatusLabels.staffLabel(status);
    }

    public String getCustomerStatusLabel() {
        return BookingStatusLabels.customerLabel(status);
    }
}
