package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.pricing.*;
import java.util.List;

public interface PriceListService {
    List<PriceListResponseDto> list();

    PriceListResponseDto get(Long id);

    PriceListResponseDto create(PriceListCreateRequestDto request);

    PriceListResponseDto update(Long id, PriceListUpdateRequestDto request);

    void delete(Long id);

    PriceListResponseDto activate(Long id);

    PriceListResponseDto archive(Long id);

    List<PriceItemResponseDto> items(Long id);

    PriceItemResponseDto getItem(Long id, Long itemId);

    PriceItemResponseDto createItem(Long id, PriceItemCreateRequestDto request);

    PriceItemResponseDto updateItem(Long id, Long itemId, PriceItemUpdateRequestDto request);

    void deleteItem(Long id, Long itemId);

    List<BillableServiceResponseDto> billableServices();
}
