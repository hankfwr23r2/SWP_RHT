package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.IncidentResponseDto;

// Thực hiện phương án sự cố đã được quản lý duyệt (B4).
public interface IncidentActionService {

    // TRANSFER_VEHICLE: lượt xe của sự cố chuyển sang xe thay thế
    IncidentResponseDto swapVehicle(Long incidentId, Long vehicleId, String username);

    // RETURN_TO_HUB: các con đang trên xe (hoặc con của sự cố) dừng chặng, tới điểm hub sau đó thì tổ xe giao vào hub (tập kết, không tính phí)
    IncidentResponseDto returnToHub(Long incidentId, String username);

    IncidentResponseDto recordVetResult(Long incidentId, Long planId, String note, String username);

    IncidentResponseDto cancelLegs(Long incidentId, Long planId, String username);
}
