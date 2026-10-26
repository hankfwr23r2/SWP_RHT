package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.DocumentRequirementRequest;
import com.example.racehorse_transport.dto.DocumentRequirementResponse;
import java.util.List;

public interface DocumentRequirementService {
    List<DocumentRequirementResponse> listAll();
    List<DocumentRequirementResponse> listActive();
    DocumentRequirementResponse create(DocumentRequirementRequest request, String actor);
    DocumentRequirementResponse update(Integer id, DocumentRequirementRequest request, String actor);
    DocumentRequirementResponse changeStatus(Integer id, String status, String actor);
}
