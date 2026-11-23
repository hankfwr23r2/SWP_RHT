package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.pricing.*;
import com.example.racehorse_transport.service.PriceListService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class PriceListController {
    private final PriceListService service;

    @GetMapping("/price-lists")
    public ApiResponseDto<List<PriceListResponseDto>> list() {
        return ApiResponseDto.success(service.list(), "Thành công.");
    }

    @GetMapping("/price-lists/{id}")
    public ApiResponseDto<PriceListResponseDto> get(@PathVariable Long id) {
        return ApiResponseDto.success(service.get(id), "Thành công.");
    }

    @PostMapping("/price-lists")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDto<PriceListResponseDto> create(@Valid @RequestBody PriceListCreateRequestDto request) {
        return ApiResponseDto.success(service.create(request), "Thành công.");
    }

    @PutMapping("/price-lists/{id}")
    public ApiResponseDto<PriceListResponseDto> update(@PathVariable Long id, @Valid @RequestBody PriceListUpdateRequestDto request) {
        return ApiResponseDto.success(service.update(id, request), "Thành công.");
    }

    @DeleteMapping("/price-lists/{id}")
    public ApiResponseDto<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponseDto.success(null, "Đã xóa.");
    }

    @PostMapping("/price-lists/{id}/activate")
    public ApiResponseDto<PriceListResponseDto> activate(@PathVariable Long id, @Valid @RequestBody PriceListActivateRequestDto request) {
        return ApiResponseDto.success(service.activate(id), "Thành công.");
    }

    @PostMapping("/price-lists/{id}/archive")
    public ApiResponseDto<PriceListResponseDto> archive(@PathVariable Long id, @Valid @RequestBody PriceListArchiveRequestDto request) {
        return ApiResponseDto.success(service.archive(id), "Thành công.");
    }

    @GetMapping("/price-lists/{id}/items")
    public ApiResponseDto<List<PriceItemResponseDto>> items(@PathVariable Long id) {
        return ApiResponseDto.success(service.items(id), "Thành công.");
    }

    @GetMapping("/price-lists/{id}/items/{itemId}")
    public ApiResponseDto<PriceItemResponseDto> getItem(@PathVariable Long id, @PathVariable Long itemId) {
        return ApiResponseDto.success(service.getItem(id, itemId), "Thành công.");
    }

    @PostMapping("/price-lists/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponseDto<PriceItemResponseDto> createItem(@PathVariable Long id, @Valid @RequestBody PriceItemCreateRequestDto request) {
        return ApiResponseDto.success(service.createItem(id, request), "Thành công.");
    }

    @PutMapping("/price-lists/{id}/items/{itemId}")
    public ApiResponseDto<PriceItemResponseDto> updateItem(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody PriceItemUpdateRequestDto request) {
        return ApiResponseDto.success(service.updateItem(id, itemId, request), "Thành công.");
    }

    @DeleteMapping("/price-lists/{id}/items/{itemId}")
    public ApiResponseDto<Void> deleteItem(@PathVariable Long id, @PathVariable Long itemId) {
        service.deleteItem(id, itemId);
        return ApiResponseDto.success(null, "Đã xóa.");
    }

    @GetMapping("/billable-services")
    public ApiResponseDto<List<BillableServiceResponseDto>> billableServices() {
        return ApiResponseDto.success(service.billableServices(), "Thành công.");
    }
}
