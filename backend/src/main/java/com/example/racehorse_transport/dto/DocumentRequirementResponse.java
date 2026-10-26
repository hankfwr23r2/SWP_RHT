package com.example.racehorse_transport.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocumentRequirementResponse {
    Integer id;
    String documentGroup;
    String documentType;
    String documentName;
    String description;
    String tripType;
    String country;
    boolean required;
    String status;
    String updatedBy;
    Instant updatedAt;
}
