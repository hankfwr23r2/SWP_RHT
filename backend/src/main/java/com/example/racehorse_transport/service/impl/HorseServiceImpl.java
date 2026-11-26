package com.example.racehorse_transport.service.impl;

import static com.example.racehorse_transport.policy.BookingSubmissionPolicy.businessDate;

import com.example.racehorse_transport.dto.horse.HorseCreateRequestDto;
import com.example.racehorse_transport.dto.horse.HorseDocumentResponseDto;
import com.example.racehorse_transport.dto.horse.HorseResponseDto;
import com.example.racehorse_transport.dto.horse.HorseResponseDto.HorseDocumentStatusResponseDto;
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
import com.example.racehorse_transport.service.HorseDocumentService;
import com.example.racehorse_transport.service.HorseService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

    private final HorseRepository horses;
    private final HorseTypeRepository horseTypes;
    private final CustomerRepository customers;
    private final BookingHorseRepository bookingHorses;
    private final HorseDocumentService documents;
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
        List<Long> ids = owned.stream().map(Horse::getId).toList();
        Map<Long, String> active = activeBookings(ids);
        var profiles = documents.documentsFor(ids);
        return owned.stream().map(horse -> profile(horse, active.get(horse.getId()), profiles.get(horse.getId()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HorseResponseDto horse(String username, Long id) {
        return profile(ownedHorse(username, id), activeBookings(List.of(id)).get(id));
    }

    @Override
    @Transactional
    public HorseResponseDto createHorse(String username, HorseCreateRequestDto request) {
        String microchip = HorsePolicy.requireMicrochip(request.microchip());
        HorsePolicy.requireBirthYearNotAfter(request.birthYear(), businessDate(clock.instant()).getYear());
        Horse horse = request.toEntity(horseType(request.horseTypeId()));
        horse.setOwner(customers.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ khách.")));
        horse.setMicrochip(microchip);
        horse.setStatus(HorseStatus.ACTIVE);
        return profile(saveUnique(horse), null);
    }

    @Override
    @Transactional
    public HorseResponseDto updateHorse(String username, Long id, HorseUpdateRequestDto request) {
        HorsePolicy.requireBirthYearNotAfter(request.birthYear(), businessDate(clock.instant()).getYear());
        HorseType horseType = horseType(request.horseTypeId());
        Horse horse = ownedHorse(username, id);
        request.applyTo(horse, horseType);
        horse.setUpdatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        return profile(saveUnique(horse), activeBookings(List.of(id)).get(id));
    }

    private HorseResponseDto profile(Horse horse, String activeBookingCode) {
        return profile(horse, activeBookingCode, documents.documentsFor(List.of(horse.getId())).get(horse.getId()));
    }

    private static HorseResponseDto profile(Horse horse, String activeBookingCode, List<HorseDocumentResponseDto> documents) {
        var missing = documents.stream().filter(HorseDocumentResponseDto::canUpload)
                .map(HorseDocumentResponseDto::documentTypeCode).toList();
        return HorseResponseDto.from(horse, activeBookingCode, missing, documents.stream().map(HorseDocumentStatusResponseDto::from).toList());
    }

    private Map<Long, String> activeBookings(List<Long> horseIds) {
        if (horseIds.isEmpty()) {
            return Map.of();
        }
        return bookingHorses.findBusyHorses(horseIds, BookingHorseStatus.ACTIVE, BookingSubmissionPolicy.ENDED_STATUSES).stream()
                .collect(Collectors.toMap(HorseBooking::getHorseId, HorseBooking::getBookingCode, (first, second) -> first));
    }

    private Horse saveUnique(Horse horse) {
        try {
            return horses.saveAndFlush(horse);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && "uk_horse_microchip".equals(violation.getConstraintName())) {
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
