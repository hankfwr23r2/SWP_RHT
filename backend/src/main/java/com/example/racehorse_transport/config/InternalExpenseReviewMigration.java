package com.example.racehorse_transport.config;

import com.example.racehorse_transport.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Startup delegates the idempotent legacy transition to the transactional service layer. */
@Component
@Order(50)
@RequiredArgsConstructor
public class InternalExpenseReviewMigration implements CommandLineRunner {
    private final SettlementService settlementService;

    @Override
    public void run(String... args) {
        settlementService.migrateLegacySettlements();
    }
}
