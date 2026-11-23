package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

// Tuyến kèm toàn bộ điểm dừng. Tạo mới và cập nhật dùng chung tập field; cập nhật thay toàn bộ điểm dừng.
@Getter
@Setter
@Schema(name = "RouteRequest")
public class RouteRequestDto {

    @NotBlank(message = "Mã tuyến không được để trống")
    @Size(max = 30, message = "Mã tuyến tối đa 30 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Mã tuyến chỉ gồm chữ, số và dấu gạch ngang")
    private String code;

    @NotBlank(message = "Tên tuyến không được để trống")
    @Size(max = 200, message = "Tên tuyến tối đa 200 ký tự")
    private String name;

    @NotNull(message = "Chọn điểm đầu tuyến")
    private Long originLocationId;

    @NotNull(message = "Chọn điểm cuối tuyến")
    private Long destinationLocationId;

    @NotNull(message = "Nhập quãng đường")
    @DecimalMin(value = "0.01", message = "Quãng đường phải lớn hơn 0")
    @Digits(integer = 6, fraction = 2, message = "Quãng đường tối đa 6 chữ số phần nguyên và 2 chữ số thập phân")
    private BigDecimal distanceKm;

    @NotNull(message = "Nhập thời gian chạy dự kiến")
    @Min(value = 1, message = "Thời gian chạy phải lớn hơn 0")
    private Integer estDurationMinutes;

    @Valid
    @NotNull(message = "Tuyến phải có điểm dừng")
    @Size(min = 2, message = "Tuyến phải có ít nhất 2 điểm dừng")
    private List<RouteStopRequestDto> stops;

    // Tuyến mới, chưa lưu. Điểm đầu, điểm cuối do service nạp; trạng thái do service đặt.
    public Route toEntity(Location origin, Location destination) {
        Route route = new Route();
        applyTo(route, origin, destination);
        return route;
    }

    public void applyTo(Route route, Location origin, Location destination) {
        route.setCode(code.trim().toUpperCase(Locale.ROOT));
        route.setName(name.trim());
        route.setOrigin(origin);
        route.setDestination(destination);
        route.setDistanceKm(distanceKm);
        route.setEstDurationMinutes(estDurationMinutes);
    }
}
