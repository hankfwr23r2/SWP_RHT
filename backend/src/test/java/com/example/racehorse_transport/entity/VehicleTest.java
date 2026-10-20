package com.example.racehorse_transport.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VehicleTest {

    @Test
    void storesCapacityInsideVehicleType() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType("Xe chuyên dụng");

        vehicle.setCapacity(4);
        assertEquals("Xe chuyên dụng 4 ngăn", vehicle.getVehicleType());
        assertEquals(4, vehicle.getCapacity());

        vehicle.setCapacity(6);
        assertEquals("Xe chuyên dụng 6 ngăn", vehicle.getVehicleType());
        assertEquals(6, vehicle.getCapacity());
    }

    @Test
    void readsCapacityFromExistingVehicleType() {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleType("Xe 9 ngăn");

        assertEquals(9, vehicle.getCapacity());
    }
}
