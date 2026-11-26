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
    // Rỗng khi chạy thẳng, hoặc khi không có chuyến phù hợp
    private List<DepartureResponseDto> departures;
}
