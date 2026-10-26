package com.example.racehorse_transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentRequirementRequest {
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

    private boolean required = true;

    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE")
    private String status = "ACTIVE";
}
