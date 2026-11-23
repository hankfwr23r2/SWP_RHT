package com.example.racehorse_transport.dto.file;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FileUploadResponse")
public class FileUploadResponseDto {
    private String url;
    private String originalFilename;
    private String contentType;
    private long size;
}
