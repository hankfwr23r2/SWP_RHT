package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.LocalFileClient;
import com.example.racehorse_transport.config.LocalDemoProperties;
import com.example.racehorse_transport.service.DeliveryFailureService;
import com.example.racehorse_transport.service.DepartureDecisionService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.LocalDemoService;
import com.example.racehorse_transport.service.QuotationService;
import com.example.racehorse_transport.service.SettlementService;
import com.example.racehorse_transport.util.OffsetClock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.LongConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@Profile("local-demo")
@RequiredArgsConstructor
@Slf4j
public class LocalDemoServiceImpl implements LocalDemoService {

    private final OffsetClock clock;
    private final LocalDemoProperties properties;
    private final LocalFileClient files;
    private final DepartureService departures;
    private final DepartureDecisionService decisions;
    private final QuotationService quotations;
    private final SettlementService settlements;
    private final DeliveryFailureService deliveryFailures;

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public Duration offset() {
        return clock.offset();
    }

    // Tua xong chạy ngay các việc tự động, không phải chờ lịch chạy theo giờ thật
    @Override
    public void advance(Duration amount) {
        store(clock.offset().plus(amount));
        departures.generateFromSchedules();
        departures.closePastCutoff();
        each(quotations.expirationCandidates(), quotations::expire);
        decisions.releaseUnconfirmed();
        deliveryFailures.timeoutExpired();
        each(settlements.autoCloseCandidates(), settlements::autoClose);
    }

    @Override
    public void reset() {
        store(Duration.ZERO);
    }

    @Override
    public Resource file(String name) {
        return files.read(name);
    }

    private void store(Duration offset) {
        clock.setOffset(offset);
        try {
            Files.createDirectories(properties.clockFile().getParent());
            Files.writeString(properties.clockFile(), offset.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void each(List<Long> ids, LongConsumer action) {
        for (Long id : ids) {
            try {
                action.accept(id);
            } catch (RuntimeException ex) {
                log.error("Không chạy được việc tự động cho bản ghi {}", id, ex);
            }
        }
    }
}
