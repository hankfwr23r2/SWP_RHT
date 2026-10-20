package com.example.racehorse_transport.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request metadata khi khach hang upload ho so ngua (Form-data).
 * File thuc te duoc gui rieng qua MultipartFile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadHorseDocumentRequest {

    /** ID con ngua can gan ho so */
    private Integer horseId;

    /**
     * Loai giay to:
     * PASSPORT        - Ho chieu ngua
     * VACCINATION     - So tiem phong
     * BLOOD_TEST      - Ket qua xet nghiem mau
     * HEALTH_CERT     - Giay chung nhan kiem dich
     * OTHER           - Giay to khac
     */
    private String documentType;
}
