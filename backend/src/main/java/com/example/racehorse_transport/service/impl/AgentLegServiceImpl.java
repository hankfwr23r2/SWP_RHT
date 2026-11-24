package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.AgentLegRequestDto;
import com.example.racehorse_transport.dto.dispatch.HorseLegResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AgentLegService;
import com.example.racehorse_transport.service.BookingStatusService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentLegServiceImpl implements AgentLegService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<BookingStatus> DISPATCHABLE = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    private static final Set<HorseLegStatus> INACTIVE = Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED);

    private final HorseLegRepository legs;
    private final BookingHorseRepository bookingHorses;
    private final BookingAddressRepository bookingAddresses;
    private final LocationRepository locations;
    private final PartyRepository parties;
    private final PartyRoleRepository partyRoles;
    private final StaffRepository staff;
    private final BookingStatusService bookingStatus;
    private final Clock clock;

    @Override
    @Transactional
    public HorseLegResponseDto create(Long bookingHorseId, AgentLegRequestDto request) {
        BookingHorse line = bookingHorses.findById(bookingHorseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa trong đơn " + bookingHorseId));
        Booking booking = line.getBooking();
        if (line.getStatus() != BookingHorseStatus.ACTIVE || !DISPATCHABLE.contains(booking.getStatus())) {
            throw new DispatchRuleException("bookingHorseId", "Ngựa hoặc đơn " + booking.getCode() + " không ở trạng thái lập chặng được.", true);
        }
        if (booking.getBookingType() != BookingType.EXPORT && booking.getBookingType() != BookingType.IMPORT) {
            throw new DispatchRuleException("bookingHorseId", "Chỉ đơn xuất hoặc nhập mới có chặng đại lý.");
        }
        DispatchPolicy.requirePlannedWindow(request.plannedStartAt(), request.plannedEndAt());
        Party agent = parties.findById(request.agentPartyId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đối tác " + request.agentPartyId()));
        LocalDate day = LocalDate.ofInstant(request.plannedStartAt(), BUSINESS_ZONE);
        boolean destAgent = partyRoles.findById_PartyId(agent.getId()).stream()
                .anyMatch(r -> r.getId().getRoleType() == PartyRoleType.DEST_AGENT && DispatchPolicy.roleValidOn(r.getValidFrom(), r.getValidTo(), day));
        if (!destAgent) {
            throw new DispatchRuleException("agentPartyId", agent.getDisplayName() + " không có vai trò đại lý nước đến còn hiệu lực vào ngày " + day + ".");
        }

        List<HorseLeg> chain = legs.findByBookingHorse_IdOrderBySequenceNumber(line.getId());
        List<HorseLeg> active = chain.stream().filter(l -> !INACTIVE.contains(l.getStatus())).toList();
        BookingAddress address = bookingAddresses.findByBooking_Id(booking.getId()).stream()
                .filter(a -> a.getAddressType() == (booking.getBookingType() == BookingType.EXPORT ? AddressRole.DELIVERY : AddressRole.PICKUP))
                .findFirst().orElseThrow(() -> new DispatchRuleException("bookingHorseId", "Đơn " + booking.getCode() + " thiếu địa chỉ."));

        HorseLeg leg = new HorseLeg();
        leg.setBookingHorse(line);
        leg.setSequenceNumber(chain.stream().mapToInt(HorseLeg::getSequenceNumber).max().orElse(0) + 1);
        leg.setAgentParty(agent);
        leg.setPlannedStartAt(request.plannedStartAt());
        leg.setPlannedEndAt(request.plannedEndAt());
        leg.setStatus(HorseLegStatus.PLANNED);
        if (booking.getBookingType() == BookingType.EXPORT) {
            // Nối sau chặng xe đã kết thúc ở cửa khẩu, đi tới địa chỉ giao
            HorseLeg previous = active.isEmpty() ? null : active.getLast();
            if (previous == null || previous.getAgentParty() != null || previous.getToLocation() == null
                    || previous.getToLocation().getLocationType() != LocationType.BORDER_GATE) {
                throw new DispatchRuleException("bookingHorseId", "Đơn xuất: lập chặng xe tới cửa khẩu trước rồi mới lập chặng đại lý.");
            }
            if (request.gateLocationId() != null && !request.gateLocationId().equals(previous.getToLocation().getId())) {
                throw new DispatchRuleException("gateLocationId", "Chặng đại lý phải bắt đầu ở cửa khẩu nơi chặng xe kết thúc.");
            }
            DispatchPolicy.requireAfterPreviousLeg(previous.getPlannedEndAt(), request.plannedStartAt());
            leg.setLegType(HorseLegType.DELIVERY);
            leg.setFromLocation(previous.getToLocation());
            leg.setToBookingAddress(address);
        } else {
            // Đơn nhập: đại lý đưa ngựa từ nơi gửi tới cửa khẩu, là chặng đầu tiên
            if (!active.isEmpty()) {
                throw new DispatchRuleException("bookingHorseId", "Đơn nhập: chặng đại lý phải là chặng đầu. Gỡ các chặng xe trước.", true);
            }
            if (request.gateLocationId() == null) {
                throw new DispatchRuleException("gateLocationId", "Đơn nhập phải chọn cửa khẩu đại lý giao ngựa.");
            }
            Location gate = locations.findById(request.gateLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm " + request.gateLocationId()));
            if (gate.getLocationType() != LocationType.BORDER_GATE) {
                throw new DispatchRuleException("gateLocationId", gate.getName() + " không phải cửa khẩu.");
            }
            leg.setLegType(HorseLegType.PICKUP);
            leg.setFromBookingAddress(address);
            leg.setToLocation(gate);
        }
        legs.saveAndFlush(leg);
        return HorseLegResponseDto.from(leg);
    }

    @Override
    @Transactional
    public void cancel(Long horseLegId) {
        HorseLeg leg = agentLeg(horseLegId);
        if (leg.getStatus() != HorseLegStatus.PLANNED) {
            throw new DispatchRuleException("status", "Chỉ gỡ được chặng chưa chạy.", true);
        }
        boolean hasLaterLeg = legs.findByBookingHorse_IdOrderBySequenceNumber(leg.getBookingHorse().getId()).stream()
                .anyMatch(l -> l.getSequenceNumber() > leg.getSequenceNumber() && !INACTIVE.contains(l.getStatus()));
        if (hasLaterLeg) {
            throw new DispatchRuleException("status", "Con ngựa còn chặng sau chặng này. Gỡ chặng sau trước.", true);
        }
        leg.setStatus(HorseLegStatus.CANCELLED);
        legs.flush();
    }

    @Override
    @Transactional
    public HorseLegResponseDto start(Long horseLegId) {
        HorseLeg leg = agentLeg(horseLegId);
        if (leg.getLegType() != HorseLegType.PICKUP || leg.getStatus() != HorseLegStatus.PLANNED) {
            throw new DispatchRuleException("status", "Chỉ ghi đại lý đã nhận ngựa cho chặng đón của đơn nhập chưa chạy.", true);
        }
        leg.setStatus(HorseLegStatus.IN_PROGRESS);
        leg.setActualStartAt(clock.instant());
        legs.flush();
        return HorseLegResponseDto.from(leg);
    }

    @Override
    @Transactional
    public HorseLegResponseDto complete(Long horseLegId, String username) {
        HorseLeg leg = agentLeg(horseLegId);
        // Chặng đại lý của đơn xuất chạy khi tổ xe bàn giao ở cửa khẩu (handover AGENT)
        if (leg.getLegType() != HorseLegType.DELIVERY || leg.getStatus() != HorseLegStatus.IN_PROGRESS) {
            throw new DispatchRuleException("status", "Chỉ ghi đã giao cho chặng giao của đại lý đang chạy (đã bàn giao ở cửa khẩu).", true);
        }
        leg.setStatus(HorseLegStatus.COMPLETED);
        leg.setActualEndAt(clock.instant());
        legs.flush();
        Booking booking = leg.getBookingHorse().getBooking();
        if (booking.getStatus() == BookingStatus.IN_OPERATION && legs.countActiveHorsesNotDelivered(booking.getId()) == 0) {
            Long actor = staff.findByUser_Username(username).orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")).getUserId();
            bookingStatus.transition(booking.getId(), BookingStatus.DELIVERED, "Đại lý đã giao xong mọi con ngựa", actor);
        }
        return HorseLegResponseDto.from(leg);
    }

    private HorseLeg agentLeg(Long id) {
        HorseLeg leg = legs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chặng " + id));
        if (leg.getAgentParty() == null) {
            throw new DispatchRuleException("horseLegId", "Đây không phải chặng của đại lý.", true);
        }
        return leg;
    }
}
