package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.UnclaimedHorseResolutionRequestDto;
import org.springframework.web.multipart.MultipartFile;

public interface UnclaimedHorseService {
    void resolve(Long stayId, UnclaimedHorseResolutionRequestDto request, MultipartFile proof, String username);
}
