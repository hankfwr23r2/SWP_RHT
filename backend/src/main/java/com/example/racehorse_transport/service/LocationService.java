package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.network.CountryResponseDto;
import com.example.racehorse_transport.dto.network.HubServiceAreaResponseDto;
import com.example.racehorse_transport.dto.network.LocationRequestDto;
import com.example.racehorse_transport.dto.network.LocationResponseDto;
import com.example.racehorse_transport.dto.network.ProvinceResponseDto;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import java.util.List;

// Danh mục địa lý và điểm mạng lưới: quốc gia, tỉnh, location, vùng phục vụ của hub. Chủ sở hữu: luồng A (A1).
public interface LocationService {

    List<CountryResponseDto> countries();

    List<ProvinceResponseDto> provinces(String countryCode);

    // type, status null nghĩa là không lọc
    List<LocationResponseDto> locations(LocationType type, ActiveStatus status);

    LocationResponseDto location(Long id);

    LocationResponseDto createLocation(LocationRequestDto request);

    LocationResponseDto updateLocation(Long id, LocationRequestDto request);

    LocationResponseDto activateLocation(Long id);

    // Không cho ngừng dùng địa điểm đang thuộc một tuyến đang chạy
    LocationResponseDto deactivateLocation(Long id);

    List<HubServiceAreaResponseDto> serviceAreas();

    // Giao tỉnh cho hub; tỉnh đã thuộc hub khác thì chuyển sang hub này
    HubServiceAreaResponseDto assignServiceArea(String provinceCode, Long hubLocationId);

    void removeServiceArea(String provinceCode);
}
