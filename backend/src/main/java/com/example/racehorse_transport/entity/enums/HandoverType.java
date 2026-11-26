package com.example.racehorse_transport.entity.enums;

// Giá trị khớp CHECK trong migration.
public enum HandoverType {
    PICKUP,
    HUB_IN,
    HUB_OUT,
    STAY_IN,
    STAY_OUT,
    DELIVERY,
    RETURN,
    // Bàn giao ca: người phụ trách mới ký nhận, ngựa không đổi chỗ
    SHIFT
}
