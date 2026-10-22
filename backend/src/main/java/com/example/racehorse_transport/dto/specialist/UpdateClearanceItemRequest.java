package com.example.racehorse_transport.dto.specialist;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClearanceItemRequest {
    private String type; // health_cert, quarantine_cert, customs_declaration, import_permit, ata_carnet, poa...
    private String note;
    private List<String> photos; // Danh sách đường dẫn ảnh chứng từ đã tải lên.
    private String status; // "todo" or "done"
}
