package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.client.ValhallaClient;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.exception.RoutingUnavailableException;
import com.example.racehorse_transport.service.PricingRouteService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class PricingRouteServiceImpl implements PricingRouteService {
    private final ValhallaClient routing;
    private final EntityManagerFactory entityManagerFactory;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public BigDecimal distance(RoadSegment segment, LocalDateTime time, int weightTons, int topSpeedKmh) {
        return route(segment, time, weightTons, topSpeedKmh).km();
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public RoadEstimate route(RoadSegment segment, LocalDateTime time, int weightTons, int topSpeedKmh) {
        if (segment.from().equals(segment.to())) return new RoadEstimate(BigDecimal.ZERO.setScale(2), 0);
        if (weightTons <= 0 || topSpeedKmh <= 0) {
            throw new RoutingUnavailableException();
        }
        releaseRequestConnection();
        var route = routing.truckRoute(List.of(segment.from(), segment.to()), weightTons, topSpeedKmh, time);
        double km = route.km();
        if (!Double.isFinite(km) || km <= 0) {
            throw new RoutingUnavailableException();
        }
        // Km tính tiền: km gần nhất, tối thiểu 1 km
        return new RoadEstimate(BigDecimal.valueOf(Math.max(1, Math.round(km))).setScale(2), route.driveSeconds());
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public List<RoadRouteDto.Leg> legs(List<GeoPointDto> points, LocalDateTime time, int weightTons, int topSpeedKmh) {
        releaseRequestConnection();
        return routing.truckRoute(points, weightTons, topSpeedKmh, time).legs();
    }

    private void releaseRequestConnection() {
        // With open-in-view, Spring's Hibernate adapter can hold the connection until the request closes.
        // Return it to the pool now. The managed session can acquire another connection for calculation.
        if (TransactionSynchronizationManager.getResource(entityManagerFactory) instanceof EntityManagerHolder holder) {
            var session = holder.getEntityManager().unwrap(SessionImplementor.class);
            var connection = session.getJdbcCoordinator().getLogicalConnection();
            if (connection.isPhysicallyConnected()) {
                connection.manualDisconnect();
            }
        }
    }
}
