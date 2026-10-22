package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class CoordinatorStaffCodeMigration implements CommandLineRunner {

    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)^DP-(\\d+)$");
    private static final Pattern COORDINATOR_CODE = Pattern.compile("(?i)^CO-(\\d+)$");

    private final StaffRepository staffRepository;

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

        List<Staff> legacyCoordinators = staffList.stream()
                .filter(staff -> "COORDINATOR".equalsIgnoreCase(staff.getRole()))
                .filter(staff -> LEGACY_CODE.matcher(staff.getStaffCode() == null ? "" : staff.getStaffCode()).matches())
                .sorted(Comparator.comparing(Staff::getId))
                .toList();

        for (Staff staff : legacyCoordinators) {
            Matcher matcher = LEGACY_CODE.matcher(staff.getStaffCode());
            if (!matcher.matches()) {
                continue;
            }

            String newCode = "CO-" + matcher.group(1);
            usedCodes.remove(normalize(staff.getStaffCode()));
            if (usedCodes.contains(normalize(newCode))) {
                newCode = nextAvailableCoordinatorCode(usedCodes);
            }
            staff.setStaffCode(newCode);
            usedCodes.add(normalize(newCode));
        }

        if (!legacyCoordinators.isEmpty()) {
            staffRepository.saveAll(legacyCoordinators);
            log.info("Migrated {} legacy coordinator staff code(s) from DP to CO.", legacyCoordinators.size());
        }
    }

    private String nextAvailableCoordinatorCode(Set<String> usedCodes) {
        int nextNumber = usedCodes.stream()
                .map(COORDINATOR_CODE::matcher)
                .filter(Matcher::matches)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElse(0) + 1;

        String candidate;
        do {
            candidate = "CO-" + String.format("%02d", nextNumber++);
        } while (usedCodes.contains(normalize(candidate)));
        return candidate;
    }

    private String normalize(String code) {
        return code.toUpperCase(Locale.ROOT);
    }
}
