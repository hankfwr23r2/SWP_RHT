package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseDTO;
import com.example.racehorse_transport.dto.HorseJourneyLogDTO;
import com.example.racehorse_transport.dto.UpdateHorseLocationRequest;
import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseJourneyLog;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseTypeRepository;
import com.example.racehorse_transport.repository.HorseJourneyLogRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.HorseService;
import com.example.racehorse_transport.util.MicrochipPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.time.Instant;

@RestController
@RequestMapping("/horses")
@RequiredArgsConstructor
public class HorseController {

    private final HorseService horseService;
    private final HorseRepository horseRepository;
    private final HorseTypeRepository horseTypeRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final HorseJourneyLogRepository horseJourneyLogRepository;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<HorseDTO>>> getAllHorses(Authentication authentication) {
        List<Horse> horses = isCustomer(authentication)
                ? horseRepository.findByCustomerNewestFirst(currentUser(authentication).getId())
                : horseService.findAll();
        return ResponseEntity.ok(ApiResponse.success(horses.stream().map(this::toDto).toList(), "Lấy danh sách hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR', 'ESCORT', 'DRIVER')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseDTO>> getHorseById(@PathVariable Integer id, Authentication authentication) {
        Horse horse = horseService.findById(id).orElse(null);
        if (horse == null) return ResponseEntity.status(404).body(ApiResponse.error("Không tìm thấy hồ sơ ngựa"));
        ensureCanView(horse, authentication);
        return ResponseEntity.ok(ApiResponse.success(toDto(horse), "Lấy hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<HorseDTO>> createHorse(@RequestBody HorseDTO request, Authentication authentication) {
        String chip = MicrochipPolicy.normalize(request.getMicrochipID());
        if (chip.isEmpty()) {
            throw new IllegalArgumentException("Mã microchip không được để trống.");
        }
        if (!MicrochipPolicy.isIso(chip)) {
            throw new IllegalArgumentException("Mã microchip phải gồm đúng 15 chữ số (chuẩn ISO 11784/11785).");
        }
        if (horseRepository.existsByMicrochipIDIgnoreCase(chip)) {
            throw new IllegalArgumentException("Mã microchip đã được sử dụng.");
        }
        request.setMicrochipID(chip);

        Horse horse = new Horse();
        apply(request, horse);
        if (isCustomer(authentication)) {
            horse.setCustomerID(customerFor(currentUser(authentication)));
        } else if (request.getCustomerID() != null) {
            horse.setCustomerID(customerRepository.findById(request.getCustomerID())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng.")));
        }
        Horse saved;
        try {
            saved = horseService.save(horse);
        } catch (DataIntegrityViolationException e) {
            // Hai lần lưu cùng lúc vượt qua bước kiểm tra trùng ở trên: DB chặn bằng unique index
            throw new IllegalArgumentException("Mã microchip đã được sử dụng.");
        }
        return ResponseEntity.status(201).body(ApiResponse.success(toDto(saved), "Tạo hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseDTO>> updateHorse(@PathVariable Integer id, @RequestBody HorseDTO request, Authentication authentication) {
        Horse horse = horseService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ ngựa."));
        ensureCanManage(horse, authentication);
        if (request.getMicrochipID() != null && !request.getMicrochipID().equalsIgnoreCase(horse.getMicrochipID())) {
            throw new IllegalArgumentException("Không thể thay đổi mã microchip sau khi lưu.");
        }
        apply(request, horse);
        horse.setCustomerID(horse.getCustomerID());
        horse.setUpdatedAt(Instant.now().toEpochMilli());
        return ResponseEntity.ok(ApiResponse.success(toDto(horseService.save(horse)), "Cập nhật hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'MANAGER')")
    @PatchMapping("/{id}/location")
    public ResponseEntity<ApiResponse<HorseDTO>> updateLocation(
            @PathVariable Integer id,
            @RequestBody UpdateHorseLocationRequest request,
            Authentication authentication) {
        Horse horse = horseService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ ngựa."));
        ensureCanManage(horse, authentication);

        if (request.getLocationName() == null || request.getLocationName().isBlank()) {
            throw new IllegalArgumentException("Tên địa điểm chuồng mới không được để trống.");
        }

        String oldLocation = horse.getCurrentLocationName();
        String newLocation = request.getLocationName().trim();
        String newCountry = request.getCountry() != null && !request.getCountry().isBlank()
                ? request.getCountry().trim().toUpperCase() : horse.getCurrentCountry();
        if (newLocation.equalsIgnoreCase(oldLocation != null ? oldLocation.trim() : null)
                && (newCountry == null || newCountry.equalsIgnoreCase(horse.getCurrentCountry()))) {
            throw new IllegalArgumentException("Địa chỉ chuồng mới trùng với vị trí hiện tại.");
        }
        if ("IN_TRANSIT".equalsIgnoreCase(horse.getTransitStatus())) {
            throw new IllegalStateException("Ngựa đang trong hành trình vận chuyển, không thể tự cập nhật vị trí chuồng.");
        }
        String activeBooking = activeBookingOf(horse);
        if (activeBooking != null) {
            throw new IllegalStateException("Ngựa đang nằm trong đơn " + activeBooking
                    + " chưa hoàn tất. Vị trí chuồng sẽ tự cập nhật khi chuyến đi kết thúc.");
        }

        horse.setCurrentLocationName(newLocation);
        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            horse.setCurrentCountry(request.getCountry().trim().toUpperCase());
        } else if (horse.getCurrentCountry() == null) {
            horse.setCurrentCountry("VN");
        }
        horse.setLocationUpdatedAt(System.currentTimeMillis());
        horse.setLocationSource("OWNER_DECLARED");

        Horse saved = horseService.save(horse);

        // Lưu bản ghi nhật ký hành trình / di dời chuồng
        HorseJourneyLog log = HorseJourneyLog.builder()
                .horse(saved)
                .fromLocation(oldLocation != null ? oldLocation : "Vị trí ban đầu")
                .toLocation(newLocation)
                .departAt(System.currentTimeMillis())
                .arrivedAt(System.currentTimeMillis())
                .driverName(authentication.getName() + " (Chủ ngựa)")
                .notes(request.getNotes() != null && !request.getNotes().isBlank()
                        ? request.getNotes().trim() : "Chủ ngựa cập nhật vị trí chuồng")
                .source("OWNER_RELOCATION")
                .createdAt(System.currentTimeMillis())
                .build();
        horseJourneyLogRepository.save(log);

        return ResponseEntity.ok(ApiResponse.success(toDto(saved), "Cập nhật vị trí ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR', 'ESCORT', 'DRIVER')")
    @GetMapping("/{id}/journey-logs")
    public ResponseEntity<ApiResponse<List<HorseJourneyLogDTO>>> getJourneyLogs(
            @PathVariable Integer id,
            Authentication authentication) {
        Horse horse = horseService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ ngựa."));
        ensureCanView(horse, authentication);

        List<HorseJourneyLog> logs = horseJourneyLogRepository.findByHorse_IdOrderByCreatedAtDesc(id);
        List<HorseJourneyLogDTO> dtos = logs.stream().map(l -> HorseJourneyLogDTO.builder()
                .id(l.getId())
                .horseId(l.getHorse().getId())
                .bookingId(l.getBookingId())
                .tripCode(l.getTripCode())
                .fromLocation(l.getFromLocation())
                .toLocation(l.getToLocation())
                .departAt(l.getDepartAt())
                .arrivedAt(l.getArrivedAt())
                .driverName(l.getDriverName())
                .proofImageUrl(l.getProofImageUrl())
                .notes(l.getNotes())
                .source(l.getSource())
                .createdAt(l.getCreatedAt())
                .build()).toList();

        return ResponseEntity.ok(ApiResponse.success(dtos, "Lấy lịch sử hành trình ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHorse(@PathVariable Integer id, Authentication authentication) {
        Horse horse = horseService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ ngựa."));
        ensureCanManage(horse, authentication);
        horseService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa hồ sơ ngựa thành công"));
    }

    private void apply(HorseDTO request, Horse horse) {
        if (request.getHorseName() != null) horse.setHorseName(request.getHorseName().trim());
        if (request.getMicrochipID() != null) horse.setMicrochipID(request.getMicrochipID().trim().toUpperCase());
        if (request.getGender() != null) horse.setGender(request.getGender());
        if (request.getBreed() != null) horse.setBreed(request.getBreed());
        if (request.getColor() != null) horse.setColor(request.getColor());
        if (request.getAge() != null) horse.setAge(request.getAge());
        if (request.getWeight() != null) horse.setWeight(request.getWeight());
        if (request.getIdentificationMarks() != null) horse.setIdentificationMarks(request.getIdentificationMarks());
        if (request.getStatus() != null) horse.setStatus(request.getStatus());
        if (request.getHorseTypeID() != null) {
            horse.setHorseTypeID(horseTypeRepository.findById(request.getHorseTypeID())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giống ngựa.")));
        } else if ("Khác".equalsIgnoreCase(request.getBreed())) {
            horse.setHorseTypeID(null);
        }
        // Sau khi tạo, vị trí chỉ đổi qua PATCH /location (có kiểm tra đơn đang chạy) hoặc khi giao ngựa xong
        if (horse.getId() == null && request.getCurrentLocationName() != null) horse.setCurrentLocationName(request.getCurrentLocationName().trim());
        if (horse.getId() == null && request.getCurrentCountry() != null) horse.setCurrentCountry(request.getCurrentCountry().trim());
        if (horse.getCompletedTrips() == null) horse.setCompletedTrips(request.getCompletedTrips() == null ? 0 : request.getCompletedTrips());
        if (horse.getCreatedAt() == null) horse.setCreatedAt(request.getCreatedAt() == null ? Instant.now().toEpochMilli() : request.getCreatedAt().toEpochMilli());
    }

    // Đơn chưa kết thúc có ngựa này (cùng tập trạng thái kết thúc với lúc tạo đơn); null nếu không có
    private String activeBookingOf(Horse horse) {
        if (horse.getCustomerID() == null) return null;
        Set<String> finished = Set.of("completed", "cancelled", "rejected", "quote_expired");
        for (Booking booking : bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(horse.getCustomerID().getId())) {
            String status = booking.getStatus() != null ? booking.getStatus().toLowerCase() : "";
            if (finished.contains(status) || booking.getDetailsJson() == null || booking.getDetailsJson().isBlank()) continue;
            try {
                BookingDetailsPayload payload = objectMapper.readValue(booking.getDetailsJson(), BookingDetailsPayload.class);
                if (payload.getHorses() == null) continue;
                for (BookingHorseDto bookingHorse : payload.getHorses()) {
                    String id = bookingHorse.getHorseId() != null ? bookingHorse.getHorseId().replaceAll("\\D", "") : "";
                    if (id.equals(String.valueOf(horse.getId()))) return "#BK-" + booking.getId();
                }
            } catch (Exception e) {
                throw new IllegalStateException("Không đọc được đơn #BK-" + booking.getId() + " để kiểm tra ngựa.");
            }
        }
        return null;
    }

    private HorseDTO toDto(Horse horse) {
        HorseDTO dto = new HorseDTO();
        dto.setId(horse.getId());
        dto.setHorseTypeID(horse.getHorseTypeID() != null ? horse.getHorseTypeID().getId() : null);
        dto.setCustomerID(horse.getCustomerID() != null ? horse.getCustomerID().getId() : null);
        dto.setHorseName(horse.getHorseName());
        dto.setMicrochipID(horse.getMicrochipID());
        dto.setGender(horse.getGender());
        dto.setBreed(horse.getBreed());
        dto.setColor(horse.getColor());
        dto.setAge(horse.getAge());
        dto.setWeight(horse.getWeight());
        dto.setIdentificationMarks(horse.getIdentificationMarks());
        dto.setCompletedTrips(horse.getCompletedTrips());
        dto.setCreatedAt(horse.getCreatedAt() == null ? null : Instant.ofEpochMilli(horse.getCreatedAt()));
        dto.setStatus(horse.getStatus());
        dto.setCurrentLocationName(horse.getCurrentLocationName());
        dto.setCurrentCountry(horse.getCurrentCountry());
        dto.setLocationUpdatedAt(horse.getLocationUpdatedAt());
        dto.setLocationSource(horse.getLocationSource());
        dto.setTransitStatus(horse.getTransitStatus());
        return dto;
    }


    private User currentUser(Authentication authentication) {
        return userRepository.findByUsernameOrEmail(authentication.getName(), authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Không xác định được tài khoản."));
    }

    private Customer customerFor(User user) {
        return customerRepository.findById(user.getId()).orElseGet(() -> {
            Customer customer = new Customer();
            customer.setUser(user);
            customer.setFullName(user.getUsername());
            return customerRepository.save(customer);
        });
    }

    private boolean isCustomer(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private void ensureCanView(Horse horse, Authentication authentication) {
        if (isCustomer(authentication)) ensureOwner(horse, currentUser(authentication));
    }

    private void ensureCanManage(Horse horse, Authentication authentication) {
        if (isCustomer(authentication)) ensureOwner(horse, currentUser(authentication));
    }

    private void ensureOwner(Horse horse, User user) {
        if (horse.getCustomerID() == null || !user.getId().equals(horse.getCustomerID().getId())) {
            throw new AccessDeniedException("Bạn không có quyền truy cập hồ sơ ngựa này.");
        }
    }
}
