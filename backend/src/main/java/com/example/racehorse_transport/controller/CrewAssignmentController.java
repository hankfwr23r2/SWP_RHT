package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.dispatch.MyCrewAssignmentResponseDto;
import com.example.racehorse_transport.service.CrewAssignmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// Lượt xe của tôi: tài xế, hộ tống xem phân công và xác nhận nhận việc (docs/MAN_HINH_VA_TAC_DONG.md mục 3.10)
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DRIVER', 'ESCORT')")
public class CrewAssignmentController {

    private final CrewAssignmentService crew;

    @GetMapping("/crew-assignments/mine")
    public ApiResponseDto<List<MyCrewAssignmentResponseDto>> mine(Authentication authentication) {
        return ApiResponseDto.success(crew.mine(authentication.getName()), "Lượt xe của tôi");
    }

    @PostMapping("/crew-assignments/{id}/acknowledge")
    public ApiResponseDto<MyCrewAssignmentResponseDto> acknowledge(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(crew.acknowledge(id, authentication.getName()), "Đã xác nhận nhận việc");
    }
}
