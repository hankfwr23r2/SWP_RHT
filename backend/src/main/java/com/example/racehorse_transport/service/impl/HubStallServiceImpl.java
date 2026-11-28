package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.HubStallChangeRequestDto;
import com.example.racehorse_transport.dto.health.HubStallCreateRequestDto;
import com.example.racehorse_transport.dto.health.HubStallInventoryResponseDto;
import com.example.racehorse_transport.dto.health.HubStallResponseDto;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.HubStall;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.HubStallPolicy;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.HubStallRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.service.CoordinatorRecipientService;
import com.example.racehorse_transport.service.HubStallService;
import com.example.racehorse_transport.service.NotificationService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HubStallServiceImpl implements HubStallService {
    private final LocationRepository locations;
    private final HubStallRepository stalls;
    private final HorseStayRepository stays;
    private final CoordinatorRecipientService coordinators;
    private final NotificationService notifications;

    @Override
    @Transactional(readOnly = true)
    public HubStallInventoryResponseDto inventory(Long hubId) {
        requireHub(locations.findById(hubId).orElseThrow(() -> missingHub(hubId)));
        var occupied = occupiedAt(hubId);
        var rows = stalls.findByLocation_IdOrderByCode(hubId).stream()
                .map(stall -> HubStallResponseDto.from(stall, occupied.contains(stall.getId())))
                .toList();
        int total = Math.toIntExact(rows.stream().filter(HubStallResponseDto::active).count());
        int available = Math.toIntExact(rows.stream().filter(row -> row.active() && !row.occupied()).count());
        return new HubStallInventoryResponseDto(rows, available, total);
    }

    @Override
    @Transactional
    public HubStallResponseDto create(Long hubId, HubStallCreateRequestDto request) {
        HubStall stall = request.toEntity(lockHub(hubId));
        if (stalls.existsByLocation_IdAndCode(hubId, stall.getCode())) {
            throw new DispatchRuleException("code", "Mã ô đã được sử dụng tại hub.", true);
        }
        stall.setActive(true);
        return HubStallResponseDto.from(stalls.save(stall), false);
    }

    @Override
    @Transactional
    public HubStallResponseDto deactivate(Long hubId, Long stallId) {
        lockHub(hubId);
        HubStall stall = requireStall(hubId, stallId);
        if (occupiedAt(hubId).contains(stallId)) {
            throw new DispatchRuleException("hubStallId", "Ô " + stall.getCode() + " đang có ngựa.", true);
        }
        stall.setActive(false);
        return HubStallResponseDto.from(stall, false);
    }

    @Override
    @Transactional
    public HubStallResponseDto move(Long stayId, HubStallChangeRequestDto request) {
        HorseStay stay = stays.findForUpdate(stayId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lưu trú " + stayId));
        if (!HubStallPolicy.isCompanyHubStay(stay) || !HubStallPolicy.isOpen(stay)) {
            throw new DispatchRuleException("stayId", "Chỉ đổi ô cho ngựa đang lưu trú tại hub.", true);
        }
        Long hubId = stay.getLocation().getId();
        lockHub(hubId);
        HubStall stall = requireStall(hubId, request.hubStallId());
        if (!stall.isActive() || occupiedAt(hubId).contains(stall.getId())) {
            throw new DispatchRuleException("hubStallId", "Ô " + stall.getCode() + " không còn trống.", true);
        }
        request.applyTo(stay, stall);
        stays.flush();
        return HubStallResponseDto.from(stall, true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void allocateOnArrival(HorseStay stay) {
        allocate(stay, true);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void allocateOnBalanceHold(HorseStay stay) {
        // Chuyển loại cho cùng một lần lưu trú, giữ ô và cảnh báo đã gửi lúc nhập hub.
        allocate(stay, false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForTransition(HorseStay stay) {
        if (HubStallPolicy.isCompanyHubStay(stay)) {
            lockHub(stay.getLocation().getId());
        }
    }

    private void allocate(HorseStay stay, boolean warnIfFull) {
        if (stay.getHubStall() != null || !HubStallPolicy.isCompanyHubStay(stay) || !HubStallPolicy.isOpen(stay)) {
            return;
        }
        Location hub = lockHub(stay.getLocation().getId());
        var occupied = occupiedAt(hub.getId());
        HubStallPolicy.firstAvailableStandard(stalls.findByLocation_IdOrderByCode(hub.getId()), occupied)
                .ifPresentOrElse(stay::setHubStall, () -> {
                    if (warnIfFull) {
                        warnCoordinators(stay, hub);
                    }
                });
    }

    private void warnCoordinators(HorseStay stay, Location hub) {
        var line = stay.getBookingHorse();
        var recipients = coordinators.forHorse(line, null);
        if (!recipients.isEmpty()) {
            notifications.notify(recipients, line.getBooking().getId(), "Hub không còn ô thường trống",
                    line.getHorse().getName() + " đã vào " + hub.getName() + " và cần được xếp ô.", "/coordinator/hub");
        }
    }

    private Set<Long> occupiedAt(Long hubId) {
        return Set.copyOf(stays.findOccupiedStallIds(hubId));
    }

    private HubStall requireStall(Long hubId, Long stallId) {
        return stalls.findById(stallId).filter(stall -> hubId.equals(stall.getLocation().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ô " + stallId + " tại hub " + hubId));
    }

    private Location lockHub(Long hubId) {
        return requireHub(locations.findForUpdate(hubId).orElseThrow(() -> missingHub(hubId)));
    }

    private static Location requireHub(Location location) {
        if (location.getLocationType() != LocationType.HUB) {
            throw new DispatchRuleException("hubId", "Địa điểm không phải hub.");
        }
        return location;
    }

    private static ResourceNotFoundException missingHub(Long hubId) {
        return new ResourceNotFoundException("Không tìm thấy hub " + hubId);
    }
}
