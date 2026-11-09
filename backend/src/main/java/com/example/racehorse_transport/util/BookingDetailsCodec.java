package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.booking.BookingServiceSelectionDto;
import com.example.racehorse_transport.entity.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Relational horse/service storage with the established booking-details JSON API. */
public final class BookingDetailsCodec {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> CATEGORIES = Set.of("stall", "feed", "water", "insurance");

    private BookingDetailsCodec() {}

    public static String read(Booking booking) {
        if (!booking.isDetailsNormalized()) return booking.getStoredDetailsJson();
        try {
            ObjectNode root = object(booking.getStoredDetailsJson());
            root.put("originCountry", booking.getOriginCountry());
            root.put("destCountry", booking.getDestCountry());
            root.put("gate", booking.getGateName());
            if (booking.getHorsesPresent() != null) {
                if (!booking.getHorsesPresent()) root.putNull("horses");
                else {
                    List<BookingHorseDto> horses = booking.getBookingHorses().stream()
                            .sorted(Comparator.comparing(BookingHorse::getSnapshotOrder,
                                    Comparator.nullsLast(Integer::compareTo)))
                            .map(horse -> horseDto(booking, horse)).toList();
                    root.set("horses", JSON.valueToTree(horses));
                }
            }
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw unreadable(booking, ex);
        }
    }

    public static void write(Booking booking, String value) {
        final Prepared prepared;
        try {
            prepared = prepare(value);
        } catch (JsonProcessingException | RuntimeException ex) {
            if (booking.isDetailsNormalized()) throw unreadable(booking, ex);
            // Legacy unsupported payloads remain byte-for-byte intact for explicit migration review.
            booking.setStoredDetailsJson(value);
            return;
        }
        if (prepared == null) {
            booking.setStoredDetailsJson(value);
            return;
        }

        Map<Integer, BookingHorse> currentHorses = new HashMap<>();
        for (BookingHorse horse : booking.getBookingHorses()) currentHorses.put(horse.getHorseID().getId(), horse);
        Map<String, ServiceBooking> currentServices = new HashMap<>();
        for (ServiceBooking service : booking.getServiceBookings()) currentServices.put(serviceKey(service), service);
        Set<Integer> wantedHorses = new HashSet<>();
        Set<String> wantedServices = new HashSet<>();
        int order = 0;
        for (BookingHorseDto dto : prepared.horses()) {
            Integer horseId = numericHorseId(dto.getHorseId());
            wantedHorses.add(horseId);
            BookingHorse horse = currentHorses.get(horseId);
            if (horse == null) {
                horse = new BookingHorse();
                BookingHorseId id = new BookingHorseId();
                id.setBookingID(booking.getId()); id.setHorseID(horseId);
                horse.setId(id);
                horse.setBookingID(booking);
                Horse reference = new Horse(); reference.setId(horseId); horse.setHorseID(reference);
                horse.setAddedDate(Instant.now());
                booking.getBookingHorses().add(horse);
            }
            updateHorse(horse, dto, order++);
            if (dto.getSelectedServices() != null) {
                for (var entry : dto.getSelectedServices().entrySet()) {
                    BookingServiceSelectionDto selection = entry.getValue();
                    String key = horseId + ":" + selection.getId();
                    wantedServices.add(key);
                    ServiceBooking service = currentServices.get(key);
                    if (service == null) {
                        service = new ServiceBooking();
                        ServiceBookingId id = new ServiceBookingId();
                        id.setBookingID(booking.getId()); id.setHorseID(horseId); id.setServiceID(selection.getId());
                        service.setId(id); service.setBookingID(booking); service.setHorseID(horse.getHorseID());
                        Service reference = new Service(); reference.setId(selection.getId()); service.setServiceID(reference);
                        booking.getServiceBookings().add(service);
                    }
                    updateService(service, entry.getKey(), selection);
                }
            }
        }
        booking.getServiceBookings().removeIf(service -> !wantedServices.contains(serviceKey(service)));
        booking.getBookingHorses().removeIf(horse -> !wantedHorses.contains(horse.getHorseID().getId()));
        booking.setOriginCountry(text(prepared.workflow(), "originCountry"));
        booking.setDestCountry(text(prepared.workflow(), "destCountry"));
        booking.setGateName(text(prepared.workflow(), "gate"));
        booking.setHorsesPresent(prepared.horsesPresent());
        prepared.workflow().remove(List.of("originCountry", "destCountry", "gate", "horses"));
        booking.setStoredDetailsJson(prepared.workflow().toString());
        booking.setDetailsNormalized(true);
    }

