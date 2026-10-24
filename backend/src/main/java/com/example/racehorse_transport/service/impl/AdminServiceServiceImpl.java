package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.admin.CategoryResponse;
import com.example.racehorse_transport.dto.admin.CreateServiceRequest;
import com.example.racehorse_transport.dto.admin.ServiceResponse;
import com.example.racehorse_transport.entity.Category;
import com.example.racehorse_transport.entity.Service;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.CategoryRepository;
import com.example.racehorse_transport.repository.ServiceRepository;
import com.example.racehorse_transport.service.AdminServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class AdminServiceServiceImpl implements AdminServiceService {

    private final ServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> getAllServices() {
        return serviceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(c -> CategoryResponse.builder()
                        .id(c.getId())
                        .categoryName(c.getCategoryName())
                        .description(c.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceResponse getServiceById(Integer id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ ID: " + id));
        return toResponse(service);
    }

    @Override
    @Transactional
    public ServiceResponse createService(CreateServiceRequest request) {
        if (request == null || request.getServiceName() == null || request.getServiceName().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên dịch vụ không được để trống!");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục ID: " + request.getCategoryId()));

        int nextId = serviceRepository.findMaxId() + 1;

        Service service = new Service();
        service.setId(nextId);
        service.setServiceName(request.getServiceName().trim());
        service.setCategoryID(category);
        service.setPrice(request.getPrice());
        service.setStatus(request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus().trim().toUpperCase()
                : "ACTIVE");

        Service saved = serviceRepository.save(service);
        log.info("Admin created service: ID={}, Name={}", saved.getId(), saved.getServiceName());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ServiceResponse updateService(Integer id, CreateServiceRequest request) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ ID: " + id));

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục ID: " + request.getCategoryId()));

        service.setServiceName(request.getServiceName().trim());
        service.setCategoryID(category);
        service.setPrice(request.getPrice());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            service.setStatus(request.getStatus().trim().toUpperCase());
        }

        Service updated = serviceRepository.save(service);
        log.info("Admin updated service: ID={}", updated.getId());
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void toggleServiceStatus(Integer id) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dịch vụ ID: " + id));

        String newStatus = "ACTIVE".equalsIgnoreCase(service.getStatus()) ? "INACTIVE" : "ACTIVE";
        service.setStatus(newStatus);
        serviceRepository.save(service);
        log.info("Admin toggled service ID={} status to {}", id, newStatus);
    }

    private ServiceResponse toResponse(Service s) {
        return ServiceResponse.builder()
                .id(s.getId())
                .serviceName(s.getServiceName())
                .categoryId(s.getCategoryID() != null ? s.getCategoryID().getId() : null)
                .categoryName(s.getCategoryID() != null ? s.getCategoryID().getCategoryName() : null)
                .price(s.getPrice())
                .status(s.getStatus())
                .build();
    }
}
