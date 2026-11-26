package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.tracking.CustodyRowResponseDto;
import com.example.racehorse_transport.service.CustodyBoardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Bảng phụ trách của điều phối và quản lý (MAN_HINH 4.11)
@RestController
@RequiredArgsConstructor
public class CustodyBoardController {

    private final CustodyBoardService custody;

    @GetMapping("/custody-board")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'MANAGER')")
    public ApiResponseDto<List<CustodyRowResponseDto>> board() {
        return ApiResponseDto.success(custody.board(), "Ngựa đang được giữ");
    }
}
