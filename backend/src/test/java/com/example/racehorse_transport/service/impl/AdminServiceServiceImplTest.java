package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.admin.CreateServiceRequest;
import com.example.racehorse_transport.entity.Category;
import com.example.racehorse_transport.entity.Service;
import com.example.racehorse_transport.repository.CategoryRepository;
import com.example.racehorse_transport.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminServiceServiceImplTest {

    private final ServiceRepository serviceRepository = mock(ServiceRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final AdminServiceServiceImpl service = new AdminServiceServiceImpl(serviceRepository, categoryRepository);
    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(2);
        category.setCategoryName("Gói thức ăn");
    }

    @Test
    void getActiveServices_readsOnlyActiveCatalogEntries() {
        Service item = service(3, "Gói cơ bản", "0", "ACTIVE");
        when(serviceRepository.findByStatusIgnoreCaseOrderByIdAsc("ACTIVE")).thenReturn(List.of(item));

        var result = service.getActiveServices();

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.getId()).isEqualTo(3);
            assertThat(response.getServiceName()).isEqualTo("Gói cơ bản");
            assertThat(response.getCategoryId()).isEqualTo(2);
            assertThat(response.getPrice()).isEqualByComparingTo("0");
        });
        verify(serviceRepository).findByStatusIgnoreCaseOrderByIdAsc("ACTIVE");
    }

    @Test
    void createService_persistsTheNewCatalogEntry() {
        when(categoryRepository.findById(2)).thenReturn(Optional.of(category));
        when(serviceRepository.findMaxId()).thenReturn(10);
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createService(CreateServiceRequest.builder()
                .serviceName("Gói mới")
                .categoryId(2)
                .price(new BigDecimal("125000"))
                .build());

        assertThat(response.getId()).isEqualTo(11);
        assertThat(response.getServiceName()).isEqualTo("Gói mới");
        assertThat(response.getPrice()).isEqualByComparingTo("125000");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        verify(serviceRepository).save(any(Service.class));
    }

    @Test
    void updateService_persistsEditedNameAndPrice() {
        Service existing = service(3, "Old name", "100000", "ACTIVE");
        when(serviceRepository.findById(3)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(2)).thenReturn(Optional.of(category));
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateService(3, CreateServiceRequest.builder()
                .serviceName("New name")
                .categoryId(2)
                .price(new BigDecimal("175000"))
                .build());

        assertThat(response.getServiceName()).isEqualTo("New name");
        assertThat(response.getPrice()).isEqualByComparingTo("175000");
        verify(serviceRepository).save(existing);
    }

    @Test
    void toggleServiceStatus_persistsTheNewStatus() {
        Service existing = service(3, "Gói cơ bản", "0", "ACTIVE");
        when(serviceRepository.findById(3)).thenReturn(Optional.of(existing));
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.toggleServiceStatus(3);

        assertThat(existing.getStatus()).isEqualTo("INACTIVE");
        verify(serviceRepository).save(existing);
    }

    private Service service(int id, String name, String price, String status) {
        Service item = new Service();
        item.setId(id);
        item.setServiceName(name);
        item.setCategoryID(category);
        item.setPrice(new BigDecimal(price));
        item.setStatus(status);
        return item;
    }
}
