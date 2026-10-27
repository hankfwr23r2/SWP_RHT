package com.example.racehorse_transport.exception;

// Tuyến của đơn nội địa đi vào lãnh thổ nước khác → 422, điều phối bỏ hoặc đổi trạm
public class DomesticRouteAbroadException extends RuntimeException {

    public DomesticRouteAbroadException(String message) {
        super(message);
    }
}
