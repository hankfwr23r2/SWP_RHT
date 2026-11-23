package com.example.racehorse_transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "DocumentRequirementRequest")
public class DocumentRequirementRequestDto {
    @NotBlank
    @Pattern(regexp = "HORSE|CLEARANCE")
    private String documentGroup;

    @NotBlank
    @Pattern(regexp = "passport|vaccine|lab|health_cert|poa|customs_declaration|import_permit|quarantine_cert|ata_carnet|commercial_invoice")
    private String documentType;

    @NotBlank
    @Size(max = 255)
    private String documentName;

    @Size(max = 1000)
    private String description;

    @NotBlank
    @Pattern(regexp = "ALL|DOMESTIC|INTERNATIONAL")
    private String tripType = "ALL";

    @NotBlank
    @Pattern(regexp = "ALL|VN|LA|KH")
    private String country = "ALL";

    // Cửa khẩu (Location loại BorderGate); null = áp dụng chung
    private Integer locationId;

    private boolean required = true;

    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE")
    private String status = "ACTIVE";

    public DocumentRequirement toEntity(Location gate) {
        DocumentRequirement target = new DocumentRequirement();
        applyTo(target, gate);
        return target;
    }

    public void applyTo(DocumentRequirement target, Location gate) {
        target.setDocumentGroup(getDocumentGroup());
        target.setDocumentType(getDocumentType());
        target.setDocumentName(getDocumentName().trim());
        target.setDescription(getDescription() == null ? null : getDescription().trim());
        target.setTripType(getTripType());
        target.setCountry(getCountry());
        target.setLocation(gate);
        target.setRequired(isRequired());
        target.setStatus(getStatus());
    }
}
