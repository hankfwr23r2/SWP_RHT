package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.network.CountryResponseDto;
import com.example.racehorse_transport.dto.network.HubServiceAreaResponseDto;
import com.example.racehorse_transport.dto.network.LocationRequestDto;
import com.example.racehorse_transport.dto.network.LocationResponseDto;
import com.example.racehorse_transport.dto.network.ProvinceResponseDto;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.HubServiceArea;
import com.example.racehorse_transport.entity.HubServiceAreaId;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.CountryRepository;
import com.example.racehorse_transport.repository.HubServiceAreaRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.ProvinceRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.service.LocationService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final CountryRepository countries;
    private final ProvinceRepository provinces;
    private final LocationRepository locations;
    private final HubServiceAreaRepository serviceAreas;
    private final RouteRepository routes;

    @Override
    @Transactional(readOnly = true)
    public List<CountryResponseDto> countries() {
        return countries.findAll(Sort.by("code")).stream().map(CountryResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvinceResponseDto> provinces(String countryCode) {
        return provinces.findByCountry_CodeOrderByName(countryCode.trim().toUpperCase(Locale.ROOT)).stream()
                .map(ProvinceResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocationResponseDto> locations(LocationType type, ActiveStatus status) {
        return locations.search(type, status).stream().map(LocationResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LocationResponseDto location(Long id) {
        return LocationResponseDto.from(findLocation(id));
    }

    @Override
    @Transactional
    public LocationResponseDto createLocation(LocationRequestDto request) {
        requireUniqueCode(request.getCode().trim().toUpperCase(Locale.ROOT), null);
        Location location = request.toEntity(country(request.getCountryCode()), province(request.getProvinceCode(), request.getCountryCode()));
        location.setStatus(ActiveStatus.ACTIVE);
        return LocationResponseDto.from(locations.save(location));
    }

    @Override
    @Transactional
    public LocationResponseDto updateLocation(Long id, LocationRequestDto request) {
        Location location = findLocation(id);
        // Kiểm mã trùng trước khi ghi đè, để lần flush tự động của truy vấn không vướng ràng buộc UNIQUE
        requireUniqueCode(request.getCode().trim().toUpperCase(Locale.ROOT), id);
        LocationType previousType = location.getType();
        request.applyTo(location, country(request.getCountryCode()), province(request.getProvinceCode(), request.getCountryCode()));
        // Đổi loại địa điểm làm sai tuyến và vùng phục vụ đang dùng nó
        if (previousType != location.getType()
                && (routes.isUsedByRoute(id, ActiveStatus.ACTIVE) || routes.isUsedByRoute(id, ActiveStatus.INACTIVE)
                    || (previousType == LocationType.HUB && servesAnyProvince(id)))) {
            throw new IllegalStateException("Không đổi được loại của địa điểm đang dùng trong tuyến hoặc vùng phục vụ.");
        }
        return LocationResponseDto.from(location);
    }

    @Override
    @Transactional
    public LocationResponseDto activateLocation(Long id) {
        Location location = findLocation(id);
        location.setStatus(ActiveStatus.ACTIVE);
        return LocationResponseDto.from(location);
    }

    @Override
    @Transactional
    public LocationResponseDto deactivateLocation(Long id) {
        Location location = findLocation(id);
        if (routes.isUsedByRoute(id, ActiveStatus.ACTIVE)) {
            throw new IllegalStateException("Địa điểm đang thuộc một tuyến đang chạy. Ngừng tuyến trước.");
        }
        location.setStatus(ActiveStatus.INACTIVE);
        return LocationResponseDto.from(location);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HubServiceAreaResponseDto> serviceAreas() {
        return serviceAreas.findAllWithHubAndProvince().stream().map(HubServiceAreaResponseDto::from).toList();
    }

    @Override
    @Transactional
    public HubServiceAreaResponseDto assignServiceArea(String provinceCode, Long hubLocationId) {
        Province province = provinces.findById(provinceCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tỉnh " + provinceCode));
        Location hub = findLocation(hubLocationId);
        if (hub.getType() != LocationType.HUB || hub.getStatus() != ActiveStatus.ACTIVE) {
            throw new IllegalArgumentException("Chỉ giao tỉnh cho hub đang hoạt động.");
        }
        var current = serviceAreas.findById_ProvinceCode(provinceCode);
        if (current.isPresent()) {
            if (current.get().getHub().getId().equals(hubLocationId)) {
                return HubServiceAreaResponseDto.from(current.get());
            }
            // Khóa chính gồm cả hub, nên chuyển hub là xóa dòng cũ rồi thêm dòng mới
            serviceAreas.delete(current.get());
            serviceAreas.flush();
        }
        HubServiceArea area = new HubServiceArea();
        area.setId(new HubServiceAreaId(hubLocationId, provinceCode));
        area.setHub(hub);
        area.setProvince(province);
        return HubServiceAreaResponseDto.from(serviceAreas.save(area));
    }

    @Override
    @Transactional
    public void removeServiceArea(String provinceCode) {
        HubServiceArea area = serviceAreas.findById_ProvinceCode(provinceCode)
                .orElseThrow(() -> new ResourceNotFoundException("Tỉnh " + provinceCode + " chưa thuộc hub nào"));
        serviceAreas.delete(area);
    }

    private Location findLocation(Long id) {
        return locations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm " + id));
    }

    private Country country(String code) {
        return countries.findById(code).orElseThrow(() -> new IllegalArgumentException("Quốc gia không hợp lệ: " + code));
    }

    // Tỉnh có thể bỏ trống; nếu có thì phải thuộc đúng quốc gia đã chọn
    private Province province(String provinceCode, String countryCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return null;
        }
        Province province = provinces.findById(provinceCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Tỉnh không hợp lệ: " + provinceCode));
        if (!province.getCountry().getCode().equals(countryCode)) {
            throw new IllegalArgumentException("Tỉnh " + provinceCode + " không thuộc quốc gia " + countryCode + ".");
        }
        return province;
    }

    private void requireUniqueCode(String code, Long selfId) {
        locations.findByCode(code)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new IllegalStateException("Mã địa điểm " + code + " đã được dùng.");
                });
    }

    private boolean servesAnyProvince(Long hubId) {
        return serviceAreas.findAllWithHubAndProvince().stream().anyMatch(area -> area.getHub().getId().equals(hubId));
    }
}
