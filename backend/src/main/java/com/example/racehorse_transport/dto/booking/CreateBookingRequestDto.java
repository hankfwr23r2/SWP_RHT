package com.example.racehorse_transport.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateBookingRequest")
public class CreateBookingRequestDto {

    @NotBlank(message = "Loại chuyến không được để trống (domestic/international)")
    private String type;

    @NotBlank(message = "Điểm đón không được để trống")
    private String originName;
    private String originCountry;

    @NotBlank(message = "Điểm giao không được để trống")
    private String destName;
    private String destCountry;

    @NotNull(message = "Ngày khởi hành không được để trống")
    private Long departAt;

    @Valid
    @NotNull(message = "Thông tin người gửi không được để trống")
    private PartyContactDto consignor;

    @Valid
    @NotNull(message = "Thông tin người nhận không được để trống")
    private PartyContactDto consignee;

    @NotEmpty(message = "Danh sách ngựa vận chuyển không được để trống")
    private List<BookingHorseDto> horses;

    // Bắt buộc true khi có ngựa từ chối bảo hiểm; giờ và phiên bản do server ghi
    private Boolean limitedLiabilityAccepted;
}
