package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Incident;

import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.service.IncidentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class IncidentServiceImpl implements IncidentService {

    private final IncidentRepository incidentRepository;

    @Autowired
    public IncidentServiceImpl(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Override
    public List<Incident> findAll() {
        return incidentRepository.findAll();
    }

    @Override
    public Optional<Incident> findById(Integer id) {
        return incidentRepository.findById(id);
    }

    @Override
    public Incident save(Incident entity) {
        return incidentRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        incidentRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 3: Xử lý sự cố (Flow 5)
    // =========================================================================
    // TODO (Dev 3): Báo cáo và Xử lý sự cố (Đổi lộ trình khẩn cấp, tai nạn...).
    // - Viết hàm reportIncident(Integer routeId, String description, ...)
    // - Viết hàm resolveIncident(Integer incidentId)

}
