package com.example.racehorse_transport.dto.horse;

import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.entity.enums.HorseStatus;
import java.time.Instant;
import java.util.List;

public record HorseResponseDto(Long id, String name, String microchip, HorseGender sex, Short birthYear,
                               String color, Long horseTypeId, String horseTypeName, String identificationMarks,
                               HorseStatus status, Instant createdAt, Instant updatedAt, String activeBookingCode,
                               List<String> missingDocumentTypes, List<HorseDocumentStatusResponseDto> documentStatuses) {

    // activeBookingCode: mã đơn chưa kết thúc đang giữ con ngựa, null nếu không có
    public static HorseResponseDto from(Horse horse, String activeBookingCode, List<String> missingDocumentTypes,
            List<HorseDocumentStatusResponseDto> documentStatuses) {
        return new HorseResponseDto(horse.getId(), horse.getName(), horse.getMicrochip(), horse.getSex(), horse.getBirthYear(), horse.getColor(),
                horse.getHorseType().getId(), horse.getHorseType().getName(), horse.getIdentificationMarks(),
                horse.getStatus(), horse.getCreatedAt(), horse.getUpdatedAt(), activeBookingCode, missingDocumentTypes, documentStatuses);
    }

    public record HorseDocumentStatusResponseDto(String documentTypeCode, String documentTypeName, DocumentStatus status) {
        public static HorseDocumentStatusResponseDto from(HorseDocumentResponseDto document) {
            return new HorseDocumentStatusResponseDto(document.documentTypeCode(), document.documentTypeName(), document.status());
        }
    }
}