    private static Prepared prepare(String value) throws JsonProcessingException {
        if (value == null || value.isBlank()) return null;
        JsonNode parsed = JSON.readTree(value);
        ObjectNode root;
        if (parsed.isArray()) { root = JSON.createObjectNode(); root.set("horses", parsed); }
        else if (parsed.isObject()) root = (ObjectNode) parsed;
        else return null;
        for (String field : List.of("originCountry", "destCountry", "gate")) {
            if (root.hasNonNull(field) && !root.get(field).isTextual()) return null;
        }
        Boolean horsesPresent = root.has("horses") ? !root.get("horses").isNull() : null;
        List<BookingHorseDto> horses = new ArrayList<>();
        if (Boolean.TRUE.equals(horsesPresent)) {
            if (!root.get("horses").isArray()) return null;
            Set<Integer> horseIds = new HashSet<>();
            for (JsonNode horseNode : root.get("horses")) {
                BookingHorseDto horse = JSON.treeToValue(horseNode, BookingHorseDto.class);
                if (!sameValue(horseNode, JSON.valueToTree(horse))) return null;
                if (!horseIds.add(numericHorseId(horse.getHorseId()))) return null;
                // Old fixtures/partial imports without catalog-resolved snapshots remain legacy.
                if (horse.getSelectedServices() == null || horse.getSelectedServices().isEmpty()) return null;
                if (horse.getSelectedServiceCodes() != null && (!CATEGORIES.containsAll(horse.getSelectedServiceCodes().keySet())
                        || horse.getSelectedServiceCodes().values().stream().anyMatch(Objects::isNull))) return null;
                Set<Integer> serviceIds = new HashSet<>();
                for (var selection : horse.getSelectedServices().entrySet()) {
                    BookingServiceSelectionDto service = selection.getValue();
                    if (!CATEGORIES.contains(selection.getKey()) || service == null || service.getId() == null
                            || service.getId() <= 0 || !serviceIds.add(service.getId())
                            || service.getPrice() != null && service.getPrice().signum() < 0) return null;
                }
                if (horse.getInsurance() != null) InsuranceDetailsMapper.update(new BookingHorseInsurance(), horse.getInsurance());
                horses.add(horse);
            }
        }
        return new Prepared(root, horsesPresent, horses);
    }

