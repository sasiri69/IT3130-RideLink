package com.ridelink.driver.dto;

import com.ridelink.driver.model.Vehicle;
import com.ridelink.driver.model.VehicleType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {

    @NotBlank(message = "Vehicle make is required (e.g. Toyota, Honda)")
    private String make;

    @NotBlank(message = "Vehicle model is required (e.g. Prius, Civic, Alto)")
    private String model;

    @NotNull(message = "Manufacturing year is required")
    @Min(value = 1990, message = "Vehicle must be manufactured after 1990")
    @Max(value = 2030, message = "Invalid manufacturing year")
    private Integer year;

    @NotBlank(message = "License plate is required")
    @Pattern(regexp = "^[A-Z0-9- ]{4,12}$", message = "Invalid license plate format (e.g. CAB-1234 or WP-CAB-1234)")
    private String licensePlate;

    @NotBlank(message = "Vehicle color is required")
    private String color;

    @NotNull(message = "Vehicle type is required (CAR, VAN, BIKE, TUKTUK)")
    private VehicleType vehicleType;

    @NotNull(message = "Seating capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 15, message = "Capacity cannot exceed 15")
    private Integer seatingCapacity;

    public Vehicle toEntity() {
        return Vehicle.builder()
                .make(make.trim())
                .model(model.trim())
                .year(year)
                .licensePlate(licensePlate.trim().toUpperCase())
                .color(color.trim())
                .vehicleType(vehicleType)
                .seatingCapacity(seatingCapacity)
                .build();
    }
}
