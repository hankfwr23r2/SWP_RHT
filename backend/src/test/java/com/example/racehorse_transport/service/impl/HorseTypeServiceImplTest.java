package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.admin.CreateHorseTypeRequest;
import com.example.racehorse_transport.dto.admin.UpdateHorseTypeInsuredValueRequest;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.HorseTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorseTypeServiceImplTest {

    @Mock
    private HorseTypeRepository horseTypeRepository;

    @Mock
    private HorseRepository horseRepository;

    private HorseTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HorseTypeServiceImpl(horseTypeRepository, horseRepository, org.mockito.Mockito.mock(com.example.racehorse_transport.service.AdminAuditWriter.class));
    }

    @Test
    void getHorseTypes_returnsMappedDtos() {
        HorseType type = new HorseType();
        type.setId(1);
        type.setTypeName("Thoroughbred");
        type.setDescription("Racing horse");
        type.setInsuredValue(new BigDecimal("1000000000"));

        when(horseTypeRepository.findAll(any(Sort.class))).thenReturn(List.of(type));

        var response = service.findAll();
        assertThat(response).hasSize(1);
        assertThat(response.getFirst().insuredValue())
                .isEqualByComparingTo("1000000000");
    }

    @Test
    void createHorseType_createsAndReturnsDto() {
        when(horseTypeRepository.existsByTypeNameIgnoreCase("Marwari")).thenReturn(false);
        when(horseTypeRepository.save(any(HorseType.class))).thenAnswer(inv -> {
            HorseType t = inv.getArgument(0);
            t.setId(21);
            return t;
        });

        CreateHorseTypeRequest req = CreateHorseTypeRequest.builder()
                .typeName("Marwari")
                .description("Ngựa quý hiếm Ấn Độ")
                .insuredValue(new BigDecimal("750000000"))
                .build();

        var response = service.create(req);
        assertThat(response.typeName()).isEqualTo("Marwari");
        assertThat(response.insuredValue()).isEqualByComparingTo("750000000");
    }

    @Test
    void createHorseType_duplicateThrows() {
        when(horseTypeRepository.existsByTypeNameIgnoreCase("Thoroughbred")).thenReturn(true);
        CreateHorseTypeRequest req = CreateHorseTypeRequest.builder()
                .typeName("Thoroughbred")
                .insuredValue(new BigDecimal("1000000000"))
                .build();

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    void updateHorseType_updatesAllFields() {
        HorseType existing = new HorseType();
        existing.setId(1);
        existing.setTypeName("Thoroughbred");
        existing.setInsuredValue(new BigDecimal("1000000000"));

        when(horseTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(horseTypeRepository.existsByTypeNameIgnoreCaseAndIdNot("Thoroughbred Elite", 1)).thenReturn(false);
        when(horseTypeRepository.save(any(HorseType.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateHorseTypeRequest req = CreateHorseTypeRequest.builder()
                .typeName("Thoroughbred Elite")
                .description("Ngựa đua thuần chủng cao cấp")
                .insuredValue(new BigDecimal("1200000000"))
                .build();

        var response = service.update(1, req);
        assertThat(response.typeName()).isEqualTo("Thoroughbred Elite");
        assertThat(response.insuredValue()).isEqualByComparingTo("1200000000");
    }

    @Test
    void deleteHorseType_successfulWhenUnlinked() {
        HorseType existing = new HorseType();
        existing.setId(99);
        existing.setTypeName("TestBreed");

        when(horseTypeRepository.findById(99)).thenReturn(Optional.of(existing));
        when(horseRepository.existsByHorseTypeID_Id(99)).thenReturn(false);

        service.delete(99);
        verify(horseTypeRepository).delete(existing);
    }

    @Test
    void deleteHorseType_failsWhenLinkedToHorses() {
        HorseType existing = new HorseType();
        existing.setId(1);
        existing.setTypeName("Thoroughbred");

        when(horseTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(horseRepository.existsByHorseTypeID_Id(1)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đang có hồ sơ ngựa");
    }

    @Test
    void updateInsuredValue_updatesAndSaves() {
        HorseType type = new HorseType();
        type.setId(1);
        type.setTypeName("Thoroughbred");
        type.setInsuredValue(new BigDecimal("1000000000"));

        when(horseTypeRepository.findById(1)).thenReturn(Optional.of(type));
        when(horseTypeRepository.save(any(HorseType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateHorseTypeInsuredValueRequest req = new UpdateHorseTypeInsuredValueRequest(new BigDecimal("1200000000"));
        var response = service.updateInsuredValue(1, req);

        assertThat(response.insuredValue()).isEqualByComparingTo("1200000000");
        verify(horseTypeRepository).save(type);
    }
}
