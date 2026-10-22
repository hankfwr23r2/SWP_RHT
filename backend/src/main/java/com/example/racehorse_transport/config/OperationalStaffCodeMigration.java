package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingAssignment;
import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(22)
@RequiredArgsConstructor
public class OperationalStaffCodeMigration implements CommandLineRunner {

    private static final List<CodeRule> RULES = List.of(
            new CodeRule("ADMIN", "ADMIN", Pattern.compile("(?i)^(?:AD|ADM)-(\\d+)$")),
            new CodeRule("DRIVER", "DR", Pattern.compile("(?i)^TX-(\\d+)$")),
            new CodeRule("ESCORT", "ES", Pattern.compile("(?i)^(?:NV|HT)-(\\d+)$")));

    private final StaffRepository staffRepository;
    private final BookingRepository bookingRepository;
    private final BookingAssignmentRepository bookingAssignmentRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        List<Staff> staffList = staffRepository.findAll();
        Set<String> usedCodes = new HashSet<>();
        for (Staff staff : staffList) {
            if (staff.getStaffCode() != null) {
                usedCodes.add(normalize(staff.getStaffCode()));
            }
        }

        List<Staff> changedStaff = new ArrayList<>();
        for (CodeRule rule : RULES) {
            List<Staff> legacyStaff = staffList.stream()
                    .filter(staff -> rule.role().equalsIgnoreCase(staff.getRole()))
                    .filter(staff -> rule.legacyCode().matcher(staff.getStaffCode() == null ? "" : staff.getStaffCode()).matches())
                    .sorted(Comparator.comparing(Staff::getId))
                    .toList();

            for (Staff staff : legacyStaff) {
                Matcher matcher = rule.legacyCode().matcher(staff.getStaffCode());
                if (!matcher.matches()) {
                    continue;
                }

                String suffix = padMinimumTwo(matcher.group(1));
                String newCode = rule.prefix() + "-" + suffix;
                if (usedCodes.contains(normalize(newCode))) {
                    newCode = nextAvailableCode(rule.prefix(), usedCodes);
                }

                staff.setStaffCode(newCode);
                usedCodes.add(normalize(newCode));
                changedStaff.add(staff);
            }
        }

        if (!changedStaff.isEmpty()) {
            staffRepository.saveAll(changedStaff);
            log.info("Migrated {} legacy admin, driver, or escort staff code(s).", changedStaff.size());
        }

        migrateTripStaffCodes();
    }

    private void migrateTripStaffCodes() {
        List<Booking> changedBookings = new ArrayList<>();
        for (Booking booking : bookingRepository.findAll()) {
            if (booking.getDetailsJson() == null || booking.getDetailsJson().isBlank()) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(booking.getDetailsJson());
                JsonNode trips = root.path("trips");
                if (!trips.isArray()) {
                    continue;
                }

                List<BookingAssignment> assignments = bookingAssignmentRepository.findByBookingID_Id(booking.getId());
                boolean changed = false;
                for (JsonNode tripNode : trips) {
                    if (!(tripNode instanceof ObjectNode trip)) {
                        continue;
                    }
                    String tripId = trip.path("tripId").asText("");
                    changed |= migrateTripIdentifier(trip, "driverId", "DRIVER", tripId, assignments);
                    changed |= migrateTripIdentifier(trip, "escortId", "ESCORT", tripId, assignments);
                }
                if (changed) {
                    booking.setDetailsJson(objectMapper.writeValueAsString(root));
                    changedBookings.add(booking);
                }
            } catch (Exception ex) {
                log.warn("Could not migrate staff codes in booking {} detailsJson: {}", booking.getId(), ex.getMessage());
            }
        }
        if (!changedBookings.isEmpty()) {
            bookingRepository.saveAll(changedBookings);
            log.info("Migrated trip staff codes in {} booking(s).", changedBookings.size());
        }
    }

    private boolean migrateTripIdentifier(ObjectNode trip, String field, String role, String tripId,
            List<BookingAssignment> assignments) {
        String currentValue = trip.path(field).asText("");
        if (currentValue.isBlank()) {
            return false;
        }

        String assignedCode = tripId.isBlank() ? null : assignments.stream()
                .filter(assignment -> assignment.getStaffID() != null)
                .filter(assignment -> role.equalsIgnoreCase(assignment.getAssignRole()))
                .filter(assignment -> assignment.getNote() != null && assignment.getNote().contains(tripId))
                .map(assignment -> assignment.getStaffID().getStaffCode())
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(null);

        String normalized = currentValue.trim().toUpperCase(Locale.ROOT);
        String replacement = assignedCode;
        if (replacement == null) {
            if ("DRIVER".equals(role) && normalized.matches("^TX-\\d+$")) {
                replacement = "DR-" + normalized.substring(3);
            } else if ("ESCORT".equals(role) && normalized.matches("^(NV|HT)-\\d+$")) {
                replacement = "ES-" + normalized.substring(normalized.indexOf('-') + 1);
            }
        }
        if (replacement == null || replacement.equals(currentValue)) {
            return false;
        }
        trip.put(field, replacement);
        return true;
    }

    private String nextAvailableCode(String prefix, Set<String> usedCodes) {
        Pattern targetPattern = Pattern.compile("(?i)^" + Pattern.quote(prefix) + "-(\\d+)$");
        BigInteger nextNumber = usedCodes.stream()
                .map(targetPattern::matcher)
                .filter(Matcher::matches)
                .map(matcher -> new BigInteger(matcher.group(1)))
                .max(BigInteger::compareTo)
                .orElse(BigInteger.ZERO)
                .add(BigInteger.ONE);

        String candidate;
        do {
            candidate = prefix + "-" + padMinimumTwo(nextNumber.toString());
            nextNumber = nextNumber.add(BigInteger.ONE);
        } while (usedCodes.contains(normalize(candidate)));
        return candidate;
    }

    private String padMinimumTwo(String suffix) {
        return suffix.length() < 2 ? "0" + suffix : suffix;
    }

    private String normalize(String code) {
        return code.toUpperCase(Locale.ROOT);
    }

    private record CodeRule(String role, String prefix, Pattern legacyCode) {
    }
}
