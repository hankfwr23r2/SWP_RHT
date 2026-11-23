package com.example.racehorse_transport.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingPartyRole;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.CustomerType;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.entity.enums.HorseStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import com.example.racehorse_transport.entity.enums.RuleDirection;
import com.example.racehorse_transport.entity.enums.TermsKind;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BillableServiceRepository;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseAddOnRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingPartyRepository;
import com.example.racehorse_transport.repository.BookingRatingRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.CountryRepository;
import com.example.racehorse_transport.repository.CountryRuleRepository;
import com.example.racehorse_transport.repository.CustomerAddressRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.DepartureScheduleDayRepository;
import com.example.racehorse_transport.repository.DepartureScheduleRepository;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.HorseTypeRepository;
import com.example.racehorse_transport.repository.HubServiceAreaRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.repository.PriceItemRepository;
import com.example.racehorse_transport.repository.PriceListRepository;
import com.example.racehorse_transport.repository.ProvinceRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.repository.RouteStopRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.StallTypeRepository;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// Entity nhóm 1–8 map đúng bảng: đọc được dữ liệu seed qua quan hệ, và ghi rồi đọc lại được trọn một đơn.
// ddl-auto=validate đã kiểm tên cột và kiểu; test này kiểm thêm khóa ghép, @MapsId, cột char và cột do DB sinh giá trị.
@PostgresIntegrationTest
@Transactional
class EntityMappingTest {

    @Autowired private EntityManager em;
    @Autowired private RouteRepository routes;
    @Autowired private RouteStopRepository routeStops;
    @Autowired private StaffRepository staff;
    @Autowired private HubServiceAreaRepository hubServiceAreas;
    @Autowired private AppUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private CustomerAddressRepository customerAddresses;
    @Autowired private PartyRepository parties;
    @Autowired private PartyRoleRepository partyRoles;
    @Autowired private PriceListRepository priceLists;
    @Autowired private PriceItemRepository priceItems;
    @Autowired private CountryRepository countries;
    @Autowired private ProvinceRepository provinces;
    @Autowired private CountryRuleRepository countryRules;
    @Autowired private DocumentRequirementRepository requirements;
    @Autowired private DepartureScheduleRepository schedules;
    @Autowired private DepartureScheduleDayRepository scheduleDays;
    @Autowired private DepartureRepository departures;
    @Autowired private HorseTypeRepository horseTypes;
    @Autowired private HorseRepository horses;
    @Autowired private StallTypeRepository stallTypes;
    @Autowired private BillableServiceRepository services;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingPartyRepository bookingParties;
    @Autowired private BookingAddressRepository bookingAddresses;
    @Autowired private BookingHorseRepository bookingHorses;
    @Autowired private BookingHorseAddOnRepository addOns;
    @Autowired private BookingStatusHistoryRepository statusHistory;
    @Autowired private TermsAcceptanceRepository termsAcceptances;
    @Autowired private BookingRatingRepository ratings;

    @Test
    void routeStopsKeepOrderAndLocations() {
        Route route = routes.findByCode("HCM-DN").orElseThrow();
        List<RouteStop> stops = routeStops.findById_RouteIdOrderById_SequenceNumber(route.getId());

        assertThat(route.getOrigin().getCode()).isEqualTo("HUB-HCM");
        assertThat(route.getDestination().getLocationType()).isEqualTo(LocationType.HUB);
        assertThat(route.getDistanceKm()).isEqualByComparingTo("960.00");
        assertThat(stops).extracting(s -> s.getId().getSequenceNumber()).containsExactly(1, 2, 3, 4, 5);
        assertThat(stops.get(0).getLocation().getCode()).isEqualTo("HUB-HCM");
        assertThat(stops.get(2).getStopType()).isEqualTo(RouteStopType.VITAL_CHECK);
        assertThat(stops.get(4).getLocation().getCode()).isEqualTo("HUB-DN");
    }

    @Test
    void staffSharesKeyWithUserAndBelongsToHeadquarters() {
        Staff driver = staff.findByStaffCode("DRV-001").orElseThrow();

        assertThat(driver.getUserId()).isEqualTo(driver.getUser().getId());
        assertThat(driver.getUser().getUsername()).isEqualTo("driver1.dev");
        assertThat(driver.getHomeHubLocation().getCode()).isEqualTo("HUB-HCM");
        assertThat(driver.getDriverLicenseExpiryDate()).isEqualTo(LocalDate.of(2030, 12, 31));
    }

    @Test
    void provinceResolvesToItsHub() {
        HubServiceArea area = hubServiceAreas.findById_ProvinceCode("VN-46").orElseThrow();

        assertThat(area.getHub().getCode()).isEqualTo("HUB-DN");
        assertThat(area.getProvince().getCountry().getCode()).isEqualTo("VN");
        assertThat(provinces.findByCountry_CodeOrderByName("LA")).hasSize(18);
        assertThat(countries.findById("KH").orElseThrow().getDefaultCurrencyCode()).isEqualTo("KHR");
    }

