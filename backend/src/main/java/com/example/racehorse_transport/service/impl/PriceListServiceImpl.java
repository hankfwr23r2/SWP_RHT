package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.pricing.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.PriceListStatus;
import com.example.racehorse_transport.exception.PricingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.PriceListPolicy;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.PriceListService;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceListServiceImpl implements PriceListService {
    private final PriceListRepository lists;
    private final PriceItemRepository items;
    private final BillableServiceRepository services;
    private final RouteRepository routes;
    private final VehicleClassRepository vehicleClasses;
    private final StallTypeRepository stallTypes;

    @Override
    public List<PriceListResponseDto> list() {
        return lists.findAllByOrderByIdAsc().stream().map(PriceListResponseDto::from).toList();
    }

    @Override
    public PriceListResponseDto get(Long id) {
        return PriceListResponseDto.from(require(lists, id, "bảng giá"));
    }

    @Override
    @Transactional
    public PriceListResponseDto create(PriceListCreateRequestDto request) {
        PriceListPolicy.validateDates(request.validFrom(), request.validTo());
        PriceList list = request.toEntity();
        applyRates(list, request.marginRate(), request.fuelBufferRate(), request.depositRate());
        list.setStatus(PriceListStatus.DRAFT);
        return PriceListResponseDto.from(lists.save(list));
    }

    @Override
    @Transactional
    public PriceListResponseDto update(Long id, PriceListUpdateRequestDto request) {
        PriceList list = editable(id);
        PriceListPolicy.validateDates(request.validFrom(), request.validTo());
        applyRates(list, request.marginRate(), request.fuelBufferRate(), request.depositRate());
        request.applyTo(list);
        return PriceListResponseDto.from(list);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PriceList list = editable(id);
        write(() -> {
            lists.delete(list);
            lists.flush();
            return null;
        });
    }

    @Override
    @Transactional
    public PriceListResponseDto activate(Long id) {
        PriceList list = locked(id);
        PriceListPolicy.requireDraft(list);
        PriceListPolicy.validateDates(list.getValidFrom(), list.getValidTo());
        lists.lockActivationCurrency(list.getCurrencyCode());
        return write(() -> {
            lists.findByCurrencyCodeAndStatusAndValidToIsNull(list.getCurrencyCode(), PriceListStatus.ACTIVE)
                    .ifPresent(previous -> {
                        previous.setValidTo(PriceListPolicy.endBeforeReplacement(previous, list));
                        lists.flush();
                    });
            list.setStatus(PriceListStatus.ACTIVE);
            lists.flush();
            return PriceListResponseDto.from(list);
        });
    }

    @Override
    @Transactional
    public PriceListResponseDto archive(Long id) {
        PriceList list = locked(id);
        PriceListPolicy.requireArchivable(list);
        list.setStatus(PriceListStatus.ARCHIVED);
        return PriceListResponseDto.from(list);
    }

    @Override
    public List<PriceItemResponseDto> items(Long id) {
        require(lists, id, "bảng giá");
        return items.findByPriceList_Id(id).stream().sorted(Comparator.comparing(PriceItem::getId))
                .map(PriceItemResponseDto::from).toList();
    }

    @Override
    public PriceItemResponseDto getItem(Long id, Long itemId) {
        return PriceItemResponseDto.from(item(id, itemId));
    }

    @Override
    @Transactional
    public PriceItemResponseDto createItem(Long id, PriceItemCreateRequestDto request) {
        PriceList list = editable(id);
        PriceListPolicy.validateItem(request.kmFrom(), request.kmTo(), request.unitPrice());
        PriceItem item = request.toEntity(list, require(services, request.billableServiceId(), "dịch vụ"),
                optional(routes, request.routeId(), "tuyến"), optional(vehicleClasses, request.vehicleClassId(), "hạng xe"),
                optional(stallTypes, request.stallTypeCode(), "loại khoang"));
        item.setUnitPrice(request.unitPrice());
        return write(() -> PriceItemResponseDto.from(items.saveAndFlush(item)));
    }

    @Override
    @Transactional
    public PriceItemResponseDto updateItem(Long id, Long itemId, PriceItemUpdateRequestDto request) {
        editable(id);
        PriceItem item = item(id, itemId);
        PriceListPolicy.validateItem(request.kmFrom(), request.kmTo(), request.unitPrice());
        request.applyTo(item, require(services, request.billableServiceId(), "dịch vụ"),
                optional(routes, request.routeId(), "tuyến"), optional(vehicleClasses, request.vehicleClassId(), "hạng xe"),
                optional(stallTypes, request.stallTypeCode(), "loại khoang"));
        item.setUnitPrice(request.unitPrice());
        return write(() -> PriceItemResponseDto.from(items.saveAndFlush(item)));
    }

    @Override
    @Transactional
    public void deleteItem(Long id, Long itemId) {
        editable(id);
        PriceItem item = item(id, itemId);
        write(() -> {
            items.delete(item);
            items.flush();
            return null;
        });
    }

    @Override
    public List<BillableServiceResponseDto> billableServices() {
        return services.findByStatusOrderByCodeAsc(ActiveStatus.ACTIVE).stream()
                .map(BillableServiceResponseDto::from).toList();
    }

    private PriceList locked(Long id) {
        return lists.findForUpdate(id).orElseThrow(() -> missing("bảng giá", id));
    }

    private PriceList editable(Long id) {
        PriceList list = locked(id);
        PriceListPolicy.requireDraft(list);
        if (lists.isReferenced(id)) {
            throw new PricingRuleException("Bảng giá đã được tham chiếu.", true);
        }
        return list;
    }

    private PriceItem item(Long id, Long itemId) {
        return items.findByIdAndPriceList_Id(itemId, id).orElseThrow(() -> missing("dòng giá", itemId));
    }

    private static void applyRates(PriceList list, BigDecimal margin, BigDecimal fuel, BigDecimal deposit) {
        PriceListPolicy.validateRates(margin, fuel, deposit);
        list.setMarginRate(margin);
        list.setFuelBufferRate(fuel);
        list.setDepositRate(deposit);
    }

    private static <E, K> E require(CrudRepository<E, K> repository, K id, String resource) {
        return repository.findById(id).orElseThrow(() -> missing(resource, id));
    }

    private static <E, K> E optional(CrudRepository<E, K> repository, K id, String resource) {
        return id == null ? null : require(repository, id, resource);
    }

    private static ResourceNotFoundException missing(String resource, Object id) {
        return new ResourceNotFoundException("Không tìm thấy " + resource + " " + id + ".");
    }

    private static <T> T write(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql) {
                    switch (sql.getSQLState()) {
                        case "23P01" -> throw new PricingRuleException("Bảng giá đang hoạt động bị trùng thời hạn.", true);
                        case "23505" -> throw new PricingRuleException("Điều kiện của dòng giá đã tồn tại.", true);
                        case "23503" -> throw new PricingRuleException("Tham chiếu bảng giá hoặc dòng giá đã thay đổi.", true);
                        default -> { }
                    }
                }
            }
            throw ex;
        }
    }
}