    private static void updateHorse(BookingHorse row, BookingHorseDto horse, int order) {
        row.setSnapshotOrder(order); row.setHorseReference(horse.getHorseId()); row.setHorseName(horse.getName());
        row.setMicrochip(horse.getMicrochip()); row.setBreed(horse.getBreed()); row.setSex(horse.getSex());
        row.setStall(horse.getStall()); row.setFeedPackage(horse.getFeedPackage()); row.setWaterPlan(horse.getWaterPlan());
        row.setInsuranceOpted(horse.getInsuranceOpted()); row.setEligibilityStatus(horse.getStatus());
        row.setUnfitInfoJson(horse.getUnfitInfo() == null ? null : JSON.valueToTree(horse.getUnfitInfo()).toString());
        row.setSelectedServicesPresent(horse.getSelectedServices() != null);
        row.setSelectedServiceCodesPresent(horse.getSelectedServiceCodes() != null);
        Map<String, String> codes = horse.getSelectedServiceCodes() == null ? Map.of() : horse.getSelectedServiceCodes();
        row.setStallServiceCode(codes.get("stall")); row.setFeedServiceCode(codes.get("feed"));
        row.setWaterServiceCode(codes.get("water")); row.setInsuranceServiceCode(codes.get("insurance"));
        if (horse.getInsurance() == null) row.setInsurancePolicy(null);
        else {
            BookingHorseInsurance policy = row.getInsurancePolicy();
            if (policy == null) { policy = new BookingHorseInsurance(); policy.setBookingHorse(row); row.setInsurancePolicy(policy); }
            InsuranceDetailsMapper.update(policy, horse.getInsurance());
        }
        row.setTotalHorseServicePrice(horse.getSelectedServices() == null ? BigDecimal.ZERO
                : horse.getSelectedServices().values().stream().map(BookingServiceSelectionDto::getPrice)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static void updateService(ServiceBooking row, String category, BookingServiceSelectionDto selection) {
        row.setSelectionCategory(category); row.setSnapshotServiceCode(selection.getServiceCode());
        row.setSnapshotServiceName(selection.getServiceName()); row.setSnapshotCategoryId(selection.getCategoryId());
        row.setSnapshotCategoryName(selection.getCategoryName()); row.setSnapshotBillingUnit(selection.getBillingUnit());
        row.setSnapshotDescription(selection.getDescription()); row.setSnapshotPrice(selection.getPrice());
        row.setQuantity(1);
        row.setUnitPrice(selection.getPrice() == null ? BigDecimal.ZERO : selection.getPrice());
        row.setSubtotal(row.getUnitPrice());
    }

    private static BookingHorseDto horseDto(Booking booking, BookingHorse row) {
        Map<String, BookingServiceSelectionDto> services = null;
        if (row.isSelectedServicesPresent()) {
            services = new LinkedHashMap<>();
            for (ServiceBooking service : booking.getServiceBookings()) {
                if (Objects.equals(service.getHorseID().getId(), row.getHorseID().getId())) {
                    services.put(service.getSelectionCategory(), BookingServiceSelectionDto.builder()
                            .id(service.getServiceID().getId()).serviceCode(service.getSnapshotServiceCode())
                            .serviceName(service.getSnapshotServiceName()).categoryId(service.getSnapshotCategoryId())
                            .categoryName(service.getSnapshotCategoryName()).billingUnit(service.getSnapshotBillingUnit())
                            .description(service.getSnapshotDescription()).price(service.getSnapshotPrice()).build());
                }
            }
        }
        Map<String, String> codes = null;
        if (row.isSelectedServiceCodesPresent()) {
            codes = new LinkedHashMap<>();
            if (row.getStallServiceCode() != null) codes.put("stall", row.getStallServiceCode());
            if (row.getFeedServiceCode() != null) codes.put("feed", row.getFeedServiceCode());
            if (row.getWaterServiceCode() != null) codes.put("water", row.getWaterServiceCode());
            if (row.getInsuranceServiceCode() != null) codes.put("insurance", row.getInsuranceServiceCode());
        }
        Map<String, Object> unfit = null;
        if (row.getUnfitInfoJson() != null) {
            try { unfit = JSON.readValue(row.getUnfitInfoJson(), new TypeReference<>() {}); }
            catch (JsonProcessingException ex) { throw unreadable(booking, ex); }
        }
        return BookingHorseDto.builder().horseId(row.getHorseReference()).name(row.getHorseName())
                .microchip(row.getMicrochip()).breed(row.getBreed()).sex(row.getSex()).stall(row.getStall())
                .feedPackage(row.getFeedPackage()).waterPlan(row.getWaterPlan()).insuranceOpted(row.getInsuranceOpted())
                .insurance(InsuranceDetailsMapper.toDto(row.getInsurancePolicy())).status(row.getEligibilityStatus())
                .unfitInfo(unfit).selectedServiceCodes(codes).selectedServices(services).build();
    }

    private static ObjectNode object(String value) throws JsonProcessingException {
        if (value == null || value.isBlank()) return JSON.createObjectNode();
        JsonNode node = JSON.readTree(value);
        if (!(node instanceof ObjectNode root)) throw new IllegalArgumentException("Booking workflow must be an object");
        return root;
    }

    private static Integer numericHorseId(String value) {
        if (value == null) throw new IllegalArgumentException("Missing horse reference");
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) throw new IllegalArgumentException("Invalid horse reference");
        int id = Integer.parseInt(digits);
        if (id <= 0) throw new IllegalArgumentException("Invalid horse reference");
        return id;
    }

    /** Jackson may coerce strings/numbers; unsupported legacy shapes must never be rewritten. */
    private static boolean sameValue(JsonNode left, JsonNode right) {
        if (left == null || left.isNull()) return right == null || right.isNull();
        if (right == null || right.isNull()) return false;
        if (left.isNumber() && right.isNumber()) return left.decimalValue().compareTo(right.decimalValue()) == 0;
        if (left.isObject() && right.isObject()) {
            Set<String> keys = new HashSet<>();
            left.fieldNames().forEachRemaining(keys::add); right.fieldNames().forEachRemaining(keys::add);
            return keys.stream().allMatch(key -> sameValue(left.get(key), right.get(key)));
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++) if (!sameValue(left.get(i), right.get(i))) return false;
            return true;
        }
        return left.equals(right);
    }

    private static String serviceKey(ServiceBooking service) {
        return service.getHorseID().getId() + ":" + service.getServiceID().getId();
    }

    private static String text(ObjectNode root, String key) {
        return root.hasNonNull(key) ? root.get(key).textValue() : null;
    }

    private static IllegalStateException unreadable(Booking booking, Exception cause) {
        return new IllegalStateException("Không thể đọc chi tiết đơn " + booking.getId() + ".", cause);
    }

    private record Prepared(ObjectNode workflow, Boolean horsesPresent, List<BookingHorseDto> horses) {}
}
