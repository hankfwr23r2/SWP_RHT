package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.HorseDocument;

import com.example.racehorse_transport.repository.HorseDocumentRepository;
import com.example.racehorse_transport.service.HorseDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class HorseDocumentServiceImpl implements HorseDocumentService {

    private final HorseDocumentRepository horseDocumentRepository;

    @Autowired
    public HorseDocumentServiceImpl(HorseDocumentRepository horseDocumentRepository) {
        this.horseDocumentRepository = horseDocumentRepository;
    }

    @Override
    public List<HorseDocument> findAll() {
        return horseDocumentRepository.findAll();
    }

    @Override
    public Optional<HorseDocument> findById(Integer id) {
        return horseDocumentRepository.findById(id);
    }

    @Override
    public HorseDocument save(HorseDocument entity) {
        return horseDocumentRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        horseDocumentRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 2: Hồ sơ (Flow 2)
    // =========================================================================
    // TODO (Dev 2): Upload hồ sơ (tích hợp Cloud/Local).
    // - Viết hàm uploadHorseDocument(Integer horseId, MultipartFile file)
    
    // TODO (Dev 2): Chuyên viên duyệt/từ chối hồ sơ.
    // - Viết hàm reviewDocument(Integer documentId, boolean isApproved, String note)

}
