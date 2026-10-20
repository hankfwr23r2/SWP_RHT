package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseDocument;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.HorseDocumentRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseJourneyLogRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.ServiceBookingRepository;
import com.example.racehorse_transport.service.HorseService;
import com.example.racehorse_transport.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class HorseServiceImpl implements HorseService {

    private final HorseRepository horseRepository;
    private final BookingHorseRepository bookingHorseRepository;
    private final ServiceBookingRepository serviceBookingRepository;
    private final HorseJourneyLogRepository horseJourneyLogRepository;
    private final HorseHealthLogRepository horseHealthLogRepository;
    private final HorseDocumentRepository horseDocumentRepository;
    private final StorageService storageService;

    @Autowired
    public HorseServiceImpl(
            HorseRepository horseRepository,
            BookingHorseRepository bookingHorseRepository,
            ServiceBookingRepository serviceBookingRepository,
            HorseJourneyLogRepository horseJourneyLogRepository,
            HorseHealthLogRepository horseHealthLogRepository,
            HorseDocumentRepository horseDocumentRepository,
            StorageService storageService) {
        this.horseRepository = horseRepository;
        this.bookingHorseRepository = bookingHorseRepository;
        this.serviceBookingRepository = serviceBookingRepository;
        this.horseJourneyLogRepository = horseJourneyLogRepository;
        this.horseHealthLogRepository = horseHealthLogRepository;
        this.horseDocumentRepository = horseDocumentRepository;
        this.storageService = storageService;
    }

    @Override
    public List<Horse> findAll() {
        return horseRepository.findAll();
    }

    @Override
    public Optional<Horse> findById(Integer id) {
        return horseRepository.findById(id);
    }

    @Override
    public Horse save(Horse entity) {
        return horseRepository.save(entity);
    }

    @Override
    @Transactional
    public void deleteById(Integer id) {
        Horse horse = horseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ ngựa."));
        if (hasHistory(horse, id)) {
            throw new IllegalArgumentException("Không thể xóa hồ sơ ngựa đã được sử dụng trong đơn vận chuyển hoặc có lịch sử chăm sóc, hành trình.");
        }

        List<HorseDocument> documents = horseDocumentRepository.findByHorseID_Id(id);
        documents.stream()
                .map(HorseDocument::getFileUrl)
                .filter(url -> url != null && !url.isBlank())
                .forEach(storageService::deleteFile);
        horseDocumentRepository.deleteAll(documents);
        horseRepository.delete(horse);
    }

    private boolean hasHistory(Horse horse, Integer id) {
        return (horse.getCompletedTrips() != null && horse.getCompletedTrips() > 0)
                || bookingHorseRepository.existsByHorseID_Id(id)
                || serviceBookingRepository.existsByHorseID_Id(id)
                || horseJourneyLogRepository.existsByHorse_Id(id)
                || horseHealthLogRepository.existsByHorseID_Id(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 1 (Lead): Core (Flow 1)
    // =========================================================================
    // TODO (Dev 1): CRUD Ngựa.
    // - (Đã có sẵn CRUD cơ bản, bổ sung logic kiểm tra microchip trùng lặp...)

}
