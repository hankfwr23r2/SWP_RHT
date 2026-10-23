package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.document.HorseDocumentResponse;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseDocument;
import com.example.racehorse_transport.repository.HorseDocumentRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.service.StorageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HorseDocumentServiceImplTest {

    private final HorseDocumentRepository horseDocumentRepository = mock(HorseDocumentRepository.class);

    private final HorseDocumentServiceImpl service = new HorseDocumentServiceImpl(
            horseDocumentRepository,
            mock(HorseRepository.class),
            mock(StorageService.class));

    @Test
    void customerDocumentsAreLoadedForAllOfTheSignedInCustomersHorsesInOneQuery() {
        Horse horse = new Horse();
        horse.setId(2);
        horse.setHorseName("Thunder");
        HorseDocument document = HorseDocument.builder()
                .id(5)
                .horseID(horse)
                .documentType("PASSPORT")
                .fileUrl("https://files.example.com/passport.pdf")
                .build();
        when(horseDocumentRepository.findByHorseID_CustomerID_User_UsernameIgnoreCase("customer@example.com"))
                .thenReturn(List.of(document));

        List<HorseDocumentResponse> documents = service.getCustomerDocuments("customer@example.com");

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getHorseId()).isEqualTo(2);
        assertThat(documents.get(0).getDocumentType()).isEqualTo("PASSPORT");
        assertThat(documents.get(0).getFileUrl()).isEqualTo("https://files.example.com/passport.pdf");
    }
}
