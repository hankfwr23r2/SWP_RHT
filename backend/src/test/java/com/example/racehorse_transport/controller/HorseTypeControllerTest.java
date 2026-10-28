package com.example.racehorse_transport.controller;

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
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HorseTypeControllerTest {

    @Mock
    private HorseTypeRepository horseTypeRepository;

    @Mock
    private HorseRepository horseRepository;

    private HorseTypeController controller;

    @BeforeEach
    void setUp() {
        controller = new HorseTypeController(horseTypeRepository, horseRepository);
    }

    @Test
    void getHorseTypes_returnsMappedDtos() {
        HorseType type = new HorseType();
        type.setId(1);
        type.setTypeName("Thoroughbred");
        type.setDescription("Racing horse");
        type.setInsuredValue(new BigDecimal("1000000000"));

        when(horseTypeRepository.findAll(any(Sort.class))).thenReturn(List.of(type));

        var response = controller.getAllHorseTypes();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().getFirst().insuredValue())
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

        var response = controller.createHorseType(req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().typeName()).isEqualTo("Marwari");
        assertThat(response.getBody().getData().insuredValue()).isEqualByComparingTo("750000000");
    }

    @Test
    void createHorseType_duplicateThrows() {
        when(horseTypeRepository.existsByTypeNameIgnoreCase("Thoroughbred")).thenReturn(true);
        CreateHorseTypeRequest req = CreateHorseTypeRequest.builder()
                .typeName("Thoroughbred")
                .insuredValue(new BigDecimal("1000000000"))
                .build();

        assertThatThrownBy(() -> controller.createHorseType(req))
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

        var response = controller.updateHorseType(1, req);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().typeName()).isEqualTo("Thoroughbred Elite");
        assertThat(response.getBody().getData().insuredValue()).isEqualByComparingTo("1200000000");
    }

    @Test
    void deleteHorseType_successfulWhenUnlinked() {
        HorseType existing = new HorseType();
        existing.setId(99);
        existing.setTypeName("TestBreed");

        when(horseTypeRepository.findById(99)).thenReturn(Optional.of(existing));
        when(horseRepository.existsByHorseTypeID_Id(99)).thenReturn(false);

        var response = controller.deleteHorseType(99);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(horseTypeRepository).delete(existing);
    }

    @Test
    void deleteHorseType_failsWhenLinkedToHorses() {
        HorseType existing = new HorseType();
        existing.setId(1);
        existing.setTypeName("Thoroughbred");

        when(horseTypeRepository.findById(1)).thenReturn(Optional.of(existing));
        when(horseRepository.existsByHorseTypeID_Id(1)).thenReturn(true);

        assertThatThrownBy(() -> controller.deleteHorseType(1))
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
        var response = controller.updateInsuredValue(1, req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().insuredValue()).isEqualByComparingTo("1200000000");
        verify(horseTypeRepository).save(type);
    }
}
