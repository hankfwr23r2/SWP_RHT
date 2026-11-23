package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.client.ValhallaClient;
import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto.HorseSelection;
import com.example.racehorse_transport.dto.pricing.PricingPreviewRequestDto;
import com.example.racehorse_transport.dto.regulation.CountryRuleResponseDto;
import com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.exception.RoutingUnavailableException;
import com.example.racehorse_transport.policy.PricingPolicy.PricingInput;
import com.example.racehorse_transport.policy.PricingPolicy.RoadLeg;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.impl.PricingDataServiceImpl;
import com.example.racehorse_transport.service.impl.PricingRouteServiceImpl;
import com.example.racehorse_transport.service.impl.PricingServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class PricingServiceTest {
    private final Instant now = Instant.parse("2026-11-23T04:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final PriceListRepository lists = mock(PriceListRepository.class);
    private final PriceItemRepository items = mock(PriceItemRepository.class);
    private final CustomerAddressRepository addresses = mock(CustomerAddressRepository.class);
    private final HorseRepository horses = mock(HorseRepository.class);
    private final VehicleClassRepository classes = mock(VehicleClassRepository.class);
    private final ValhallaClient routing = mock(ValhallaClient.class);
    private final HubServiceAreaRepository hubs = mock(HubServiceAreaRepository.class);
    private final DepartureRepository departures = mock(DepartureRepository.class);
    private final RegulationService regulations = mock(RegulationService.class);
    private final SystemConfigService config = mock(SystemConfigService.class);
    private final DataSource source = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final DataSourceTransactionManager transactions = new DataSourceTransactionManager(source);
    private final PricingDataService data = transactional(PricingDataService.class,
            new PricingDataServiceImpl(lists, items, addresses, horses, departures, hubs, classes, clock,
                    regulations, config));
    private final PricingService service = transactional(PricingService.class,
            new PricingServiceImpl(data, transactional(PricingRouteService.class, new PricingRouteServiceImpl(routing, mock(EntityManagerFactory.class)))));

    private <T> T transactional(Class<T> contract, T target) {
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return contract.cast(proxy.getProxy());
    }

    @BeforeEach
    void configurePreview() throws Exception {
        when(source.getConnection()).thenReturn(connection);
        when(config.getInt("TRUCK_WEIGHT_TONS")).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return 8;
        });
        when(config.getInt("TRUCK_TOP_SPEED_KMH")).thenReturn(70);
        when(regulations.resolve(BookingType.LOCAL, "VN", "VN", LocalDate.of(2026, 11, 23), null))
                .thenReturn(new RegulationPreviewResponseDto(null, 0, List.of(), false, null, null));
        AppUser account = new AppUser();
        account.setUsername("owner");
        Customer owner = new Customer();
        owner.setUser(account);
        Country vn = new Country();
        vn.setCode("VN");
        Province province = new Province();
        province.setCode("VN-79");
        servedBy("VN-79", 4L);
        when(addresses.findById(1L)).thenReturn(Optional.of(address(owner, vn, province, "10.70")));
        when(addresses.findById(2L)).thenReturn(Optional.of(address(owner, vn, province, "10.80")));
        Horse horse = new Horse();
        horse.setOwner(owner);
        horse.setStatus(HorseStatus.ACTIVE);
        when(horses.findById(3L)).thenReturn(Optional.of(horse));
        when(items.findStallTypeCodes()).thenReturn(List.of("STANDARD"));
        VehicleClass big = vehicle(10L, 4);
        VehicleClass small = vehicle(11L, 2);
        when(classes.findAll()).thenReturn(List.of(big, small));
    }

    @Test
    void previewUsesTheSameLinesAsInternalCalculation() {
        PriceList list = new PriceList();
        list.setId(1L);
        list.setCurrencyCode("VND");
        list.setStatus(PriceListStatus.ACTIVE);
        list.setValidFrom(LocalDate.of(2026, 11, 23));
        list.setMarginRate(BigDecimal.ZERO);
        list.setFuelBufferRate(BigDecimal.ZERO);
        list.setDepositRate(new BigDecimal("0.3"));
        when(lists.findByCurrencyCodeAndStatus("VND", PriceListStatus.ACTIVE)).thenReturn(List.of(list));
        PriceItem direct = price(22L, ServiceLayer.DIRECT);
        PriceItem fuel = price(23L, ServiceLayer.FUEL);
        when(items.findByPriceList_Id(1L)).thenReturn(List.of(direct, fuel));
        when(items.findPricingCatalog(ActiveStatus.ACTIVE)).thenReturn(List.of(direct.getBillableService(), fuel.getBillableService()));
        when(routing.truckRoute(anyList(), eq(8.0), eq(70), any()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    assertThat(TransactionSynchronizationManager.hasResource(source)).isFalse();
                    verify(connection).close();
                    return new RoadRouteDto(35, 600, List.of());
                });
        var preview = service.preview(request(), "owner");
        var result = service.calculate(new PricingInput("VND", LocalDate.of(2026, 11, 23), BookingType.LOCAL,
                null, null, List.of(new RoadLeg(ServiceLayer.DIRECT, 11L, new BigDecimal("35.00"))),
                Map.of("STANDARD", 1), 1, 0, List.of()));
        assertThat(preview.lines()).extracting(line -> line.priceItemId()).containsExactly(22L, 23L);
        assertThat(preview.legs().getFirst().vehicleClassId()).isEqualTo(11L);
        assertThat(preview.lines()).isEqualTo(result.lines().stream()
                .map(com.example.racehorse_transport.dto.pricing.PriceLineResponseDto::from).toList());
        assertThat(preview.total()).isEqualTo(result.total());
    }

    @Test
    void rejectsAnotherCustomersAddressBeforeRouting() {
        assertThatThrownBy(() -> service.preview(request(), "other")).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(routing);
    }

    @Test
    void propagatesRoutingFailureInsteadOfEstimatingPrice() {
        when(routing.truckRoute(anyList(), eq(8.0), eq(70), any())).thenThrow(new RoutingUnavailableException());
        assertThatThrownBy(() -> service.preview(request(), "owner")).isInstanceOf(RoutingUnavailableException.class);
        verifyNoInteractions(lists);
    }

    @Test
    void releasesAnOpenInViewConnectionBeforeCallingValhalla() {
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        EntityManager manager = mock(EntityManager.class);
        SessionImplementor session = mock(SessionImplementor.class, RETURNS_DEEP_STUBS);
        when(manager.unwrap(SessionImplementor.class)).thenReturn(session);
        var logicalConnection = session.getJdbcCoordinator().getLogicalConnection();
        when(logicalConnection.isPhysicallyConnected()).thenReturn(true);
        PricingRouteService routeService = transactional(PricingRouteService.class,
                new PricingRouteServiceImpl(routing, factory));
        var segment = new PricingRouteService.RoadSegment(ServiceLayer.DIRECT,
                new GeoPointDto(10.70, 106.70), new GeoPointDto(10.80, 106.70));
        when(routing.truckRoute(anyList(), eq(10.0), eq(60), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            verify(logicalConnection).manualDisconnect();
            return new RoadRouteDto(35, 600, List.of());
        });

        TransactionSynchronizationManager.bindResource(factory, new EntityManagerHolder(manager));
        try {
            assertThat(routeService.distance(segment, LocalDateTime.of(2026, 11, 23, 12, 0), 10, 60))
                    .isEqualByComparingTo("35");
        } finally {
            TransactionSynchronizationManager.unbindResource(factory);
        }
    }

    @Test
    void refusesPreviewInsideAnExistingTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                assertThatThrownBy(() -> service.preview(request(), "owner"))
                        .isInstanceOf(IllegalTransactionStateException.class));
        verifyNoInteractions(routing);
    }

    @Test
    void resolvesSharedHubAsDirectAndUsesQuarantineFromRegulation() {
        CustomerAddress delivery = addresses.findById(2L).orElseThrow();
        Province secondProvince = new Province();
        secondProvince.setCode("VN-80");
        delivery.setProvince(secondProvince);
        servedBy("VN-80", 4L);
        var rule = new CountryRuleResponseDto(1L, "VN", RuleDirection.INTERPROVINCE, 0, 0, 4,
                LocalDate.of(2026, 1, 1), null);
        when(regulations.resolve(BookingType.INTERPROVINCE, "VN", "VN", LocalDate.of(2026, 11, 23), null))
                .thenReturn(new RegulationPreviewResponseDto(rule, 99, List.of(), false, null, null));

        var loaded = data.loadPreview(request(), "owner");
        assertThat(loaded.input().bookingType()).isEqualTo(BookingType.INTERPROVINCE);
        assertThat(loaded.input().quarantineDays()).isEqualTo(4);
        assertThat(loaded.segments()).extracting(PricingRouteService.RoadSegment::layer)
                .containsExactly(ServiceLayer.DIRECT);
        verifyNoInteractions(departures);

        servedBy("VN-80", 5L);
        assertThatThrownBy(() -> data.loadPreview(request(), "owner"))
                .isInstanceOf(com.example.racehorse_transport.exception.PricingRuleException.class)
                .hasMessageContaining("Cần chọn chuyến trục");
    }

    private void servedBy(String provinceCode, long hubId) {
        Location hub = new Location();
        hub.setId(hubId);
        hub.setStatus(ActiveStatus.ACTIVE);
        hub.setLocationType(LocationType.HUB);
        HubServiceArea area = new HubServiceArea();
        area.setHub(hub);
        when(hubs.findById_ProvinceCode(provinceCode)).thenReturn(Optional.of(area));
    }

    private PricingPreviewRequestDto request() {
        return new PricingPreviewRequestDto(1L, 2L, null, now.plusSeconds(3600),
                List.of(new HorseSelection(3L, "STANDARD", List.of())));
    }

    private static CustomerAddress address(Customer owner, Country country, Province province, String latitude) {
        CustomerAddress address = new CustomerAddress();
        address.setCustomer(owner);
        address.setCountry(country);
        address.setProvince(province);
        address.setLatitude(new BigDecimal(latitude));
        address.setLongitude(new BigDecimal("106.70"));
        return address;
    }

    private static VehicleClass vehicle(Long id, int capacity) {
        VehicleClass vehicle = new VehicleClass();
        vehicle.setId(id);
        vehicle.setMaxStallCount(capacity);
        return vehicle;
    }

    private static PriceItem price(Long id, ServiceLayer layer) {
        BillableService service = new BillableService();
        service.setId(id);
        service.setCode(layer.name());
        service.setLayer(layer);
        service.setBillingUnit(BillingUnit.PER_KM);
        PriceItem price = new PriceItem();
        price.setId(id);
        price.setBillableService(service);
        price.setUnitPrice(BigDecimal.TEN);
        return price;
    }
}
