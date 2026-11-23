package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// Một lần giao nhận ở điểm dừng: các con cần giao nhận và kết quả quét chip từng con
public record HandoverResponseDto(
        Long id, HandoverType handoverType, HandoverStatus status, Long tripStopId, String handledByName, Instant waitStartedAt, Instant handedAt, String note,
        List<HandoverHorseDto> horses) {

    // scanned == null: chưa quét
    public record HandoverHorseDto(Long horseLegId, String bookingCode, String horseName, String scannedMicrochip, Boolean microchipMatched, String conditionNote) {
        static HandoverHorseDto from(HorseLeg leg, HandoverHorse scan) {
            return new HandoverHorseDto(leg.getId(), leg.getBookingHorse().getBooking().getCode(), leg.getBookingHorse().getHorse().getName(),
                    scan == null ? null : scan.getScannedMicrochip(), scan == null ? null : scan.getMicrochipMatched(),
                    scan == null ? null : scan.getConditionNote());
        }
    }

    public static HandoverResponseDto from(Handover handover, List<HorseLeg> legs, Map<Long, HandoverHorse> scansByLeg) {
        return new HandoverResponseDto(handover.getId(), handover.getHandoverType(), handover.getStatus(), handover.getTripStop().getId(),
                handover.getHandledBy().getFullName(), handover.getWaitStartedAt(), handover.getHandedAt(), handover.getNote(),
                legs.stream().map(leg -> HandoverHorseDto.from(leg, scansByLeg.get(leg.getId()))).toList());
    }
}
