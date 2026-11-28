package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.UnclaimedHorseResolutionRequestDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Staff;
import java.time.Instant;
import org.springframework.web.multipart.MultipartFile;

public interface UnclaimedHandoverService {
    void record(HorseStay stay, HorseLeg failed, Staff manager, UnclaimedHorseResolutionRequestDto request,
                MultipartFile proof, Instant now);
}
