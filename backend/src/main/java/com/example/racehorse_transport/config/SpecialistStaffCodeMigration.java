package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Order(21)
@RequiredArgsConstructor
public class SpecialistStaffCodeMigration implements CommandLineRunner {

    private final StaffRepository staffRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<Staff> staffList = staffRepository.findAll();
        List<Staff> legacySpecialists = staffList.stream()
                .filter(staff -> "SPECIALIST".equalsIgnoreCase(staff.getRole()))
                .filter(staff -> "KD-02".equalsIgnoreCase(staff.getStaffCode()))
                .toList();

        if (legacySpecialists.isEmpty()) {
            return;
        }
        if (legacySpecialists.size() > 1) {
            throw new IllegalStateException("Cannot migrate multiple Specialist accounts from KD-02 to SP-02.");
        }

        Staff specialist = legacySpecialists.get(0);
        boolean targetCodeInUse = staffList.stream()
                .anyMatch(staff -> !staff.getId().equals(specialist.getId())
                        && "SP-02".equalsIgnoreCase(staff.getStaffCode()));
        if (targetCodeInUse) {
            throw new IllegalStateException("Cannot migrate Specialist staff code KD-02 to SP-02 because SP-02 is already in use.");
        }

        specialist.setStaffCode("SP-02");
        staffRepository.save(specialist);
        log.info("Migrated Specialist staff code KD-02 to SP-02.");
    }
}
