package com.example.racehorse_transport.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDto<T> {
    private String status;  // e.g., "success" or "error"
    private String message;
    private T data;
    
    // Helper method for success
    public static <T> ApiResponseDto<T> success(T data, String message) {
        return ApiResponseDto.<T>builder()
                .status("success")
                .message(message)
                .data(data)
                .build();
    }
    
    // Helper method for error
    public static <T> ApiResponseDto<T> error(String message) {
        return ApiResponseDto.<T>builder()
                .status("error")
                .message(message)
                .data(null)
                .build();
    }
}
