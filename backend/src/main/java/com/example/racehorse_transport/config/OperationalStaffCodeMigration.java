package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.StaffRepository;
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
