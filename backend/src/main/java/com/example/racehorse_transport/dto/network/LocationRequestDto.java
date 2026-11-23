package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.enums.LocationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;

// Tạo mới và cập nhật dùng chung tập field. Trạng thái do service đặt (tạo mới là ACTIVE; đổi bằng endpoint activate/deactivate).
@Getter
@Setter
@Schema(name = "LocationRequest")
public class LocationRequestDto {

    @NotBlank(message = "Mã địa điểm không được để trống")
    @Size(max = 30, message = "Mã địa điểm tối đa 30 ký tự")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Mã địa điểm chỉ gồm chữ, số và dấu gạch ngang")
    private String code;

    @NotBlank(message = "Tên địa điểm không được để trống")
    @Size(max = 200, message = "Tên địa điểm tối đa 200 ký tự")
    private String name;

    @NotNull(message = "Chọn loại địa điểm")
    private LocationType type;

    @NotBlank(message = "Chọn quốc gia")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Mã quốc gia gồm 2 chữ in hoa")
    private String countryCode;

    @Size(max = 10, message = "Mã tỉnh tối đa 10 ký tự")
    private String provinceCode;

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    @DecimalMin(value = "-90", message = "Vĩ độ từ -90 đến 90")
    @DecimalMax(value = "90", message = "Vĩ độ từ -90 đến 90")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "Kinh độ từ -180 đến 180")
    @DecimalMax(value = "180", message = "Kinh độ từ -180 đến 180")
    private BigDecimal longitude;

    @Min(value = 0, message = "Giờ mở cửa tính bằng phút từ 0 đến 1439")
    @Max(value = 1439, message = "Giờ mở cửa tính bằng phút từ 0 đến 1439")
    private Integer openingMinutes;

    @Min(value = 0, message = "Giờ đóng cửa tính bằng phút từ 0 đến 1439")
    @Max(value = 1439, message = "Giờ đóng cửa tính bằng phút từ 0 đến 1439")
    private Integer closingMinutes;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String contactPhone;

    // Địa điểm mới, chưa lưu. Quốc gia, tỉnh do service nạp rồi truyền vào; trạng thái do service đặt.
    public Location toEntity(Country country, Province province) {
        Location location = new Location();
        applyTo(location, country, province);
        return location;
    }

    public void applyTo(Location location, Country country, Province province) {
        location.setCode(code.trim().toUpperCase(Locale.ROOT));
        location.setName(name.trim());
        location.setType(type);
        location.setCountry(country);
        location.setProvince(province);
        location.setAddress(address.trim());
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setOpeningMinutes(openingMinutes);
        location.setClosingMinutes(closingMinutes);
        location.setContactPhone(contactPhone == null || contactPhone.isBlank() ? null : contactPhone.trim());
    }
}
