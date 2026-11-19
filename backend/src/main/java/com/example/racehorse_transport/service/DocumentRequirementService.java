package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.DocumentRequirementRequestDto;
import com.example.racehorse_transport.dto.DocumentRequirementResponseDto;
import java.util.List;

public interface DocumentRequirementService {
    List<DocumentRequirementResponseDto> listAll();
    List<DocumentRequirementResponseDto> listActive();
    DocumentRequirementResponseDto create(DocumentRequirementRequestDto request, String actor);
    DocumentRequirementResponseDto update(Integer id, DocumentRequirementRequestDto request, String actor);
    DocumentRequirementResponseDto changeStatus(Integer id, String status, String actor);
}
