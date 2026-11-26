package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record TripReturnPhotoRequestDto(
        @NotNull(message = "Chụp ảnh xe tại hub trước khi kết thúc lượt.") MultipartFile file) {}
