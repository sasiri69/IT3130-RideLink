package com.ridelink.payment.dto;

import com.ridelink.payment.model.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for requesting a simulated fare estimate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareEstimateRequest {

    @DecimalMin(value = "-90.0", message = "Pickup latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Pickup latitude must be <= 90.0")
    private Double pickupLatitude;

    @DecimalMin(value = "-180.0", message = "Pickup longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Pickup longitude must be <= 180.0")
    private Double pickupLongitude;

    private String pickupAddress;

    @DecimalMin(value = "-90.0", message = "Destination latitude must be >= -90.0")
    @DecimalMax(value = "90.0", message = "Destination latitude must be <= 90.0")
    private Double destinationLatitude;

    @DecimalMin(value = "-180.0", message = "Destination longitude must be >= -180.0")
    @DecimalMax(value = "180.0", message = "Destination longitude must be <= 180.0")
    private Double destinationLongitude;

    private String destinationAddress;

    @Positive(message = "Direct distance in km must be positive if supplied")
    private Double directDistanceKm;

    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;

    @DecimalMin(value = "1.0", message = "Surge multiplier must be at least 1.0")
    @DecimalMax(value = "3.0", message = "Surge multiplier must not exceed 3.0")
    private Double surgeMultiplier;
}
