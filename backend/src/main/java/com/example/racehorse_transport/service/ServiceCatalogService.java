package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.CategoryResponseDto;
import com.example.racehorse_transport.dto.admin.CreateServiceRequestDto;
import com.example.racehorse_transport.dto.admin.ServiceResponseDto;

import java.util.List;

public interface ServiceCatalogService {
    List<ServiceResponseDto> getAllServices();
    List<ServiceResponseDto> getActiveServices();
    List<CategoryResponseDto> getAllCategories();
    ServiceResponseDto getServiceById(Integer id);
    ServiceResponseDto createService(CreateServiceRequestDto request);
    ServiceResponseDto updateService(Integer id, CreateServiceRequestDto request);
    void toggleServiceStatus(Integer id);
}
