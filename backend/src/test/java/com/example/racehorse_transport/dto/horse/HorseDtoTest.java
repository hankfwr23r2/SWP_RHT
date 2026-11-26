package com.example.racehorse_transport.dto.horse;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.entity.enums.HorseStatus;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HorseDtoTest {

    private static HorseType type() {
        HorseType type = new HorseType();
        type.setId(3L);
        type.setName("Arabian");
        return type;
    }

    @Test
    void createLeavesOwnerChipStatusAndTimestampsToService() {
        HorseType type = type();
        Horse horse = new HorseCreateRequestDto("985000000000001", " Sao Mai ", HorseGender.MARE, (short) 2019, " Nâu ",
                3L, " ").toEntity(type);

        assertThat(horse.getName()).isEqualTo("Sao Mai");
        assertThat(horse.getColor()).isEqualTo("Nâu");
        assertThat(horse.getHorseType()).isSameAs(type);
        assertThat(horse.getIdentificationMarks()).isNull();
        assertThat(horse.getId()).isNull();
        assertThat(horse.getOwner()).isNull();
        assertThat(horse.getMicrochip()).isNull();
        assertThat(horse.getStatus()).isNull();
        assertThat(horse.getCreatedAt()).isNull();
        assertThat(horse.getUpdatedAt()).isNull();
    }

    @Test
    void updateKeepsIdentityChipAndStatus() {
        Customer owner = new Customer();
        Horse horse = new Horse();
        horse.setId(9L);
        horse.setOwner(owner);
        horse.setMicrochip("985000000000001");
        horse.setStatus(HorseStatus.RETIRED);

        Instant updatedAt = Instant.parse("2026-12-01T01:00:00Z");
        horse.setUpdatedAt(updatedAt);

        new HorseUpdateRequestDto("Tên mới", HorseGender.STALLION, (short) 2018, "Đen", 3L, " Sao trắng ").applyTo(horse, type());

        assertThat(horse.getName()).isEqualTo("Tên mới");
        assertThat(horse.getId()).isEqualTo(9L);
        assertThat(horse.getOwner()).isSameAs(owner);
        assertThat(horse.getMicrochip()).isEqualTo("985000000000001");
        assertThat(horse.getStatus()).isEqualTo(HorseStatus.RETIRED);
        assertThat(horse.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(horse.getIdentificationMarks()).isEqualTo("Sao trắng");
    }

    @Test
    void responsesCarryHorseType() {
        Horse horse = new Horse();
        horse.setId(9L);
        horse.setHorseType(type());

        HorseResponseDto response = HorseResponseDto.from(horse, "BK-1", List.of("HORSE_PASSPORT"), List.of());
        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.horseTypeName()).isEqualTo("Arabian");
        assertThat(response.activeBookingCode()).isEqualTo("BK-1");
        assertThat(response.missingDocumentTypes()).containsExactly("HORSE_PASSPORT");
        assertThat(HorseResponseDto.from(horse, null, List.of(), List.of()).activeBookingCode()).isNull();
        assertThat(HorseTypeResponseDto.from(type()).id()).isEqualTo(3L);
    }

    @Test
    void documentResponseShowsTypeEvenWithoutAFileAndCopiesReviewDetails() {
        DocumentType type = new DocumentType();
        type.setCode("HORSE_PASSPORT");
        type.setName("Hộ chiếu ngựa");
        var missing = HorseDocumentResponseDto.from(type, null, null, true);
        assertThat(missing.documentTypeCode()).isEqualTo("HORSE_PASSPORT");
        assertThat(missing.id()).isNull();
        assertThat(missing.status()).isNull();
        assertThat(missing.canUpload()).isTrue();

        Document document = new Document();
        document.setId(9L);
        document.setFileName("passport.pdf");
        document.setFileUrl("https://example.test/passport.pdf");
        document.setReviewNote("Thiếu trang");
        document.setUploadedAt(Instant.parse("2026-12-01T01:00:00Z"));
        document.setExpiryDate(LocalDate.of(2027, 2, 1));
        var response = HorseDocumentResponseDto.from(type, document, DocumentStatus.REJECTED, true);
        assertThat(response.id()).isEqualTo(9L);
        assertThat(response.fileName()).isEqualTo(document.getFileName());
        assertThat(response.fileUrl()).isEqualTo(document.getFileUrl());
        assertThat(response.reviewNote()).isEqualTo(document.getReviewNote());
        assertThat(response.uploadedAt()).isEqualTo(document.getUploadedAt());
        assertThat(response.expiryDate()).isEqualTo(document.getExpiryDate());
        assertThat(HorseResponseDto.HorseDocumentStatusResponseDto.from(response).status()).isEqualTo(DocumentStatus.REJECTED);
    }
}
