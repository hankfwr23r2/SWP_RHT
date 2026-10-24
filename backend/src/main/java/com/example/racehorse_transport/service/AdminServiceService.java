package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.CategoryResponse;
import com.example.racehorse_transport.dto.admin.CreateServiceRequest;
import com.example.racehorse_transport.dto.admin.ServiceResponse;

import java.util.List;

public interface AdminServiceService {
    List<ServiceResponse> getAllServices();
    List<CategoryResponse> getAllCategories();
    ServiceResponse getServiceById(Integer id);
    ServiceResponse createService(CreateServiceRequest request);
    ServiceResponse updateService(Integer id, CreateServiceRequest request);
    void toggleServiceStatus(Integer id);
}
