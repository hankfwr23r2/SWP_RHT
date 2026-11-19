package com.example.racehorse_transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(name = "DocumentRequirementResponse")
public class DocumentRequirementResponseDto {
    Integer id;
    String documentGroup;
    String documentType;
    String documentName;
    String description;
    String tripType;
    String country;
    Integer locationId;
    String locationName;
    boolean required;
    String status;
    String updatedBy;
    Instant updatedAt;
}
