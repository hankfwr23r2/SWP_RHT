package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.StallTypeResponseDto;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.StallTypeRepository;
import com.example.racehorse_transport.service.StallTypeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StallTypeServiceImpl implements StallTypeService {

    private final StallTypeRepository stallTypes;

    @Override
    @Transactional(readOnly = true)
    public List<StallTypeResponseDto> stallTypes() {
        return stallTypes.findAll(Sort.by("code")).stream()
                .map(type -> StallTypeResponseDto.from(type, StallSeatPolicy.seats(type.getCode()))).toList();
    }
}
