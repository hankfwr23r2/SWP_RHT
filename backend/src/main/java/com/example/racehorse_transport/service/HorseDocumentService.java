package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.horse.HorseDocumentResponseDto;
import com.example.racehorse_transport.dto.horse.HorseDocumentUploadRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeResponseDto;
import java.util.List;
import java.util.Map;

public interface HorseDocumentService {
    List<DocumentTypeResponseDto> documentTypes();

    List<HorseDocumentResponseDto> documents(String username, Long horseId);

    HorseDocumentResponseDto upload(String username, Long horseId, HorseDocumentUploadRequestDto request);

    // Gọi nội bộ với các hồ sơ ngựa đã kiểm quyền.
    Map<Long, List<HorseDocumentResponseDto>> documentsFor(List<Long> horseIds);
}
