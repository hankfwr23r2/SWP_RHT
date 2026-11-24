package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.tracking.CustodyRowResponseDto;
import java.util.List;

// Bảng phụ trách (MAN_HINH 4.11): mọi con công ty hoặc đối tác đang giữ, con trễ khám đứng đầu
public interface CustodyBoardService {

    List<CustodyRowResponseDto> board();
}
