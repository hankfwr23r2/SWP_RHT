package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.MicrochipEntryMethod;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Một lần giao nhận ở điểm dừng: các con cần giao nhận và kết quả quét chip từng con; partyName: đối tác giao hoặc nhận (đại lý ở cửa khẩu); recordAttached: đã tải biên bản giao nhận
public record HandoverResponseDto(
        Long id, HandoverType handoverType, HandoverStatus status, Long tripStopId, String handledByName, Instant waitStartedAt, Instant handedAt, String note,
        List<HandoverHorseDto> horses, String partyName, Boolean recordAttached) {

    // scan == null: chưa nhập mã chip
    public record HandoverHorseDto(Long horseLegId, String bookingCode, String horseName, String scannedMicrochip,
                                  Boolean microchipMatched, String conditionNote, Boolean photoAttached,
                                  MicrochipEntryMethod entryMethod, String manualReason) {
        static HandoverHorseDto from(HorseLeg leg, HandoverHorse scan, boolean photoAttached) {
            return new HandoverHorseDto(leg.getId(), leg.getBookingHorse().getBooking().getCode(), leg.getBookingHorse().getHorse().getName(),
                    scan == null ? null : scan.getScannedMicrochip(), scan == null ? null : scan.getMicrochipMatched(),
                    scan == null ? null : scan.getConditionNote(), photoAttached,
                    scan == null ? null : scan.getMicrochipEntryMethod(), scan == null ? null : scan.getManualEntryReason());
        }
    }

    public static HandoverResponseDto from(Handover handover, List<HorseLeg> legs, Map<Long, HandoverHorse> scansByLeg, boolean recordAttached,
                                           Set<Long> photographedLegs) {
        return new HandoverResponseDto(handover.getId(), handover.getHandoverType(), handover.getStatus(), handover.getTripStop().getId(),
                handover.getHandledBy().getFullName(), handover.getWaitStartedAt(), handover.getHandedAt(), handover.getNote(),
                legs.stream().map(leg -> HandoverHorseDto.from(leg, scansByLeg.get(leg.getId()), photographedLegs.contains(leg.getId()))).toList(),
                handover.getToParty() != null ? handover.getToParty().getDisplayName()
                        : handover.getFromParty() != null ? handover.getFromParty().getDisplayName() : null,
                recordAttached);
    }
}
