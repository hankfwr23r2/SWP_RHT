package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.EvidenceResponseDto;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

// Chứng từ của sự cố và chi phí (B4): biên lai, hóa đơn kèm chi phí; ảnh hiện trường kèm sự cố (giờ chụp, tọa độ nếu có)
public interface IncidentEvidenceService {

    // Biên lai cầu đường (khoản TOLL) hoặc hóa đơn chi phí; chỉ thêm khi quản lý chưa duyệt
    List<EvidenceResponseDto> attachReceipt(Long costItemId, MultipartFile file, String username);

    List<EvidenceResponseDto> receipts(Long costItemId);

    // Ảnh hiện trường của sự cố chưa đóng; latitude, longitude đi cùng nhau hoặc cùng bỏ trống
    List<EvidenceResponseDto> attachPhoto(Long incidentId, MultipartFile file, BigDecimal latitude, BigDecimal longitude, String documentType, String username);

    List<EvidenceResponseDto> photos(Long incidentId);

    void requireVehicleSwapRecord(Long incidentId);
}
