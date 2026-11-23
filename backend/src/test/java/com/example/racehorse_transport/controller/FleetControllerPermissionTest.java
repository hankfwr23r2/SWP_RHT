package com.example.racehorse_transport.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// Chỉ quản trị viên thêm, sửa, xóa xe; điều phối và quản lý chỉ xem đội xe.
class FleetControllerPermissionTest {

    @Test
    void onlyAdminCanCreateUpdateOrDeleteVehicles() {
        for (String name : new String[] { "createVehicle", "updateVehicle", "deleteVehicle" }) {
            Method method = Arrays.stream(FleetController.class.getMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            assertThat(method.getAnnotation(PreAuthorize.class).value()).as(name).isEqualTo("hasRole('ADMIN')");
        }
    }
}
