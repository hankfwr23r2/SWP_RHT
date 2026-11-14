package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.HorseTypeDTO;
import com.example.racehorse_transport.dto.admin.CreateHorseTypeRequest;
import com.example.racehorse_transport.dto.admin.UpdateHorseTypeInsuredValueRequest;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.HorseTypeRepository;
import com.example.racehorse_transport.service.AdminAuditWriter;
import com.example.racehorse_transport.service.HorseTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.example.racehorse_transport.service.AdminAuditWriter.values;

@Service
@RequiredArgsConstructor
public class HorseTypeServiceImpl implements HorseTypeService {

    private final HorseTypeRepository horseTypeRepository;
    private final HorseRepository horseRepository;
    private final AdminAuditWriter audit;

    @Override
    public List<HorseTypeDTO> findAll() {
        return horseTypeRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public HorseTypeDTO create(CreateHorseTypeRequest request) {
        if (horseTypeRepository.existsByTypeNameIgnoreCase(request.getTypeName().trim())) {
            throw new IllegalArgumentException("Giống ngựa '" + request.getTypeName().trim() + "' đã tồn tại trong hệ thống!");
        }

        HorseType horseType = new HorseType();
        horseType.setTypeName(request.getTypeName().trim());
        horseType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        horseType.setInsuredValue(request.getInsuredValue());

        HorseType saved = horseTypeRepository.save(horseType);
        audit.record("CREATE_HORSE_TYPE", "HORSE_TYPE", saved.getId(), saved.getTypeName(), "Tạo giống ngựa", null, snapshot(saved));
        return toDto(saved);
    }

    @Override
    @Transactional
    public HorseTypeDTO update(Integer id, CreateHorseTypeRequest request) {
        HorseType horseType = horseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giống ngựa ID: " + id));

        if (horseTypeRepository.existsByTypeNameIgnoreCaseAndIdNot(request.getTypeName().trim(), id)) {
            throw new IllegalArgumentException("Giống ngựa '" + request.getTypeName().trim() + "' đã được sử dụng bởi giống khác!");
        }

        var before = snapshot(horseType);
        horseType.setTypeName(request.getTypeName().trim());
        horseType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        horseType.setInsuredValue(request.getInsuredValue());

        HorseType saved = horseTypeRepository.save(horseType);
        audit.record("UPDATE_HORSE_TYPE", "HORSE_TYPE", id, saved.getTypeName(), "Cập nhật giống ngựa", before, snapshot(saved));
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        HorseType horseType = horseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giống ngựa ID: " + id));

        if (horseRepository.existsByHorseTypeID_Id(id)) {
            throw new IllegalArgumentException("Không thể xóa giống ngựa '" + horseType.getTypeName() + "' vì đang có hồ sơ ngựa trong hệ thống liên kết!");
        }

        var before = snapshot(horseType);
        horseTypeRepository.delete(horseType);
        audit.record("DELETE_HORSE_TYPE", "HORSE_TYPE", id, horseType.getTypeName(), "Xóa giống ngựa", before, null);
    }

    @Override
    @Transactional
    public HorseTypeDTO updateInsuredValue(Integer id, UpdateHorseTypeInsuredValueRequest request) {
        HorseType horseType = horseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giống ngựa ID: " + id));
        var before = snapshot(horseType);
        horseType.setInsuredValue(request.getInsuredValue());
        HorseType saved = horseTypeRepository.save(horseType);
        audit.record("UPDATE_INSURED_VALUE", "HORSE_TYPE", id, saved.getTypeName(), "Cập nhật giá trị bảo hiểm giống ngựa", before, snapshot(saved));
        return toDto(saved);
    }

    private Map<String, Object> snapshot(HorseType type) {
        return values("typeName", type.getTypeName(), "description", type.getDescription(), "insuredValue", type.getInsuredValue());
    }

    private HorseTypeDTO toDto(HorseType horseType) {
        return new HorseTypeDTO(horseType.getId(), horseType.getTypeName(), horseType.getDescription(), horseType.getInsuredValue());
    }
}
