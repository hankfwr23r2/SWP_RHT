package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.policy.HubResolutionPolicy.Routing;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

// Kết quả tìm chuyến: chạy thẳng (không cần chọn chuyến), hoặc danh sách chuyến trục còn khoang.
@Getter
@Builder
@Schema(name = "DepartureSuggestionResponse")
public class DepartureSuggestionResponseDto {

    private Routing routing;
    // Hub phục vụ điểm đón, điểm giao; null ở phía nước ngoài của đơn quốc tế
    private String pickupHubCode;
    private String deliveryHubCode;
    private String pickupHubName;
    private String deliveryHubName;
    // Lý do không có tuyến nào để chọn; null khi có tuyến hoặc chạy thẳng
    private Unavailable unavailable;
    // Tỉnh đang có hub phục vụ, chỉ điền khi nơi đón hoặc nơi giao chưa có hub
    private List<String> servedProvinces;
    // Chuyến không còn đủ ngày làm giấy trước hạn nhận đặt (docs/NGHIEP_VU_THEO_59_BANG.md GĐ1), kèm số ngày cần
    private List<Long> tooLateDepartureIds;
    private Long leadDays;
    // Rỗng khi chạy thẳng, hoặc khi không có chuyến phù hợp
    private List<DepartureResponseDto> departures;

    public enum Unavailable { PICKUP_NOT_SERVED, DELIVERY_NOT_SERVED, NO_ROUTE }
}
