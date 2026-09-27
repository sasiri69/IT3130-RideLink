package com.ridelink.driver.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    private String make;
    private String model;
    private Integer year;
    private String licensePlate;
    private String color;
    private VehicleType vehicleType;
    private Integer seatingCapacity;
}
