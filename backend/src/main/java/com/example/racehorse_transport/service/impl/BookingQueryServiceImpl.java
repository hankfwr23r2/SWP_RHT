package com.example.racehorse_transport.service.impl;

import org.springframework.data.domain.PageRequest;
import java.util.Set;
import com.example.racehorse_transport.dto.booking.BookingSummaryResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDetailResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDetailResponseDto.HorseDetail;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.BookingHorseAddOn;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseAddOnRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingPartyRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingRatingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.repository.StaffRepository;
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
    private final StaffRepository staff;
    private final BookingRatingRepository ratings;
    private final com.example.racehorse_transport.service.DeliveryChangeService deliveryChanges;

    @Override
    public List<BookingSummaryResponseDto> list(String username) {
        return summarize(bookings.findByCustomer_User_UsernameOrderByCreatedAtDescIdDesc(username));
    }

    @Override
    public BookingDetailResponseDto get(String username, Long id) {
        Booking booking = bookings.findByIdAndCustomer_User_Username(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        return detail(booking, null, false).withDeliveryChange(deliveryChanges.disabledReason(booking));
    }

    @Override
    public List<BookingResponseDto> reviewQueue(String username) {
        var statuses = List.of(BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW, BookingStatus.NEEDS_RESUBMISSION);
        var rows = username == null ? bookings.findByStatusInOrderByCreatedAtAscIdAsc(statuses)
                : bookings.findBySpecialist_User_UsernameAndStatusInOrderByCreatedAtAscIdAsc(username, statuses);
        return rows.stream().map(BookingResponseDto::from).toList();
    }

    @Override
    public List<BookingSummaryResponseDto> summaries(Set<BookingStatus> statuses, int page, String username) {
        if (statuses.isEmpty() || page < 0) {
            throw new IllegalArgumentException("Chọn ít nhất một trạng thái và trang từ 0.");
        }
        var pagination = PageRequest.of(page, PAGE_SIZE);
        return summarize(username == null ? bookings.findByStatusInOrderByCreatedAtDescIdDesc(statuses, pagination)
                : bookings.findBySpecialist_User_UsernameAndStatusInOrderByCreatedAtDescIdDesc(username, statuses, pagination));
    }

    private List<BookingSummaryResponseDto> summarize(List<Booking> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Booking::getId).toList();
        Map<Long, BookingAddress> deliveries = addresses.findDeliveriesOfBookings(ids).stream()
                .collect(Collectors.toMap(address -> address.getBooking().getId(), Function.identity(), (first, later) -> first));
        Map<Long, Integer> horseCounts = bookings.countActiveHorses(ids).stream()
                .collect(Collectors.toMap(BookingRepository.HorseCount::getBookingId,
                        row -> Math.toIntExact(row.getHorseCount())));
        return rows.stream()
                .map(row -> BookingSummaryResponseDto.from(row, horseCounts.getOrDefault(row.getId(), 0), deliveries.get(row.getId())))
                .toList();
    }

    @Override
    public BookingDetailResponseDto reviewDetail(Long id, String username) {
        Booking booking = bookings.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        return detail(booking, intakeByName(id), booking.getSpecialist() != null
                && booking.getSpecialist().getUser().getUsername().equals(username));
    }

    // Người đã tiếp nhận đơn. NULL khi đơn chưa được tiếp nhận
    private String intakeByName(Long bookingId) {
        AppUser actor = history.findFirstByBooking_IdAndFromStatusAndToStatusOrderByChangedAtDescIdDesc(
                bookingId, BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW)
                .map(BookingStatusHistory::getChangedBy).orElse(null);
        return actor == null ? null : staff.findById(actor.getId()).map(Staff::getFullName).orElse(actor.getUsername());
    }

    private BookingDetailResponseDto detail(Booking booking, String intakeByName, boolean assignedToMe) {
        Long id = booking.getId();
        List<BookingHorse> rows = horses.findForDetail(id);
        String reason = history.findFirstByBooking_IdOrderByChangedAtDescIdDesc(id)
                .filter(row -> row.getToStatus() == BookingStatus.NEEDS_RESUBMISSION
                        || row.getToStatus() == BookingStatus.REJECTED)
                .map(BookingStatusHistory::getReason).orElse(null);
        return BookingDetailResponseDto.from(booking, parties.findById_BookingId(id),
                addresses.findAddressesForDetail(id), horseDetails(rows), reason, intakeByName, ratings.existsById(id),
                bookings.findActiveBalanceHoldAt(id).orElse(null), assignedToMe);
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
