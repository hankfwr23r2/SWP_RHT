package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.horse.HorseCreateRequestDto;
import com.example.racehorse_transport.dto.horse.HorseResponseDto;
import com.example.racehorse_transport.dto.horse.HorseTypeResponseDto;
import com.example.racehorse_transport.dto.horse.HorseUpdateRequestDto;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.HorseStatus;
import com.example.racehorse_transport.exception.HorseRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.HorsePolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository.HorseBooking;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.HorseTypeRepository;
import com.example.racehorse_transport.service.HorseService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorseServiceImpl implements HorseService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> UNIQUE_FIELDS = Set.of("uk_horse_microchip", "uk_horse_passport_number");

    private final HorseRepository horses;
    private final HorseTypeRepository horseTypes;
    private final CustomerRepository customers;
    private final BookingHorseRepository bookingHorses;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<HorseTypeResponseDto> horseTypes() {
        return horseTypes.findAll(Sort.by("name")).stream().map(HorseTypeResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HorseResponseDto> horses(String username) {
        List<Horse> owned = horses.findByOwner_User_UsernameOrderByNameAscIdAsc(username);
        Map<Long, String> active = activeBookings(owned.stream().map(Horse::getId).toList());
        return owned.stream().map(horse -> HorseResponseDto.from(horse, active.get(horse.getId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HorseResponseDto horse(String username, Long id) {
        return HorseResponseDto.from(ownedHorse(username, id), activeBookings(List.of(id)).get(id));
    }

    @Override
    @Transactional
    public HorseResponseDto createHorse(String username, HorseCreateRequestDto request) {
        String microchip = HorsePolicy.requireMicrochip(request.microchip());
        requireValid(request.birthYear(), request.declaredValue(), request.declaredCurrencyCode());
        Horse horse = request.toEntity(horseType(request.horseTypeId()));
        horse.setOwner(customers.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ khách.")));
        horse.setMicrochip(microchip);
        horse.setStatus(HorseStatus.ACTIVE);
        setDeclared(horse, request.declaredValue(), request.declaredCurrencyCode());
        return HorseResponseDto.from(saveUnique(horse), null);
    }

    @Override
    @Transactional
    public HorseResponseDto updateHorse(String username, Long id, HorseUpdateRequestDto request) {
        requireValid(request.birthYear(), request.declaredValue(), request.declaredCurrencyCode());
        HorseType horseType = horseType(request.horseTypeId());
        Horse horse = ownedHorse(username, id);
        request.applyTo(horse, horseType);
        setDeclared(horse, request.declaredValue(), request.declaredCurrencyCode());
        horse.setUpdatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        return HorseResponseDto.from(saveUnique(horse), activeBookings(List.of(id)).get(id));
    }

    private Map<Long, String> activeBookings(List<Long> horseIds) {
        if (horseIds.isEmpty()) {
            return Map.of();
        }
        return bookingHorses.findBusyHorses(horseIds, BookingHorseStatus.ACTIVE, BookingSubmissionPolicy.ENDED_STATUSES).stream()
                .collect(Collectors.toMap(HorseBooking::getHorseId, HorseBooking::getBookingCode, (first, second) -> first));
    }

    private void requireValid(Short birthYear, BigDecimal declaredValue, String declaredCurrencyCode) {
        HorsePolicy.requireBirthYearNotAfter(birthYear, clock.instant().atZone(BUSINESS_ZONE).getYear());
        HorsePolicy.requireDeclaredPair(declaredValue, declaredCurrencyCode);
    }

    private static void setDeclared(Horse horse, BigDecimal declaredValue, String declaredCurrencyCode) {
        horse.setDeclaredValue(declaredValue);
        horse.setDeclaredCurrencyCode(declaredCurrencyCode);
    }

    private Horse saveUnique(Horse horse) {
        try {
            return horses.saveAndFlush(horse);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null && UNIQUE_FIELDS.contains(violation.getConstraintName())) {
                throw new HorseRuleException.DuplicateHorseException();
            }
            throw ex;
        }
    }

    // Ngựa của khách khác coi như không tồn tại
    private Horse ownedHorse(String username, Long id) {
        return horses.findByIdAndOwner_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ ngựa."));
    }

    private HorseType horseType(Long id) {
        return horseTypes.findById(id).orElseThrow(() -> new HorseRuleException("Loại ngựa không hợp lệ."));
    }
}
