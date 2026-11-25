package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.AgentLegRequestDto;
import org.springframework.web.multipart.MultipartFile;
import com.example.racehorse_transport.dto.dispatch.HorseLegResponseDto;

// Chặng do đại lý nước ngoài chở (B2, GĐ7.4–7.5). Đại lý không có tài khoản: điều phối lập chặng và nhập hộ mốc.
public interface AgentLegService {

    // Đơn xuất: nối sau chặng xe kết thúc ở cửa khẩu, tới địa chỉ giao. Đơn nhập: chặng đầu tiên, từ địa chỉ đón tới cửa khẩu
    HorseLegResponseDto create(Long bookingHorseId, AgentLegRequestDto request);

    // Gỡ chặng đại lý chưa chạy, chỉ khi là chặng cuối của con ngựa
    void cancel(Long horseLegId);

    // Đại lý báo đã nhận ngựa từ người gửi (đơn nhập)
    HorseLegResponseDto start(Long horseLegId);

    // Đại lý báo đã giao cho người nhận ở nước đến (đơn xuất); con cuối giao xong thì đơn DELIVERED (#16)
    HorseLegResponseDto complete(Long horseLegId, MultipartFile proof, String username);
}
