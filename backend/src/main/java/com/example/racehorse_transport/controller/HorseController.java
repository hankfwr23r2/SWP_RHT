package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseDTO;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.HorseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/horses")
@RequiredArgsConstructor
public class HorseController {

    private final HorseService horseService;
    private final HorseRepository horseRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN', 'COORDINATOR')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<HorseDTO>>> getAllHorses(Authentication authentication) {
        List<Horse> horses = isCustomer(authentication)
                ? horseRepository.findByCustomerID_Id(currentUser(authentication).getId())
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
        if (request.getMicrochipID() == null || request.getMicrochipID().isBlank()) {
            throw new IllegalArgumentException("Mã microchip không được để trống.");
        }
        String chip = request.getMicrochipID().trim().toUpperCase();
        if (horseRepository.existsByMicrochipIDIgnoreCase(chip)) {
            throw new IllegalArgumentException("Mã microchip đã được sử dụng.");
        }

        Horse horse = new Horse();
        apply(request, horse);
        if (isCustomer(authentication)) {
            horse.setCustomerID(customerFor(currentUser(authentication)));
        } else if (request.getCustomerID() != null) {
            horse.setCustomerID(customerRepository.findById(request.getCustomerID())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng.")));
        }
        Horse saved = horseService.save(horse);
        return ResponseEntity.status(201).body(ApiResponse.success(toDto(saved), "Tạo hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HorseDTO>> updateHorse(@PathVariable Integer id, @RequestBody HorseDTO request, Authentication authentication) {
        Horse horse = horseService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ ngựa."));
        ensureCanManage(horse, authentication);
        if (request.getMicrochipID() != null && !request.getMicrochipID().equalsIgnoreCase(horse.getMicrochipID())) {
            throw new IllegalArgumentException("Không thể thay đổi mã microchip sau khi lưu.");
        }
        apply(request, horse);
        horse.setCustomerID(horse.getCustomerID());
        return ResponseEntity.ok(ApiResponse.success(toDto(horseService.save(horse)), "Cập nhật hồ sơ ngựa thành công"));
    }

    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteHorse(@PathVariable Integer id, Authentication authentication) {
        Horse horse = horseService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ ngựa."));
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
        if (horse.getCompletedTrips() == null) horse.setCompletedTrips(request.getCompletedTrips() == null ? 0 : request.getCompletedTrips());
        if (horse.getCreatedAt() == null) horse.setCreatedAt(request.getCreatedAt() == null ? Instant.now().toEpochMilli() : request.getCreatedAt().toEpochMilli());
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
