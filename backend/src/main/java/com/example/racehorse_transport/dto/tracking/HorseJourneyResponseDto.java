package com.example.racehorse_transport.dto.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.MicrochipEntryMethod;
import com.example.racehorse_transport.entity.enums.QuarantineResult;
import com.example.racehorse_transport.policy.CustomerNoticePolicy.HourWindow;
import com.example.racehorse_transport.policy.TrackingPolicy.CustodyHolder;
import java.time.Instant;
import java.util.List;

// Hành trình một con ngựa: chặng, lưu trú, giao nhận và hiện đang trong tay ai. Giao diện tự xếp theo thời gian
public record HorseJourneyResponseDto(
        Long bookingHorseId, String horseName, String microchip, BookingHorseStatus status,
        CustodyHolder holder, String holderPartyName, Instant holderSince,
        List<JourneyLegDto> legs, List<JourneyStayDto> stays, List<JourneyHandoverDto> handovers,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<JourneyHealthLogResponseDto> healthLogs) {

    public record JourneyLegDto(Long id, Integer sequenceNumber, HorseLegType legType, HorseLegStatus status, String fromName, String toName,
                         String vehicleTripCode, Long replacedHorseLegId,
                         Instant plannedStartAt, Instant plannedEndAt, Instant actualStartAt, Instant actualEndAt,
                         ExpectedWindowDto pickupWindow, ExpectedWindowDto deliveryWindow) {

        public static JourneyLegDto from(HorseLeg leg, HourWindow pickupWindow, HourWindow deliveryWindow) {
            return new JourneyLegDto(leg.getId(), leg.getSequenceNumber(), leg.getLegType(), leg.getStatus(),
                    nameOf(leg.getFromLocation(), leg.getFromBookingAddress()), nameOf(leg.getToLocation(), leg.getToBookingAddress()),
                    leg.getVehicleTrip() == null ? null : leg.getVehicleTrip().getCode(),
                    leg.getReplacedHorseLeg() == null ? null : leg.getReplacedHorseLeg().getId(),
                    leg.getPlannedStartAt(), leg.getPlannedEndAt(), leg.getActualStartAt(), leg.getActualEndAt(),
                    ExpectedWindowDto.from(pickupWindow), ExpectedWindowDto.from(deliveryWindow));
        }
    }

    // Khung giờ xe dự kiến tới điểm đón, điểm giao của khách
    public record ExpectedWindowDto(Instant from, Instant to) {

        public static ExpectedWindowDto from(HourWindow window) {
            return window == null ? null : new ExpectedWindowDto(window.from(), window.to());
        }
    }

    public record JourneyStayDto(Long id, HorseStayType stayType, String locationName, String operatorName,
                          Instant plannedStartAt, Instant plannedEndAt, Instant actualStartAt, Instant actualEndAt, QuarantineResult quarantineResult) {

        public static JourneyStayDto from(HorseStay stay) {
            return new JourneyStayDto(stay.getId(), stay.getStayType(), stay.getLocation().getName(), nameOf(stay.getOperatorParty()),
                    stay.getPlannedStartAt(), stay.getPlannedEndAt(), stay.getActualStartAt(), stay.getActualEndAt(), stay.getQuarantineResult());
        }
    }

    // placeName: điểm dừng của xe hoặc nơi lưu trú
    public record JourneyHandoverDto(Long id, HandoverType handoverType, HandoverStatus status, String placeName,
                              String fromPartyName, String toPartyName, Instant waitStartedAt, Instant handedAt, Boolean microchipMatched,
                              List<HandoverEvidenceResponseDto> evidence,
                              @JsonInclude(JsonInclude.Include.NON_NULL) MicrochipEntryMethod entryMethod,
                              @JsonInclude(JsonInclude.Include.NON_NULL) String manualReason) {

        // row == null: lần giao nhận đang mở và chưa ghi kết quả chip của con này
        public static JourneyHandoverDto from(Handover handover, HandoverHorse row, boolean includeChipEntry,
                                              List<HandoverEvidenceResponseDto> evidence) {
            var stop = handover.getTripStop();
            String place = stop != null ? nameOf(stop.getLocation(), stop.getBookingAddress()) : handover.getHorseStay().getLocation().getName();
            HandoverHorse chipEntry = includeChipEntry ? row : null;
            return new JourneyHandoverDto(handover.getId(), handover.getHandoverType(), handover.getStatus(), place,
                    nameOf(handover.getFromParty()), nameOf(handover.getToParty()), handover.getWaitStartedAt(), handover.getHandedAt(),
                    row == null ? null : row.getMicrochipMatched(), evidence,
                    chipEntry == null ? null : chipEntry.getMicrochipEntryMethod(), chipEntry == null ? null : chipEntry.getManualEntryReason());
        }
    }

    public record HandoverEvidenceResponseDto(Long id, String type, String fileName, String fileUrl) {
        public static HandoverEvidenceResponseDto from(Document document) {
            return new HandoverEvidenceResponseDto(document.getId(), document.getDocumentType().getCode(),
                    document.getFileName(), document.getFileUrl());
        }
    }

    static String nameOf(Location location, BookingAddress address) {
        return location != null ? location.getName() : address.getAddressLine();
    }

    static String nameOf(Party party) {
        return party == null ? null : party.getDisplayName();
    }
}
