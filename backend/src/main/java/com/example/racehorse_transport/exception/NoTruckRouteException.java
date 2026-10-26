package com.example.racehorse_transport.exception;

// Dịch vụ chỉ đường báo không có đường cho xe tải giữa các điểm → 422, điều phối đổi cửa khẩu hoặc xe
public class NoTruckRouteException extends RuntimeException {

    public NoTruckRouteException() {
        super("Không tìm được đường cho xe tải giữa các điểm đã chọn. Hãy chọn cửa khẩu hoặc xe khác.");
    }
}
