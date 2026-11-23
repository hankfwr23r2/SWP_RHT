package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.RouteStopId;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "RouteStopRequest")
public class RouteStopRequestDto {

    @NotNull(message = "Thiếu thứ tự điểm dừng")
    @Min(value = 1, message = "Thứ tự điểm dừng bắt đầu từ 1")
    private Integer seq;

    @NotNull(message = "Chọn địa điểm cho điểm dừng")
    private Long locationId;

    @NotNull(message = "Chọn loại điểm dừng")
    private RouteStopType stopType;

    // Số phút tính từ lúc xuất phát
    @NotNull(message = "Thiếu thời điểm tới điểm dừng")
    @Min(value = 0, message = "Số phút không được âm")
    private Integer offsetMinutes;

    @NotNull(message = "Thiếu thời gian dừng")
    @Min(value = 0, message = "Số phút không được âm")
    private Integer dwellMinutes;

    // Điểm dừng mới của tuyến đã lưu. Tuyến và địa điểm do service nạp rồi truyền vào.
    public RouteStop toEntity(Route route, Location location) {
        RouteStop stop = new RouteStop();
        stop.setId(new RouteStopId(route.getId(), seq));
        stop.setRoute(route);
        stop.setLocation(location);
        stop.setStopType(stopType);
        stop.setOffsetMinutes(offsetMinutes);
        stop.setDwellMinutes(dwellMinutes);
        return stop;
    }
}
