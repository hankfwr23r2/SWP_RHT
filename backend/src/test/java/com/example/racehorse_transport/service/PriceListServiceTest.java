package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.dto.pricing.PriceListCreateRequestDto;
import com.example.racehorse_transport.dto.pricing.PriceListUpdateRequestDto;
import com.example.racehorse_transport.dto.pricing.PriceItemCreateRequestDto;
import com.example.racehorse_transport.dto.pricing.PriceItemUpdateRequestDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.PriceListStatus;
import com.example.racehorse_transport.exception.PricingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.impl.PriceListServiceImpl;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PriceListServiceTest {
    private final PriceListRepository lists = mock(PriceListRepository.class);
    private final PriceItemRepository items = mock(PriceItemRepository.class);
    private final BillableServiceRepository catalog = mock(BillableServiceRepository.class);
    private final PriceListService service = new PriceListServiceImpl(lists, items,
            catalog, mock(RouteRepository.class), mock(VehicleClassRepository.class),
            mock(StallTypeRepository.class));
    private final LocalDate date = LocalDate.of(2026, 11, 23);

    @Test
    void createsDraftWithValidatedRates() {
        when(lists.save(any(PriceList.class))).thenAnswer(call -> call.getArgument(0));
        var result = service.create(new PriceListCreateRequestDto("Base", "VND", BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("0.30"), date, null));
        assertThat(result.status()).isEqualTo(PriceListStatus.DRAFT);
        assertThat(result.depositRate()).isEqualByComparingTo("0.30");
    }

    @Test
    void activationFlushesPreviousEndBeforeChangingNewStatus() {
        PriceList previous = list(PriceListStatus.ACTIVE, date.minusDays(1));
        PriceList draft = list(PriceListStatus.DRAFT, date);
        when(lists.findForUpdate(2L)).thenReturn(Optional.of(draft));
        when(lists.findByCurrencyCodeAndStatusAndValidToIsNull("VND", PriceListStatus.ACTIVE))
                .thenReturn(Optional.of(previous));
        doAnswer(call -> {
            assertThat(previous.getValidTo()).isEqualTo(date.minusDays(1));
            assertThat(draft.getStatus()).isEqualTo(PriceListStatus.DRAFT);
            return null;
        }).doNothing().when(lists).flush();
        assertThat(service.activate(2L).status()).isEqualTo(PriceListStatus.ACTIVE);
        verify(lists, times(2)).flush();
    }

    @Test
    void mapsExclusionViolationToConflict() {
        when(lists.findForUpdate(2L)).thenReturn(Optional.of(list(PriceListStatus.DRAFT, date)));
        doThrow(new DataIntegrityViolationException("overlap", new SQLException("overlap", "23P01")))
                .when(lists).flush();
        assertThatThrownBy(() -> service.activate(2L)).isInstanceOfSatisfying(PricingRuleException.class,
                error -> assertThat(error.isConflict()).isTrue());
    }

    @Test
    void rejectsDeletingReferencedDraft() {
        when(lists.findForUpdate(2L)).thenReturn(Optional.of(list(PriceListStatus.DRAFT, date)));
        when(lists.isReferenced(2L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(PricingRuleException.class);
        verify(lists, never()).delete(any(PriceList.class));
    }

    @Test
    void readsUpdatesAndDeletesDraft() {
        PriceList draft = list(PriceListStatus.DRAFT, date);
        draft.setId(2L);
        when(lists.findAllByOrderByIdAsc()).thenReturn(List.of(draft));
        when(lists.findById(2L)).thenReturn(Optional.of(draft));
        when(lists.findForUpdate(2L)).thenReturn(Optional.of(draft));
        assertThat(service.list()).containsExactly(service.get(2L));
        var update = new PriceListUpdateRequestDto("Edited", "VND", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, date, null);
        assertThat(service.update(2L, update).name()).isEqualTo("Edited");
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
        service.delete(2L);
        verify(lists).delete(draft);
        verify(lists).flush();
    }

    @Test
    void managesItemsOnlyWithinTheirOwnDraft() {
        PriceList draft = list(PriceListStatus.DRAFT, date);
        draft.setId(2L);
        BillableService billable = new BillableService();
        billable.setId(3L);
        when(lists.findById(2L)).thenReturn(Optional.of(draft));
        when(lists.findForUpdate(2L)).thenReturn(Optional.of(draft));
        when(catalog.findById(3L)).thenReturn(Optional.of(billable));
        when(catalog.findByStatusOrderByCodeAsc(ActiveStatus.ACTIVE)).thenReturn(List.of(billable));
        when(items.saveAndFlush(any(PriceItem.class))).thenAnswer(call -> {
            PriceItem item = call.getArgument(0);
            item.setId(4L);
            return item;
        });
        var create = new PriceItemCreateRequestDto(3L, null, null, null, BigDecimal.valueOf(50), null, BigDecimal.ONE);
        assertThat(service.createItem(2L, create).unitPrice()).isEqualTo(BigDecimal.ONE);
        PriceItem item = create.toEntity(draft, billable, null, null, null);
        item.setId(4L);
        when(items.findByPriceList_Id(2L)).thenReturn(List.of(item));
        when(items.findByIdAndPriceList_Id(4L, 2L)).thenReturn(Optional.of(item));
        assertThat(service.billableServices()).hasSize(1);
        assertThat(service.items(2L)).containsExactly(service.getItem(2L, 4L));
        assertThatThrownBy(() -> service.getItem(9L, 4L)).isInstanceOf(ResourceNotFoundException.class);
        var update = new PriceItemUpdateRequestDto(3L, null, null, null, BigDecimal.valueOf(75), null, BigDecimal.TEN);
        var updated = service.updateItem(2L, 4L, update);
        assertThat(updated.unitPrice()).isEqualTo(BigDecimal.TEN);
        assertThat(updated.kmFrom()).isEqualByComparingTo("75");
        assertThat(updated.kmTo()).isNull();
        service.deleteItem(2L, 4L);
        verify(items).delete(item);
        draft.setStatus(PriceListStatus.ACTIVE);
        assertThatThrownBy(() -> service.updateItem(2L, 4L, update)).isInstanceOf(PricingRuleException.class);
        assertThat(service.archive(2L).status()).isEqualTo(PriceListStatus.ARCHIVED);
        assertThatThrownBy(() -> service.archive(2L)).isInstanceOf(PricingRuleException.class);
    }

    private static PriceList list(PriceListStatus status, LocalDate start) {
        PriceList list = new PriceList();
        list.setCurrencyCode("VND");
        list.setStatus(status);
        list.setValidFrom(start);
        return list;
    }
}
