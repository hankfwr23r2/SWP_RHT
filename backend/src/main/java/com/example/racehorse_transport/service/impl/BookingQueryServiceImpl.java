package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingDetailResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDetailResponseDto.HorseDetail;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.BookingHorseAddOn;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseAddOnRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingPartyRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.service.BookingQueryService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingQueryServiceImpl implements BookingQueryService {

    private final BookingRepository bookings;
    private final BookingPartyRepository parties;
    private final BookingAddressRepository addresses;
    private final BookingHorseAddOnRepository addOns;
    private final BookingHorseRepository horses;
    private final HorseInsuranceRepository insurances;

    @Override
    public List<BookingResponseDto> list(String username) {
        return bookings.findByCustomer_User_UsernameOrderByCreatedAtDescIdDesc(username).stream()
                .map(BookingResponseDto::from)
                .toList();
    }

    @Override
    public BookingDetailResponseDto get(String username, Long id) {
        Booking booking = bookings.findByIdAndCustomer_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        List<BookingHorse> rows = horses.findForDetail(id);
        return BookingDetailResponseDto.from(booking, parties.findById_BookingId(id),
                addresses.findAddressesForDetail(id), horseDetails(rows));
    }

    private List<HorseDetail> horseDetails(List<BookingHorse> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(BookingHorse::getId).toList();
        Map<Long, List<BookingHorseAddOn>> addOnsByHorse = addOns.findAddOnsForDetail(ids).stream()
                .collect(Collectors.groupingBy(addOn -> addOn.getId().getBookingHorseId()));
        Map<Long, HorseInsurance> insuranceByHorse = insurances.findForDetail(ids).stream()
                .collect(Collectors.toMap(insurance -> insurance.getBookingHorse().getId(), Function.identity()));
        return rows.stream()
                .map(row -> HorseDetail.from(row, addOnsByHorse.getOrDefault(row.getId(), List.of()),
                        insuranceByHorse.get(row.getId())))
                .toList();
    }
}
