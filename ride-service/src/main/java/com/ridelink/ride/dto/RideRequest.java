package com.ridelink.ride.dto;

import com.ridelink.ride.model.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a new ride booking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RideRequest {

    @NotBlank(message = "Passenger ID is required")
    private String passengerId;

    @NotNull(message = "Pickup location is required")
    @Valid
    private LocationPointRequest pickupLocation;

    @NotNull(message = "Destination location is required")
    @Valid
    private LocationPointRequest destinationLocation;

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    @NotBlank(message = "Service area is required")
    private String serviceArea;
}
