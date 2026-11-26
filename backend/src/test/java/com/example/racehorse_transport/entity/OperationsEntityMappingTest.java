package com.example.racehorse_transport.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.HandoverDocumentDirection;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentTransactionStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuarantineResult;
import com.example.racehorse_transport.entity.enums.QuotationLineType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.AuditLogRepository;
import com.example.racehorse_transport.repository.BookingAddressRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CostItemRepository;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.DocumentTypeRepository;
import com.example.racehorse_transport.repository.HandoverDocumentRepository;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.repository.IncidentPlanRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.NotificationRecipientRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.PaymentTransactionRepository;
import com.example.racehorse_transport.repository.QuotationLineRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.repository.RouteStopRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// Entity nhóm 9–15 map đúng bảng: đọc được báo giá, thanh toán, bảo hiểm do seed tạo, và ghi rồi đọc lại được trọn một chuỗi vận hành.
// ddl-auto=validate đã kiểm tên cột và kiểu; test này kiểm thêm khóa ghép, FK ghép tới route_stop, cột jsonb và cột do DB sinh giá trị.
@PostgresIntegrationTest
@Transactional
class OperationsEntityMappingTest {

    private static final Instant START = Instant.parse("2027-01-10T01:00:00Z");

    @Autowired private EntityManager em;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingHorseRepository bookingHorses;
    @Autowired private BookingAddressRepository bookingAddresses;
    @Autowired private StaffRepository staff;
    @Autowired private AppUserRepository users;
    @Autowired private VehicleRepository vehicles;
    @Autowired private LocationRepository locations;
    @Autowired private RouteStopRepository routeStops;
    @Autowired private PartyRepository parties;
    @Autowired private DocumentTypeRepository documentTypes;
    @Autowired private VehicleTripRepository vehicleTrips;
    @Autowired private TripStopRepository tripStops;
    @Autowired private CrewAssignmentRepository crew;
    @Autowired private HorseLegRepository horseLegs;
    @Autowired private HorseStayRepository horseStays;
    @Autowired private HorseHealthLogRepository healthLogs;
    @Autowired private HandoverRepository handovers;
    @Autowired private HandoverHorseRepository handoverHorses;
    @Autowired private HandoverDocumentRepository handoverDocuments;
    @Autowired private DocumentRepository documents;
    @Autowired private HorseInsuranceRepository insurances;
    @Autowired private QuotationRepository quotations;
    @Autowired private QuotationLineRepository quotationLines;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentTransactionRepository paymentTransactions;
    @Autowired private IncidentRepository incidents;
    @Autowired private IncidentPlanRepository incidentPlans;
    @Autowired private CostItemRepository costItems;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationRecipientRepository recipients;
    @Autowired private AuditLogRepository auditLogs;

    @Test
    void seededQuotationPaymentsAndInsuranceReadThroughNewEntities() {
        Booking booking = bookings.findByCode("DEV-DOMESTIC-001").orElseThrow();

        Quotation quotation = quotations.findByBooking_IdOrderByVersion(booking.getId()).get(0);
        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.ACCEPTED);
        assertThat(quotation.getSentBy().getStaffCode()).isEqualTo("MGR-001");
        assertThat(quotation.getDepositRate()).isEqualByComparingTo("0.3");
        assertThat(quotation.getPriceList().getName()).isEqualTo("Bảng giá mẫu (dev)");

