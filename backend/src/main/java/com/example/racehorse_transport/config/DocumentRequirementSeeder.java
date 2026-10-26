package com.example.racehorse_transport.config;

import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
@RequiredArgsConstructor
public class DocumentRequirementSeeder implements CommandLineRunner {
    private final DocumentRequirementRepository repository;

    private static final List<String[]> DEFAULTS = List.of(
            new String[] {"HORSE", "passport", "Hộ chiếu ngựa", "ALL", "ALL"},
            new String[] {"HORSE", "vaccine", "Hồ sơ tiêm chủng", "ALL", "ALL"},
            new String[] {"HORSE", "lab", "Kết quả xét nghiệm máu", "ALL", "ALL"},
            new String[] {"CLEARANCE", "health_cert", "Giấy chứng nhận kiểm dịch", "ALL", "ALL"},
            new String[] {"CLEARANCE", "poa", "Giấy ủy quyền vận chuyển", "ALL", "ALL"},
            new String[] {"CLEARANCE", "customs_declaration", "Tờ khai hải quan", "INTERNATIONAL", "KH"},
            new String[] {"CLEARANCE", "import_permit", "Giấy phép nhập khẩu", "INTERNATIONAL", "KH"},
            new String[] {"CLEARANCE", "quarantine_cert", "Chứng thư kiểm dịch biên giới", "INTERNATIONAL", "KH"},
            new String[] {"CLEARANCE", "customs_declaration", "Tờ khai hải quan", "INTERNATIONAL", "LA"},
            new String[] {"CLEARANCE", "import_permit", "Giấy phép nhập khẩu", "INTERNATIONAL", "LA"},
            new String[] {"CLEARANCE", "ata_carnet", "Sổ ATA Carnet", "INTERNATIONAL", "LA"});

    @Override
    public void run(String... args) {
        List<DocumentRequirement> existingRequirements = repository.findAll();
        for (String[] defaults : DEFAULTS) {
            boolean exists = existingRequirements.stream().anyMatch(item ->
                    defaults[0].equals(item.getDocumentGroup())
                            && defaults[1].equals(item.getDocumentType())
                            && defaults[3].equals(item.getTripType())
                            && defaults[4].equals(item.getCountry()));
            if (!exists) {
                DocumentRequirement requirement = new DocumentRequirement();
                requirement.setDocumentGroup(defaults[0]);
                requirement.setDocumentType(defaults[1]);
                requirement.setDocumentName(defaults[2]);
                requirement.setTripType(defaults[3]);
                requirement.setCountry(defaults[4]);
                requirement.setRequired(true);
                requirement.setStatus("ACTIVE");
                requirement.setUpdatedBy("SYSTEM");
                requirement.setUpdatedAt(Instant.now());
                repository.save(requirement);
            }
        }
    }
}
