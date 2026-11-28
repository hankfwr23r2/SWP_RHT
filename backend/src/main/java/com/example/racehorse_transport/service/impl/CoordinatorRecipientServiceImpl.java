package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import org.springframework.security.access.AccessDeniedException;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.CoordinatorRecipientService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoordinatorRecipientServiceImpl implements CoordinatorRecipientService {
    private final StaffRepository staff;
    private final HandoverHorseRepository scans;
    private final com.example.racehorse_transport.repository.HorseStayRepository stays;

    @Override
    public List<Long> forTrip(VehicleTrip trip) {
        return recipients(trip == null ? null : trip.getCoordinator());
    }

    @Override
    public List<Long> forHorse(BookingHorse horse, VehicleTrip trip) {
        return trip != null ? forTrip(trip) : recipients(atHub(horse));
    }

    @Override
    public Staff atHub(BookingHorse horse) {
        var arrival = scans.findFirstByHorseLeg_BookingHorse_IdAndHandover_HandoverTypeAndHandover_StatusAndMicrochipMatchedTrueOrderByHandover_HandedAtDescHandover_IdDesc(
                horse.getId(), HandoverType.HUB_IN, HandoverStatus.DONE).orElse(null);
        if (arrival != null) {
            var stop = arrival.getHandover().getTripStop();
            if (stop != null) {
                return stop.getVehicleTrip().getCoordinator();
            }
            if (arrival.getHorseLeg().getVehicleTrip() != null) {
                return arrival.getHorseLeg().getVehicleTrip().getCoordinator();
            }
        }
        var booking = horse.getBooking();
        boolean selfDelivery = booking.getPickupMethod() == com.example.racehorse_transport.entity.enums.TransferMethod.SELF
                || horse.getPickupMissChoice() == com.example.racehorse_transport.entity.enums.PickupMissChoice.SELF_DELIVERY;
        return selfDelivery && booking.getDeparture() != null ? booking.getDeparture().getCoordinator() : null;
    }

    @Override
    public Staff staffByUsername(String username) {
        return staff.findByUser_Username(username)
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên."));
    }

    @Override
    public List<Long> managers() {
        return staff.findUserIdsByRoleAndStatus("MANAGER", AppUserStatus.ACTIVE);
    }

    @Override
    public List<Long> forHubBooking(Long bookingId) {
        var horses = stays.findByBookingHorse_Booking_Id(bookingId).stream()
                .filter(stay -> stay.getActualStartAt() != null && stay.getActualEndAt() == null)
                .map(stay -> stay.getBookingHorse()).distinct().toList();
        if (horses.isEmpty()) {
            return recipients(null);
        }
        return horses.stream().flatMap(horse -> forHorse(horse, null).stream()).distinct().toList();
    }

    private List<Long> recipients(Staff owner) {
        if (owner != null) {
            return List.of(owner.getUserId());
        }
        return staff.findUserIdsByRoleAndStatus("COORDINATOR", AppUserStatus.ACTIVE);
    }
}