        List<QuotationLine> lines = quotationLines.findByQuotation_IdOrderByLineNumber(quotation.getId());
        assertThat(lines).isNotEmpty();
        assertThat(lines).allSatisfy(line -> assertThat(line.getAmount())
                .isEqualByComparingTo(line.getQuantity().multiply(line.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)));
        assertThat(lines.stream().map(QuotationLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(quotation.getTotalAmount());

        List<Payment> paid = payments.findByBooking_IdOrderById(booking.getId());
        assertThat(paid).extracting(Payment::getPaymentType).containsExactly(PaymentType.DEPOSIT, PaymentType.BALANCE);
        assertThat(paid).allSatisfy(p -> {
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(p.getPaidAt()).isNotNull();
            assertThat(p.getQuotation().getId()).isEqualTo(quotation.getId());
        });
        assertThat(paid.get(0).getAmount()).isEqualByComparingTo(quotation.getDepositAmount());
        assertThat(paid.get(0).getAmount().add(paid.get(1).getAmount())).isEqualByComparingTo(quotation.getTotalAmount());

        List<BookingHorse> horses = bookingHorses.findByBooking_Id(booking.getId());
        assertThat(horses).hasSize(2).allSatisfy(line -> {
            HorseInsurance insurance = insurances.findByBookingHorse_Id(line.getId()).orElseThrow();
            assertThat(insurance.getCoverageOption()).isEqualTo(InsuranceCoverageOption.PERSONAL);
            assertThat(insurance.getStatus()).isEqualTo(HorseInsuranceStatus.CONFIRMED);
            assertThat(insurance.getCurrencyCode()).isEqualTo("VND");
            assertThat(insurance.getConfirmedBy().getStaffCode()).isEqualTo("SPC-001");
            assertThat(insurance.getValidTo()).isAfter(insurance.getValidFrom());
        });
    }

    @Test
    void newQuotationLineGetsAmountComputedByDatabase() {
        Booking booking = bookings.findByCode("DEV-DOMESTIC-001").orElseThrow();
        Quotation accepted = quotations.findByBooking_IdOrderByVersion(booking.getId()).get(0);
        QuotationLine template = quotationLines.findByQuotation_IdOrderByLineNumber(accepted.getId()).get(0);

        Quotation draft = new Quotation();
        draft.setBooking(booking);
        draft.setVersion(2);
        draft.setPriceList(accepted.getPriceList());
        draft.setCurrencyCode("VND");
        draft.setDepositRate(accepted.getDepositRate());
        draft.setExpiresAt(START);
        draft.setConfirmedPickupDate(accepted.getConfirmedPickupDate());
        draft.setConfirmedDepartureDate(accepted.getConfirmedDepartureDate());
        draft.setEstimatedDeliveryDate(accepted.getEstimatedDeliveryDate());
        draft.setTotalAmount(BigDecimal.ZERO);
        draft.setPassThroughAmount(BigDecimal.ZERO);
        draft.setDepositAmount(BigDecimal.ZERO);
        draft.setStatus(QuotationStatus.DRAFT);
        draft = quotations.save(draft);

        QuotationLine line = new QuotationLine();
        line.setQuotation(draft);
        line.setLineNumber(1);
        line.setLineType(QuotationLineType.SERVICE);
        line.setBillableService(template.getBillableService());
        line.setDescription("Dòng thử");
        line.setQuantity(new BigDecimal("2.50"));
        line.setUnit("KM");
        line.setUnitPrice(new BigDecimal("100000.40"));
        line.setPassThrough(false);
        quotationLines.saveAndFlush(line);

        assertThat(line.getAmount()).isEqualByComparingTo("250001.00");
        em.clear();
        assertThat(quotationLines.findByQuotation_IdOrderByLineNumber(draft.getId()))
                .singleElement().satisfies(l -> assertThat(l.getAmount()).isEqualByComparingTo("250001.00"));
    }

    @Test
    void operationsChainRoundTrip() {
        Booking booking = bookings.findByCode("DEV-DOMESTIC-001").orElseThrow();
        BookingHorse bookingHorse = bookingHorses.findByBooking_Id(booking.getId()).get(0);
        BookingAddress delivery = bookingAddresses.findByBooking_Id(booking.getId()).stream()
                .filter(a -> a.getAddressType() == AddressRole.DELIVERY).findFirst().orElseThrow();
        Staff driver = staff.findByStaffCode("DRV-001").orElseThrow();
        Staff manager = staff.findByStaffCode("MGR-001").orElseThrow();
        Location hcm = locations.findByCode("HUB-HCM").orElseThrow();
        Location danang = locations.findByCode("HUB-DN").orElseThrow();
        Party agent = parties.findAll().stream().filter(p -> p.getDisplayName().startsWith("Đại lý")).findFirst().orElseThrow();
        List<RouteStop> routePlan = routeStops.findById_RouteIdOrderById_SequenceNumber(booking.getDeparture().getRoute().getId());

        // Lượt xe trục chính và điểm dừng: điểm chép từ tuyến, và điểm là địa chỉ của khách
        VehicleTrip trip = new VehicleTrip();
        trip.setCode("TEST-VT-0001");
        trip.setTripType(VehicleTripType.LINEHAUL);
        trip.setDeparture(booking.getDeparture());
        trip.setVehicle(vehicles.findAll().get(0));
        trip.setStartLocation(hcm);
        trip.setEndLocation(danang);
        trip.setPlannedStartAt(START);
        trip.setPlannedEndAt(START.plusSeconds(20 * 3600));
        trip.setStatus(VehicleTripStatus.PLANNED);
        trip = vehicleTrips.save(trip);

        TripStop origin = stop(trip, 1, TripStopType.HUB, START);
        origin.setLocation(routePlan.get(0).getLocation());
        origin.setRouteStop(routePlan.get(0));
        origin = tripStops.save(origin);
        TripStop customerStop = stop(trip, 2, TripStopType.DELIVERY, START.plusSeconds(21 * 3600));
        customerStop.setBookingAddress(delivery);
        customerStop = tripStops.save(customerStop);

        CrewAssignment assignment = new CrewAssignment();
        assignment.setVehicleTrip(trip);
        assignment.setStaff(driver);
        assignment.setCrewRole(CrewRole.DRIVER);
        assignment.setStatus(CrewAssignmentStatus.ASSIGNED);
        crew.save(assignment);

        // Chặng của con ngựa: trục do xe công ty chở, giao do đại lý, rồi giao lại thay chặng thất bại
        HorseLeg linehaul = leg(bookingHorse, 1, HorseLegType.LINEHAUL, HorseLegStatus.COMPLETED);
        linehaul.setFromLocation(hcm);
        linehaul.setToLocation(danang);
        linehaul.setVehicleTrip(trip);
        linehaul.setBoardTripStop(origin);
        linehaul = horseLegs.save(linehaul);
        HorseLeg failedDelivery = leg(bookingHorse, 2, HorseLegType.DELIVERY, HorseLegStatus.FAILED);
        failedDelivery.setFromLocation(danang);
        failedDelivery.setToBookingAddress(delivery);
        failedDelivery.setVehicleTrip(trip);
        failedDelivery = horseLegs.save(failedDelivery);
        HorseLeg redelivery = leg(bookingHorse, 3, HorseLegType.DELIVERY, HorseLegStatus.PLANNED);
        redelivery.setFromLocation(danang);
        redelivery.setToBookingAddress(delivery);
        redelivery.setVehicleTrip(trip);
        redelivery.setReplacedHorseLeg(failedDelivery);
        horseLegs.save(redelivery);

        HorseStay quarantine = new HorseStay();
        quarantine.setBookingHorse(bookingHorse);
        quarantine.setStayType(HorseStayType.QUARANTINE);
        quarantine.setLocation(danang);
        quarantine.setPlannedStartAt(START.plusSeconds(24 * 3600));
        quarantine.setPlannedEndAt(START.plusSeconds(72 * 3600));
        quarantine.setQuarantineResult(QuarantineResult.PASSED);
        quarantine = horseStays.save(quarantine);

        HorseHealthLog health = new HorseHealthLog();
        health.setBookingHorse(bookingHorse);
        health.setRecordedBy(driver);
        health.setRecordedAt(START);
        health.setTripStop(origin);
        health.setHealthStatus(HealthStatus.NORMAL);
        health.setTemperatureCelsius(new BigDecimal("37.8"));
        health.setHeartRateBpm((short) 36);
        health.setRespirationRatePerMinute((short) 12);
        health.setWeightKg(new BigDecimal("480.5"));
        healthLogs.save(health);

        // Giao nhận tại điểm dừng, kèm ngựa được giao và giấy tờ gốc
        Handover handover = new Handover();
        handover.setHandoverType(HandoverType.HUB_OUT);
        handover.setTripStop(origin);
        handover.setHandledBy(driver);
        handover.setHandedAt(START);
        handover.setStatus(HandoverStatus.DONE);
        handover = handovers.save(handover);

        HandoverHorse handed = new HandoverHorse();
        handed.setId(new HandoverHorseId(handover.getId(), linehaul.getId()));
        handed.setHandover(handover);
        handed.setHorseLeg(linehaul);
        handed.setScannedMicrochip(bookingHorse.getHorse().getMicrochip());
        handed.setMicrochipMatched(true);
        handoverHorses.save(handed);

        Document record = new Document();
        record.setDocumentType(documentTypes.findByCode("HANDOVER_RECORD").orElseThrow());
        record.setFileUrl("https://files.test/handover-0001.pdf");
        record.setFileName("handover-0001.pdf");
        record.setUploadedBy(driver.getUser());
        record.setStatus(DocumentStatus.UPLOADED);
        record.setHandover(handover);
        record = documents.save(record);

        HandoverDocument original = new HandoverDocument();
        original.setId(new HandoverDocumentId(handover.getId(), record.getId()));
        original.setHandover(handover);
        original.setDocument(record);
        original.setDirection(HandoverDocumentDirection.RECEIVED);
        handoverDocuments.save(original);

        // Sự cố, phương án và chi phí của lượt xe
        Incident incident = new Incident();
        incident.setVehicleTrip(trip);
        incident.setIncidentType(IncidentType.DELAY);
        incident.setSeverity(IncidentSeverity.LOW);
        incident.setCauseType(IncidentCauseType.COMPANY_FAULT);
        incident.setTitle("Kẹt xe trên quốc lộ");
        incident.setDescription("Chậm 2 giờ");
        incident.setReportedBy(driver);
        incident.setStatus(IncidentStatus.REPORTED);
        incident = incidents.save(incident);

        IncidentPlan plan = new IncidentPlan();
        plan.setIncident(incident);
        plan.setVersion(1);
        plan.setResolutionType(IncidentResolutionType.CONTINUE);
        plan.setInstructions("Đi tiếp, báo khách giờ đến mới");
        plan.setStatus(IncidentPlanStatus.PROPOSED);
        plan.setProposedBy(driver);
        incidentPlans.save(plan);

        CostItem toll = new CostItem();
        toll.setCategory(CostCategory.TOLL);
        toll.setDescription("Phí cầu đường");
        toll.setAmount(new BigDecimal("150000"));
        toll.setCurrencyCode("VND");
        toll.setPayerType(CostPayerType.COMPANY);
        toll.setVehicleTrip(trip);
        toll.setIncident(incident);
        toll.setIncurredAt(START);
        toll.setRecordedBy(driver);
        costItems.save(toll);

        // Thông báo, nhật ký kiểm toán và giao dịch cổng thanh toán (hai cột jsonb)
        AppUser customerUser = users.findByUsername("customer1.dev").orElseThrow();
        Notification notice = new Notification();
        notice.setBooking(booking);
        notice.setTitle("Xe đã xuất phát");
        notice.setMessage("Ngựa của bạn đang trên đường tới Đà Nẵng");
        notice = notifications.save(notice);
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(new NotificationRecipientId(notice.getId(), customerUser.getId()));
        recipient.setNotification(notice);
        recipient.setUser(customerUser);
        recipients.save(recipient);

        AuditLog audit = new AuditLog();
        audit.setActorUser(manager.getUser());
        audit.setAction("TEST_MAPPING");
        audit.setTargetTable("vehicle_trip");
        audit.setTargetId(trip.getId());
        audit.setNewData("{\"status\": \"PLANNED\"}");
        auditLogs.save(audit);

        Payment deposit = payments.findByBooking_IdOrderById(booking.getId()).get(0);
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(deposit);
        transaction.setGatewayCode("VNPAY");
        transaction.setGatewayTransactionNumber("TEST-TXN-0001");
        transaction.setResponseCode("00");
        transaction.setAmount(deposit.getAmount());
        transaction.setStatus(PaymentTransactionStatus.SUCCESS);
        transaction.setRawPayload("{\"vnp_ResponseCode\": \"00\"}");
        paymentTransactions.save(transaction);

        em.flush();
        em.clear();

        Long tripId = trip.getId();
        Long incidentId = incident.getId();
        assertThat(tripStops.findByVehicleTrip_IdOrderBySequenceNumber(tripId)).satisfiesExactly(
                first -> {
                    assertThat(first.getRouteStop().getId().getSequenceNumber()).isEqualTo(1);
                    assertThat(first.getLocation().getCode()).isEqualTo("HUB-HCM");
                    assertThat(first.getBookingAddress()).isNull();
                },
                second -> {
                    assertThat(second.getRouteStop()).isNull();
                    assertThat(second.getLocation()).isNull();
                    assertThat(second.getBookingAddress().getAddressType()).isEqualTo(AddressRole.DELIVERY);
                });
        assertThat(crew.findAll()).filteredOn(c -> c.getVehicleTrip().getId().equals(tripId))
                .singleElement().satisfies(c -> assertThat(c.getAssignedAt()).isNotNull());
        assertThat(horseLegs.findByBookingHorse_IdOrderBySequenceNumber(bookingHorse.getId())).satisfiesExactly(
                first -> assertThat(first.getBoardTripStop().getSequenceNumber()).isEqualTo(1),
                second -> assertThat(second.getVehicleTrip().getId()).isEqualTo(tripId),
                third -> assertThat(third.getReplacedHorseLeg().getStatus()).isEqualTo(HorseLegStatus.FAILED));
        assertThat(horseStays.findById(quarantine.getId()).orElseThrow().getQuarantineResult()).isEqualTo(QuarantineResult.PASSED);
        assertThat(healthLogs.findAll()).filteredOn(h -> h.getTripStop() != null && h.getTripStop().getVehicleTrip().getId().equals(tripId))
                .singleElement().satisfies(h -> {
                    assertThat(h.getTemperatureCelsius()).isEqualByComparingTo("37.8");
                    assertThat(h.getHeartRateBpm()).isEqualTo((short) 36);
                });
        assertThat(handoverHorses.findById_HandoverId(handover.getId())).singleElement().satisfies(h -> {
            assertThat(h.getMicrochipMatched()).isTrue();
            assertThat(h.getHorseLeg().getLegType()).isEqualTo(HorseLegType.LINEHAUL);
        });
        assertThat(handoverDocuments.findById(new HandoverDocumentId(handover.getId(), record.getId())).orElseThrow()
                .getDirection()).isEqualTo(HandoverDocumentDirection.RECEIVED);
        assertThat(documents.findById(record.getId()).orElseThrow().getUploadedAt()).isNotNull();
        assertThat(incidents.findById(incidentId).orElseThrow().getReportedAt()).isNotNull();
        assertThat(incidents.findById(incidentId).orElseThrow().getSeverity()).isEqualTo(IncidentSeverity.LOW);
        assertThat(incidentPlans.findAll()).filteredOn(p -> p.getIncident().getId().equals(incidentId))
                .singleElement().satisfies(p -> assertThat(p.getProposedAt()).isNotNull());
        assertThat(costItems.findAll()).filteredOn(c -> c.getIncident() != null && c.getIncident().getId().equals(incidentId))
                .singleElement().satisfies(c -> assertThat(c.getCustomerConsulted()).isFalse());
        assertThat(recipients.findById_UserIdAndReadAtIsNull(customerUser.getId()))
                .anySatisfy(r -> assertThat(r.getNotification().getCreatedAt()).isNotNull());
        assertThat(auditLogs.findById(audit.getId()).orElseThrow()).satisfies(a -> {
            assertThat(a.getCreatedAt()).isNotNull();
            assertThat(a.getNewData()).contains("PLANNED");
        });
        assertThat(paymentTransactions.findById(transaction.getId()).orElseThrow()).satisfies(t -> {
            assertThat(t.getReceivedAt()).isNotNull();
            assertThat(t.getRawPayload()).contains("vnp_ResponseCode");
        });
    }

    private static TripStop stop(VehicleTrip trip, int sequence, TripStopType type, Instant arrival) {
        TripStop stop = new TripStop();
        stop.setVehicleTrip(trip);
        stop.setSequenceNumber(sequence);
        stop.setStopType(type);
        stop.setPlannedArrivalAt(arrival);
        stop.setPlannedDepartureAt(arrival.plusSeconds(1800));
        return stop;
    }

    private static HorseLeg leg(BookingHorse bookingHorse, int sequence, HorseLegType type, HorseLegStatus status) {
        HorseLeg leg = new HorseLeg();
        leg.setBookingHorse(bookingHorse);
        leg.setSequenceNumber(sequence);
        leg.setLegType(type);
        leg.setStatus(status);
        leg.setPlannedStartAt(START.plusSeconds(sequence * 24L * 3600));
        leg.setPlannedEndAt(START.plusSeconds(sequence * 24L * 3600 + 20 * 3600));
        return leg;
    }
}
