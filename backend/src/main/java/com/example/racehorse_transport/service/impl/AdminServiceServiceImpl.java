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
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class AdminServiceServiceImpl implements AdminServiceService {

    private final ServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;

    private static final Map<Integer, String> CATEGORY_CODE_PREFIXES = Map.of(
            1, "STALL_",
            2, "FEED_",
            3, "WATER_",
            4, "INSURANCE_");
    private static final Integer RETIRED_WATER_CATEGORY_ID = 3;
    private static final Integer INSURANCE_CATEGORY_ID = 4;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> getAllServices() {
        return serviceRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .filter(service -> service.getCategoryID() == null || !RETIRED_WATER_CATEGORY_ID.equals(service.getCategoryID().getId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceResponse> getActiveServices() {
        return serviceRepository.findByStatusIgnoreCaseOrderByIdAsc("ACTIVE")
                .stream()
                .filter(service -> service.getCategoryID() == null || !RETIRED_WATER_CATEGORY_ID.equals(service.getCategoryID().getId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .filter(category -> !RETIRED_WATER_CATEGORY_ID.equals(category.getId()))
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

        ensureInsuranceCategoryReadOnly(category);
        validateServiceMetadata(request, category, null);
        int nextId = serviceRepository.findMaxId() + 1;

        Service service = new Service();
        service.setId(nextId);
        service.setServiceName(request.getServiceName().trim());
        service.setServiceCode(request.getServiceCode().trim());
        service.setBillingUnit(request.getBillingUnit().trim());
        service.setDescription(trimToNull(request.getDescription()));
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

        ensureInsuranceCategoryReadOnly(service.getCategoryID());
        ensureInsuranceCategoryReadOnly(category);
        validateServiceMetadata(request, category, service);
        service.setServiceName(request.getServiceName().trim());
        service.setBillingUnit(request.getBillingUnit().trim());
        service.setDescription(trimToNull(request.getDescription()));
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

        ensureInsuranceCategoryReadOnly(service.getCategoryID());
        String newStatus = "ACTIVE".equalsIgnoreCase(service.getStatus()) ? "INACTIVE" : "ACTIVE";
        if ("ACTIVE".equals(newStatus) && service.getCategoryID() != null
                && RETIRED_WATER_CATEGORY_ID.equals(service.getCategoryID().getId())) {
            throw new IllegalArgumentException("Danh mục Cữ nước đã ngừng cung cấp.");
        }
        service.setStatus(newStatus);
        serviceRepository.save(service);
        log.info("Admin toggled service ID={} status to {}", id, newStatus);
    }

    private ServiceResponse toResponse(Service s) {
        return ServiceResponse.builder()
                .id(s.getId())
                .serviceName(s.getServiceName())
                .serviceCode(s.getServiceCode())
                .billingUnit(s.getBillingUnit())
                .description(s.getDescription())
                .categoryId(s.getCategoryID() != null ? s.getCategoryID().getId() : null)
                .categoryName(s.getCategoryID() != null ? s.getCategoryID().getCategoryName() : null)
                .price(s.getPrice())
                .status(s.getStatus())
                .build();
    }

    private void validateServiceMetadata(CreateServiceRequest request, Category category, Service existing) {
        String code = request.getServiceCode().trim();
        if (existing != null && !code.equals(existing.getServiceCode())) {
            throw new IllegalArgumentException("Mã dịch vụ không thể thay đổi sau khi tạo.");
        }
        if (existing == null && serviceRepository.existsByServiceCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Mã dịch vụ đã được sử dụng.");
        }
        String expectedPrefix = CATEGORY_CODE_PREFIXES.get(category.getId());
        if (expectedPrefix == null || !code.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("Mã dịch vụ không khớp với danh mục đã chọn.");
        }
        if (RETIRED_WATER_CATEGORY_ID.equals(category.getId())) {
            throw new IllegalArgumentException("Danh mục Cữ nước đã ngừng cung cấp.");
        }
    }

    private void ensureInsuranceCategoryReadOnly(Category category) {
        if (category != null && INSURANCE_CATEGORY_ID.equals(category.getId())) {
            throw new IllegalArgumentException(
                    "Dịch vụ bảo hiểm chỉ có hai lựa chọn Mua/Từ chối và không thể thêm, sửa hoặc thay đổi trạng thái.");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
