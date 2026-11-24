package com.example.racehorse_transport.service.impl;

import org.springframework.data.domain.PageRequest;
import java.util.Set;
import com.example.racehorse_transport.dto.booking.BookingSummaryResponseDto;
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
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.enums.BookingStatus;
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

    // Đủ một màn hình danh sách; màn hình tải thêm trang khi cần
    private static final int PAGE_SIZE = 50;

    private final BookingRepository bookings;
    private final BookingPartyRepository parties;
    private final BookingAddressRepository addresses;
    private final BookingHorseAddOnRepository addOns;
    private final BookingHorseRepository horses;
    private final HorseInsuranceRepository insurances;
    private final BookingStatusHistoryRepository history;

    @Override
    public List<BookingSummaryResponseDto> list(String username) {
        return summarize(bookings.findByCustomer_User_UsernameOrderByCreatedAtDescIdDesc(username));
    }

    @Override
    public BookingDetailResponseDto get(String username, Long id) {
        Booking booking = bookings.findByIdAndCustomer_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        return detail(booking);
    }

    @Override
    public List<BookingResponseDto> reviewQueue() {
        return bookings.findByStatusInOrderByCreatedAtAscIdAsc(List.of(
                BookingStatus.PENDING_INTAKE,
                BookingStatus.UNDER_REVIEW,
                BookingStatus.NEEDS_RESUBMISSION))
                .stream().map(BookingResponseDto::from).toList();
    }

    @Override
    public List<BookingSummaryResponseDto> summaries(Set<BookingStatus> statuses, int page) {
        if (statuses.isEmpty() || page < 0) {
            throw new IllegalArgumentException("Chọn ít nhất một trạng thái và trang từ 0.");
        }
        return summarize(bookings.findByStatusInOrderByCreatedAtDescIdDesc(statuses, PageRequest.of(page, PAGE_SIZE)));
    }

    private List<BookingSummaryResponseDto> summarize(List<Booking> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> horseCounts = bookings.countActiveHorses(rows.stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.toMap(BookingRepository.HorseCount::getBookingId,
                        row -> Math.toIntExact(row.getHorseCount())));
        return rows.stream()
                .map(row -> BookingSummaryResponseDto.from(row, horseCounts.getOrDefault(row.getId(), 0))).toList();
    }

    @Override
    public BookingDetailResponseDto reviewDetail(Long id) {
        return detail(bookings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn.")));
    }

    private BookingDetailResponseDto detail(Booking booking) {
        Long id = booking.getId();
        List<BookingHorse> rows = horses.findForDetail(id);
        String reason = history.findFirstByBooking_IdOrderByChangedAtDescIdDesc(id)
                .filter(row -> row.getToStatus() == BookingStatus.NEEDS_RESUBMISSION
                        || row.getToStatus() == BookingStatus.REJECTED)
                .map(BookingStatusHistory::getReason).orElse(null);
        return BookingDetailResponseDto.from(booking, parties.findById_BookingId(id),
                addresses.findAddressesForDetail(id), horseDetails(rows), reason);
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