    @Test
    void customerSharesKeyWithAccount() {
        AppUser user = users.findByUsername("customer1.dev").orElseThrow();
        Customer customer = customers.findById(user.getId()).orElseThrow();

        assertThat(users.findByEmailIgnoreCase("CUSTOMER1.DEV@racehorse.test")).contains(user);
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getRole().getCode()).isEqualTo("CUSTOMER");
        assertThat(customer.getUser()).isEqualTo(user);
        assertThat(customer.getCustomerType()).isEqualTo(CustomerType.PERSON);
        assertThat(customer.getCountry().getCode()).isEqualTo("VN");
    }

    @Test
    void partnerKeepsItsRoles() {
        Party agent = parties.findAll().stream().filter(p -> p.getDisplayName().startsWith("Đại lý")).findFirst().orElseThrow();

        assertThat(partyRoles.findById_PartyId(agent.getId())).extracting(r -> r.getId().getRoleType()).containsExactly(PartyRoleType.DEST_AGENT);
    }

    @Test
    void priceItemsCarryTheirConditions() {
        PriceList list = priceLists.findAll().stream().filter(p -> p.getName().equals("Bảng giá mẫu (dev)")).findFirst().orElseThrow();
        List<PriceItem> items = priceItems.findByPriceList_Id(list.getId());

        assertThat(list.getCurrencyCode()).isEqualTo("VND");
        assertThat(list.getDepositRate()).isEqualByComparingTo("0.3");
        assertThat(items).anySatisfy(item -> {
            assertThat(item.getBillableService().getCode()).isEqualTo("LINEHAUL_STALL");
            assertThat(item.getRoute().getCode()).isEqualTo("HCM-PNH");
            assertThat(item.getStallType().getCode()).isEqualTo("STANDARD");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("6000000");
        });
        assertThat(items).anySatisfy(item -> {
            assertThat(item.getBillableService().getCode()).isEqualTo("PICKUP_KM");
            assertThat(item.getVehicleClass().getCode()).isEqualTo("HEAVY");
            assertThat(item.getKmFrom()).isEqualByComparingTo("50");
        });
    }

    @Test
    void countryRuleListsItsRequiredDocuments() {
        CountryRule rule = countryRules.findAll().stream()
                .filter(r -> r.getCountry().getCode().equals("KH") && r.getDirection() == RuleDirection.EXPORT)
                .findFirst().orElseThrow();

        List<DocumentRequirement> list = requirements.findByRule_Id(rule.getId());
        assertThat(list).hasSize(6);
        assertThat(list).anySatisfy(r -> {
            assertThat(r.getDocumentType().getCode()).isEqualTo("LAB_TEST");
            assertThat(r.getDocumentType().getOwnerType()).isEqualTo(DocumentSubject.BOOKING_HORSE);
            assertThat(r.getMandatory()).isTrue();
        });
    }

    @Test
    void newDepartureStartsWithNoBookedStalls() {
        DepartureSchedule schedule = schedules.findAll().stream()
                .filter(s -> s.getRoute().getCode().equals("HCM-PNH")).findFirst().orElseThrow();
        assertThat(scheduleDays.findById_DepartureScheduleId(schedule.getId())).extracting(d -> d.getId().getDayOfWeek())
                .containsExactlyInAnyOrder((short) 1, (short) 4);

        // Chuyến đột xuất (không theo lịch) để không trùng các chuyến do seed sinh từ lịch
        Departure departure = new Departure();
        departure.setRoute(schedule.getRoute());
        departure.setDepartureDate(LocalDate.of(2026, 12, 21));
        departure.setPlannedDepartureAt(Instant.parse("2026-12-20T23:00:00Z"));
        departure.setBookingCutoffAt(Instant.parse("2026-12-17T23:00:00Z"));
        departure.setStallCapacity(schedule.getStallCapacity());
        departure.setStatus(DepartureStatus.OPEN);
        Long id = departures.saveAndFlush(departure).getId();
        em.clear();

        assertThat(departures.findById(id).orElseThrow().getBookedStallCount()).isZero();
    }

    @Test
    void bookingAggregateRoundTrip() {
        AppUser customerUser = users.findByUsername("customer1.dev").orElseThrow();
        Customer customer = customers.findById(customerUser.getId()).orElseThrow();

        Booking booking = new Booking();
        booking.setCode("TEST-MAPPING-1");
        booking.setCustomer(customer);
        booking.setBookingType(BookingType.EXPORT);
        booking.setRequestedPickupDate(LocalDate.of(2026, 12, 13));
        booking.setStatus(BookingStatus.PENDING_INTAKE);
        booking.setCreatedBy(customerUser);
        booking = bookings.saveAndFlush(booking);

        BookingParty payer = new BookingParty();
        payer.setId(new BookingPartyId(booking.getId(), BookingPartyRole.PAYER));
        payer.setBooking(booking);
        payer.setFullName("Nguyễn Văn Khách");
        payer.setPhone("0909000001");
        payer.setIdentityDocumentCountryCode("VN");
        payer.setAddress("Phường Thủ Dầu Một, TP.HCM");
        bookingParties.save(payer);

        CustomerAddress saved = new CustomerAddress();
        saved.setCustomer(customer);
        saved.setLabel("Trang trại");
        saved.setAddressLine("Trang trại Long Thành");
        saved.setProvince(provinces.findById("VN-75").orElseThrow());
        saved.setCountry(countries.findById("VN").orElseThrow());
        saved = customerAddresses.save(saved);

        BookingAddress pickup = new BookingAddress();
        pickup.setSourceAddress(saved);
        pickup.setBooking(booking);
        pickup.setAddressType(AddressRole.PICKUP);
        pickup.setAddressLine("Trang trại Long Thành");
        pickup.setProvince(provinces.findById("VN-75").orElseThrow());
        pickup.setCountry(countries.findById("VN").orElseThrow());
        pickup.setLatitude(new BigDecimal("10.780000"));
        pickup.setLongitude(new BigDecimal("106.950000"));
        pickup.setContactName("Anh Ba");
        pickup.setContactPhone("0909000002");
        bookingAddresses.save(pickup);

        Horse horse = new Horse();
        horse.setOwner(customer);
        horse.setName("Sao Mai");
        horse.setMicrochip("985000000000777");
        horse.setSex(HorseGender.MARE);
        horse.setBirthYear((short) 2019);
        horse.setHorseType(horseTypes.findAll().get(0));
        horse.setStatus(HorseStatus.ACTIVE);
        horse = horses.save(horse);

        BookingHorse line = new BookingHorse();
        line.setBooking(booking);
        line.setHorse(horse);
        line.setStallType(stallTypes.findById("STANDARD").orElseThrow());
        line.setStatus(BookingHorseStatus.ACTIVE);
        line = bookingHorses.saveAndFlush(line);

        BillableService feeding = services.findByCode("FEEDING").orElseThrow();
        BookingHorseAddOn addOn = new BookingHorseAddOn();
        addOn.setId(new BookingHorseAddOnId(line.getId(), feeding.getId()));
        addOn.setBookingHorse(line);
        addOn.setBillableService(feeding);
        addOn.setQuantity(3);
        addOns.save(addOn);

        BookingStatusHistory history = new BookingStatusHistory();
        history.setBooking(booking);
        history.setToStatus(BookingStatus.PENDING_INTAKE);
        history.setChangedBy(customerUser);
        statusHistory.save(history);

        TermsAcceptance terms = new TermsAcceptance();
        terms.setTermsType(TermsKind.SERVICE_TERMS);
        terms.setTermsVersion("2027.1");
        terms.setUser(customerUser);
        terms.setBooking(booking);
        termsAcceptances.save(terms);

        BookingRating rating = new BookingRating();
        rating.setBooking(booking);
        rating.setScore((short) 5);
        ratings.save(rating);

        em.flush();
        em.clear();

        Booking reloaded = bookings.findByCode("TEST-MAPPING-1").orElseThrow();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getDeparture()).isNull();
        assertThat(bookingParties.findById_BookingId(reloaded.getId()))
                .singleElement().satisfies(p -> {
                    assertThat(p.getId().getBookingRole()).isEqualTo(BookingPartyRole.PAYER);
                    assertThat(p.getIdentityDocumentCountryCode()).isEqualTo("VN");
                });
        assertThat(bookingAddresses.findByBooking_Id(reloaded.getId()))
                .singleElement().satisfies(a -> {
                    assertThat(a.getProvince().getCode()).isEqualTo("VN-75");
                    assertThat(a.getSourceAddress().getLabel()).isEqualTo("Trang trại");
                });
        assertThat(customerAddresses.findByCustomer_UserIdOrderById(customerUser.getId()))
                .extracting(CustomerAddress::getLabel).contains("Trang trại");
        BookingHorse reloadedLine = bookingHorses.findByBooking_Id(reloaded.getId()).get(0);
        assertThat(reloadedLine.getAddedAt()).isNotNull();
        assertThat(reloadedLine.getFeedSuppliedByOwner()).isFalse();
        assertThat(addOns.findById_BookingHorseId(reloadedLine.getId()))
                .singleElement().satisfies(a -> assertThat(a.getBillableService().getCode()).isEqualTo("FEEDING"));
        assertThat(statusHistory.findByBooking_IdOrderByChangedAtAscIdAsc(reloaded.getId()))
                .singleElement().satisfies(h -> {
                    assertThat(h.getFromStatus()).isNull();
                    assertThat(h.getChangedAt()).isNotNull();
                });
        assertThat(ratings.findById(reloaded.getId()).orElseThrow().getRatedAt()).isNotNull();
    }
}
